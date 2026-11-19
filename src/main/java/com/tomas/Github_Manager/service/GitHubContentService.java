package com.tomas.Github_Manager.service;

import com.tomas.Github_Manager.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.tomas.Github_Manager.service.GitHubApiClient.encodePath;
import static com.tomas.Github_Manager.service.GitHubApiClient.repoPath;
import static com.tomas.Github_Manager.service.GitHubApiClient.uri;

            // Repository content: the README, README images, and text files (such as .gitignore)
@Service
public class GitHubContentService {

    private static final Logger log = LoggerFactory.getLogger(GitHubContentService.class);

    private static final int MAX_README_CHARS = 1_000_000;
    private static final long MAX_RAW_IMAGE_BYTES = 10L * 1024 * 1024; // 10MB
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private static final Map<String, MediaType> IMAGE_TYPES = Map.of(
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG,
            "gif", MediaType.IMAGE_GIF,
            "webp", MediaType.parseMediaType("image/webp"),
            "svg", MediaType.parseMediaType("image/svg+xml"));

    public record RawImage(byte[] bytes, MediaType type) {}

    public record FileContent(boolean exists, String content) {

        public static FileContent missing() {
            return new FileContent(false, null);
        }

        public Map<String, Object> toMap() {
            if (!exists) return Map.of("exists", false);
            return Map.of("exists", true, "content", content);
        }
    }

    private final GitHubApiClient api;

    public GitHubContentService(GitHubApiClient api) {
        this.api = api;
    }

    @Cacheable(value = CacheConfig.CACHE_README, key = "#username + ':' + #owner + ':' + #repo")
    public String getReadmeHtml(String token, String username, String owner, String repo) {
        log.debug("Cache MISS — getReadmeHtml for {}/{}", owner, repo);
        try {
            String html = api.restClient().get()
                    .uri(uri(repoPath(owner, repo) + "/readme"))
                    .headers(h -> {
                        h.setBearerAuth(token);
                        h.set(HttpHeaders.ACCEPT, "application/vnd.github.html+json");
                    })
                    .retrieve()
                    .body(String.class);
            if (html == null || html.isBlank()) return null;
            return html.length() > MAX_README_CHARS ? html.substring(0, MAX_README_CHARS) : html;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 404) {
                log.warn("Could not load README for {}/{}: {}", owner, repo, e.getStatusCode());
            }
            return null;
        }
    }

    public byte[] getRawFile(String token, String owner, String repo, String path) {
        return getRawFile(token, owner, repo, path, null);
    }

    public byte[] getRawFile(String token, String owner, String repo, String path, String ref) {
        String uriStr = repoPath(owner, repo) + "/contents/" + encodePath(path);
        if (ref != null && !ref.isBlank()) {
            uriStr += "?ref=" + URLEncoder.encode(ref, StandardCharsets.UTF_8);
        }
        return api.restClient().get()
                .uri(uri(uriStr))
                .headers(h -> {
                    h.setBearerAuth(token);
                    h.set(HttpHeaders.ACCEPT, "application/vnd.github.raw+json");
                })
                .exchange((req, res) -> {
                    if (!res.getStatusCode().is2xxSuccessful()) {
                        return null;
                    }
                    long length = res.getHeaders().getContentLength();
                    if (length > MAX_RAW_IMAGE_BYTES) {
                        log.warn("File too large to proxy ({} bytes): {}/{} path={}", length, owner, repo, path);
                        return null;
                    }
                    try (InputStream in = res.getBody()) {
                        byte[] bytes = in.readNBytes((int) MAX_RAW_IMAGE_BYTES + 1);
                        if (bytes.length > MAX_RAW_IMAGE_BYTES) {
                            log.warn("File stream exceeded 10MB limit: {}/{} path={}", owner, repo, path);
                            return null;
                        }
                        return bytes;
                    }
                });
    }

    //It loads the README image from the repository, provided the path is secure and the extension corresponds to a recognized image format.
    public Optional<RawImage> loadImage(String token, String owner, String repo, String path) {
        String clean = path.startsWith("/") ? path.substring(1) : path;
        int dot = clean.lastIndexOf('.');
        String ext = dot < 0 ? "" : clean.substring(dot + 1).toLowerCase(Locale.ROOT);
        MediaType type = IMAGE_TYPES.get(ext);

        if (type == null || clean.isEmpty() || clean.length() > 300
                || clean.contains("..") || clean.contains("\\")) {
            return Optional.empty();
        }

        try {
            byte[] bytes = getRawFile(token, owner, repo, clean);
            if (bytes == null || bytes.length > MAX_IMAGE_BYTES) {
                return Optional.empty();
            }
            return Optional.of(new RawImage(bytes, type));
        } catch (HttpClientErrorException e) {
            return Optional.empty();
        }
    }

   //It reads a text file (by default, .gitignore) from a specific branch
    public Optional<FileContent> readTextFile(String token, String owner, String repo, String path, String branch) {
        String clean = path.startsWith("/") ? path.substring(1) : path;
        if (clean.isEmpty() || clean.length() > 300 || clean.contains("..") || clean.contains("\\")) {
            return Optional.empty();
        }
        try {
            byte[] bytes = getRawFile(token, owner, repo, clean, branch);
            if (bytes == null) {
                return Optional.of(FileContent.missing());
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            return Optional.of(new FileContent(true, content));
        } catch (Exception e) {
            log.debug("Could not fetch file content for {}/{}: {}", owner, repo, e.getMessage());
            return Optional.of(FileContent.missing());
        }
    }
}