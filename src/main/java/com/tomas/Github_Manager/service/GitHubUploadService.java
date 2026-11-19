package com.tomas.Github_Manager.service;

import com.tomas.Github_Manager.exception.GitHubServiceException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tomas.Github_Manager.service.GitHubApiClient.encodeBranch;
import static com.tomas.Github_Manager.service.GitHubApiClient.nestedString;
import static com.tomas.Github_Manager.service.GitHubApiClient.repoPath;

                                                    //Smart Upload
@Service
public class GitHubUploadService {

    private static final Logger log = LoggerFactory.getLogger(GitHubUploadService.class);

    private static final long SSE_TIMEOUT_MS = 300_000L;

    //File present on GitHub
    public record RemoteFile(String sha, long size) {}

    //An image of the branch tree
    public record TreeSnapshot(boolean branchExists, boolean truncated, Map<String, RemoteFile> files) {}

    private final GitHubApiClient api;
    private final GitHubBranchService branchService;
    private final CacheEvictionService cacheEviction;
    private final ExecutorService uploadPool;
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public GitHubUploadService(GitHubApiClient api,
                               GitHubBranchService branchService,
                               CacheEvictionService cacheEviction) {
        this.api = api;
        this.branchService = branchService;
        this.cacheEviction = cacheEviction;

        AtomicInteger threadId = new AtomicInteger(1);
        this.uploadPool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "github-upload-worker-" + threadId.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    //Gracefully shuts down the upload pool when the application closes (5-second timeout before forced termination)
    @PreDestroy
    public void shutdownUploadPool() {
        log.info("Shutting down GitHubService upload pool gracefully...");
        uploadPool.shutdown();
        try {
            if (!uploadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                uploadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            uploadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // =====================================================================
    // Validation
    // =====================================================================

    public String validateCommitMessage(String commitMessage) {
        if (commitMessage == null || commitMessage.trim().isEmpty()) {
            return "validation.upload.commit_required";
        }
        if (commitMessage.length() > 500) {
            return "validation.upload.commit_size";
        }
        return null;
    }

    // =====================================================================
    // Smart upload: snapshot of the remote tree
    // =====================================================================


    public TreeSnapshot getTreeSnapshot(String token, String owner, String repo, String branch) {
        Map<String, Object> repoInfo = api.call(HttpMethod.GET, token, repoPath(owner, repo), null);
        String defaultBranch = (String) repoInfo.get("default_branch");
        if (defaultBranch == null || defaultBranch.isBlank()) defaultBranch = "main";
        String target = (branch == null || branch.isBlank()) ? defaultBranch : branch.trim();

        boolean exists = true;
        Map<String, Object> branchInfo;
        try {
            branchInfo = branchService.getBranch(token, owner, repo, target);
        } catch (HttpClientErrorException.NotFound e) {
            exists = false;
            if (target.equals(defaultBranch)) {
                return new TreeSnapshot(false, false, Map.of());
            }
            try {
                branchInfo = branchService.getBranch(token, owner, repo, defaultBranch);
            } catch (HttpClientErrorException.NotFound e2) {
                return new TreeSnapshot(false, false, Map.of());
            }
        }

        String treeSha = nestedString(branchInfo, "commit", "commit", "tree", "sha");
        if (treeSha == null) return new TreeSnapshot(exists, false, Map.of());

        Map<String, Object> tree = api.call(HttpMethod.GET, token,
                repoPath(owner, repo) + "/git/trees/" + treeSha + "?recursive=1", null);

        Map<String, RemoteFile> files = new LinkedHashMap<>();
        if (tree != null && tree.get("tree") instanceof List<?> entries) {
            for (Object entry : entries) {
                if (entry instanceof Map<?, ?> m && "blob".equals(m.get("type"))) {
                    String path = (String) m.get("path");
                    String sha = (String) m.get("sha");
                    Number size = (Number) m.get("size");
                    if (path != null && sha != null) {
                        files.put(path, new RemoteFile(sha, size == null ? -1L : size.longValue()));
                    }
                }
            }
        }
        boolean truncated = tree != null && Boolean.TRUE.equals(tree.get("truncated"));
        return new TreeSnapshot(exists, truncated, files);
    }

    // =====================================================================
    // Upload progress (SSE)
    // =====================================================================

    public SseEmitter registerEmitter(String uploadId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.put(uploadId, emitter);
        emitter.onCompletion(() -> emitters.remove(uploadId));
        emitter.onTimeout(() -> emitters.remove(uploadId));
        emitter.onError(e -> emitters.remove(uploadId));
        return emitter;
    }

    private void sendEvent(String uploadId, String name, Object data) {
        SseEmitter emitter = emitters.get(uploadId);
        if (emitter == null) {
            log.debug("No SSE emitter found for uploadId={} (client disconnected or timed out)", uploadId);
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(uploadId);
        }
    }

    private void closeEmitter(String uploadId) {
        SseEmitter emitter = emitters.remove(uploadId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("Emitter completion ignored for uploadId={}: {}", uploadId, e.getMessage());
            }
        }
    }

    // =====================================================================
    // Upload files
    // =====================================================================

    public void upload(String token, String username, String owner, String repo,
                       MultipartFile[] files, String[] paths,
                       String commitMessage, String branch,
                       String uploadId, boolean forceUpload) throws IOException {
        uploadFiles(token, owner, repo, files, paths, commitMessage, branch, uploadId, forceUpload);
        cacheEviction.evictRepoCaches(username, owner, repo);
    }


    public void uploadFiles(String token, String owner, String repo,
                            MultipartFile[] files, String[] paths,
                            String commitMessage, String branch,
                            String uploadId, boolean forceUpload) throws IOException {
        try {
            if (files.length != paths.length) {
                throw new GitHubServiceException(400, "error.upload.invalid_request");
            }

            List<Integer> indexes = new ArrayList<>();
            for (int i = 0; i < files.length; i++) {
                if (!files[i].isEmpty()) indexes.add(i);
            }
            if (indexes.isEmpty()) {
                throw new GitHubServiceException(400, "error.upload.no_valid_files");
            }

            Map<String, Object> repoInfo = api.call(HttpMethod.GET, token, repoPath(owner, repo), null);
            String defaultBranch = (String) repoInfo.get("default_branch");
            if (defaultBranch == null || defaultBranch.isBlank()) defaultBranch = "main";

            String targetBranch = (branch == null || branch.isBlank()) ? defaultBranch : branch.trim();
            Map<String, Object> branchInfo = resolveBranch(token, owner, repo, repoInfo, defaultBranch, targetBranch);

            String parentCommitSha = null;
            String parentTreeSha = null;
            if (!forceUpload) {
                parentCommitSha = nestedString(branchInfo, "commit", "sha");
                parentTreeSha = nestedString(branchInfo, "commit", "commit", "tree", "sha");
                if (parentCommitSha == null || parentTreeSha == null) {
                    throw new GitHubServiceException(400, "error.upload.no_commit_info");
                }
            }

            // 1) blobs
            List<Map<String, Object>> treeItems = uploadBlobs(token, owner, repo, files, paths, indexes, uploadId);

            // 2) tree
            Map<String, Object> treeRequest = new HashMap<>();
            treeRequest.put("tree", treeItems);
            if (!forceUpload) treeRequest.put("base_tree", parentTreeSha);

            Map<String, Object> treeResponse =
                    api.call(HttpMethod.POST, token, repoPath(owner, repo) + "/git/trees", treeRequest);
            String newTreeSha = (String) treeResponse.get("sha");

            // 3) commit
            Map<String, Object> commitRequest = new HashMap<>();
            commitRequest.put("message", commitMessage);
            commitRequest.put("tree", newTreeSha);
            if (!forceUpload) commitRequest.put("parents", List.of(parentCommitSha));

            Map<String, Object> commitResponse =
                    api.call(HttpMethod.POST, token, repoPath(owner, repo) + "/git/commits", commitRequest);
            String newCommitSha = (String) commitResponse.get("sha");

            // 4) move the branch ref
            Map<String, Object> refRequest = new HashMap<>();
            refRequest.put("sha", newCommitSha);
            refRequest.put("force", forceUpload);

            api.call(HttpMethod.PATCH, token,
                    repoPath(owner, repo) + "/git/refs/heads/" + encodeBranch(targetBranch), refRequest);

            log.info("Branch {} updated in {}/{} ({} files)", targetBranch, owner, repo, indexes.size());
            sendEvent(uploadId, "complete", "Done");

        } catch (GitHubServiceException e) {
            log.warn("Upload failed for {}/{}: {}", owner, repo, e.getMessageKey());
            sendEvent(uploadId, "error", Map.of(
                    "messageKey", e.getMessageKey(),
                    "args", e.getArgs() != null ? e.getArgs() : new Object[0]));
            throw e;
        } catch (Exception e) {
            log.error("Unexpected upload failure for {}/{}: {}", owner, repo, e.getMessage(), e);
            sendEvent(uploadId, "error", Map.of("messageKey", "error.upload.unknown"));
            throw e;
        } finally {
            closeEmitter(uploadId);
        }
    }


    private List<Map<String, Object>> uploadBlobs(String token, String owner, String repo,
                                                  MultipartFile[] files, String[] paths,
                                                  List<Integer> indexes, String uploadId) throws IOException {
        int total = indexes.size();
        AtomicInteger done = new AtomicInteger();
        List<Future<Map<String, Object>>> futures = new ArrayList<>(total);

        try {
            for (int idx : indexes) {
                MultipartFile file = files[idx];
                String path = paths[idx];

                futures.add(uploadPool.submit(() -> {
                    byte[] bytes = file.getBytes();
                    String sha = api.withRetry(() -> createBlob(token, owner, repo, bytes));

                    Map<String, Object> item = new HashMap<>();
                    item.put("path", path);
                    item.put("mode", "100644");
                    item.put("type", "blob");
                    item.put("sha", sha);

                    int current = done.incrementAndGet();
                    sendEvent(uploadId, "progress", Map.of(
                            "current", current,
                            "total", total,
                            "percentage", (int) ((current * 100.0) / total)));
                    return item;
                }));
            }

            List<Map<String, Object>> items = new ArrayList<>(total);
            for (Future<Map<String, Object>> future : futures) {
                items.add(future.get(5, TimeUnit.MINUTES));
            }
            return items;

        } catch (ExecutionException e) {
            cancelPendingFutures(futures);
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new IOException(cause);
        } catch (InterruptedException | TimeoutException e) {
            cancelPendingFutures(futures);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IOException("Blob upload was interrupted or timed out: " + e.getMessage(), e);
        }
    }

    // Cancels any pending upload tasks (protects resources and rate limits in the event of a file failure)
    private static void cancelPendingFutures(List<Future<Map<String, Object>>> futures) {
        for (Future<?> f : futures) {
            if (!f.isDone()) {
                f.cancel(true);
            }
        }
    }

    private Map<String, Object> resolveBranch(String token, String owner, String repo,
                                              Map<String, Object> repoInfo,
                                              String defaultBranch, String targetBranch) {
        try {
            return branchService.getBranch(token, owner, repo, targetBranch);
        } catch (HttpClientErrorException.NotFound e) {
            Number size = (Number) repoInfo.get("size");
            boolean repositoryEmpty = size != null && size.longValue() == 0;

            if (repositoryEmpty && targetBranch.equals(defaultBranch)) {
                initializeEmptyRepository(token, owner, repo, defaultBranch);
                return branchService.getBranch(token, owner, repo, targetBranch);
            }
            if (!repositoryEmpty && !targetBranch.equals(defaultBranch)) {
                branchService.createBranchFromDefault(token, owner, repo, defaultBranch, targetBranch);
                return branchService.getBranch(token, owner, repo, targetBranch);
            }
            throw new GitHubServiceException(404, "error.upload.branch_not_found", targetBranch);
        }
    }

    //It initializes an empty repository by creating an initial README.md file, ensuring there is a first commit
    private void initializeEmptyRepository(String token, String owner, String repo, String branch) {
        log.info("Initializing empty repository {}/{}", owner, repo);
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Initial commit - Auto-initialized by GitHub Manager");
        body.put("content", Base64.getEncoder().encodeToString("Repository initialized.".getBytes(StandardCharsets.UTF_8)));
        body.put("branch", branch);
        api.call(HttpMethod.PUT, token, repoPath(owner, repo) + "/contents/README.md", body);
    }

    //Creates a blob on GitHub from file bytes (Base64) and returns its SHA
    private String createBlob(String token, String owner, String repo, byte[] bytes) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", Base64.getEncoder().encodeToString(bytes));
        body.put("encoding", "base64");
        Map<String, Object> response = api.call(HttpMethod.POST, token, repoPath(owner, repo) + "/git/blobs", body);
        return (String) response.get("sha");
    }
}