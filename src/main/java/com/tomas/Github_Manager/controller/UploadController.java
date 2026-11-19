package com.tomas.Github_Manager.controller;

import com.tomas.Github_Manager.service.GitHubErrorService;
import com.tomas.Github_Manager.service.GitHubUploadService;
import com.tomas.Github_Manager.service.MessageService;
import com.tomas.Github_Manager.service.RepoValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static com.tomas.Github_Manager.controller.ControllerSupport.principal;
import static com.tomas.Github_Manager.controller.ControllerSupport.token;

                            //Smart Upload (AJAX + SSE)
@Controller
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final GitHubUploadService uploadService;
    private final RepoValidationService validation;
    private final GitHubErrorService errors;
    private final MessageService messages;

    public UploadController(GitHubUploadService uploadService,
                            RepoValidationService validation,
                            GitHubErrorService errors,
                            MessageService messages) {
        this.uploadService = uploadService;
        this.validation = validation;
        this.errors = errors;
        this.messages = messages;
    }

    @GetMapping("/repos/{owner}/{repoName}/upload-plan")
    @ResponseBody
    public ResponseEntity<Object> uploadPlan(
            @RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
            @PathVariable String owner, @PathVariable String repoName,
            @RequestParam(required = false) String branch) {

        validation.validateRepo(owner, repoName);
        try {
            return ResponseEntity.ok().body(uploadService.getTreeSnapshot(token(client), owner, repoName, branch));
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode().value())
                    .body(Map.of("error", errors.githubError(e, "error.repo.generic", null)));
        }
    }

    @GetMapping("/repos/{owner}/{repoName}/upload-progress/{uploadId}")
    public SseEmitter getUploadProgress(@PathVariable String owner, @PathVariable String repoName,
                                        @PathVariable String uploadId) {
        validation.validateRepo(owner, repoName);
        return uploadService.registerEmitter(uploadId);
    }

    @PostMapping("/repos/{owner}/{repoName}/upload-folder")
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadFolder(
            @RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
            @PathVariable String owner,
            @PathVariable String repoName,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("paths") String[] paths,
            @RequestParam String commitMessage,
            @RequestParam(required = false) String branch,
            @RequestParam String uploadId,
            @RequestParam(required = false, defaultValue = "false") boolean forceUpload) {

        validation.validateRepo(owner, repoName);

        String commitError = uploadService.validateCommitMessage(commitMessage);
        if (commitError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", messages.msg(commitError)));
        }

        try {
            uploadService.upload(token(client), principal(client), owner, repoName, files, paths,
                    commitMessage, branch, uploadId, forceUpload);
            return ResponseEntity.ok(Map.of("status", "ok"));

        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode().value())
                    .body(Map.of("error", errors.githubError(e, "error.repo.generic", null)));

        } catch (Exception e) {
            log.error("Upload failed for {}/{}", owner, repoName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", messages.msg("error.upload.unknown")));
        }
    }
}