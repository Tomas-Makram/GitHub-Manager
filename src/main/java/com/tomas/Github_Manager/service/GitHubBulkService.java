package com.tomas.Github_Manager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import static com.tomas.Github_Manager.service.GitHubApiClient.sleepQuietly;

//Bulk operations on repositories (changing visibility to private/public, and deletion), including validation, constraints, and a confirmation prompt
@Service
public class GitHubBulkService {

    private static final Logger log = LoggerFactory.getLogger(GitHubBulkService.class);

    private static final int BULK_MAX = 25;
    private static final int BULK_DELETE_MAX = 10;
    private static final long BULK_DELAY_MS = 150L;

    //Types of permitted group operations
    public enum BulkAction { PRIVATE, PUBLIC, DELETE }

    // The result of the operation on a single repository
    public record BulkResult(String repo, boolean success, String message) {}

    //Overall execution result
    public record BulkOutcome(String error, List<BulkResult> results) {

        //Constructs a result for a rejected request, including an error message
        public static BulkOutcome failure(String error) {
            return new BulkOutcome(error, List.of());
        }

        //It constructs a success result containing the outcomes for each repository
        public static BulkOutcome success(List<BulkResult> results) {
            return new BulkOutcome(null, results);
        }

        //Returns true if the request was rejected instead of being executed
        public boolean hasError() {
            return error != null;
        }
    }

    private final GitHubRepositoryService repositoryService;
    private final CacheEvictionService cacheEviction;
    private final MessageService messages;
    private final GitHubErrorService errors;
    private final RepoValidationService validation;

    public GitHubBulkService(GitHubRepositoryService repositoryService,
                             CacheEvictionService cacheEviction,
                             MessageService messages,
                             GitHubErrorService errors,
                             RepoValidationService validation) {
        this.repositoryService = repositoryService;
        this.cacheEviction = cacheEviction;
        this.messages = messages;
        this.errors = errors;
        this.validation = validation;
    }

    //Executes the batch operation on a single repository, then waits for a short interval to avoid hitting rate limits
    public void applyBulkAction(String token, BulkAction action, String owner, String repo) {
        switch (action) {
            case PRIVATE -> repositoryService.updateVisibility(token, owner, repo, true);
            case PUBLIC  -> repositoryService.updateVisibility(token, owner, repo, false);
            case DELETE  -> repositoryService.deleteRepository(token, owner, repo);
        }
        sleepQuietly(BULK_DELAY_MS);
    }


    public BulkOutcome execute(String token, String username, String actionName,
                               List<String> requestedRepos, String confirmation) {
        BulkAction action;
        try {
            action = BulkAction.valueOf(actionName == null ? "" : actionName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BulkOutcome.failure(messages.msg("error.bulk.invalid"));
        }

        List<String> repos = requestedRepos == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(requestedRepos));

        if (repos.isEmpty()) return BulkOutcome.failure(messages.msg("error.bulk.none_selected"));

        int max = action == BulkAction.DELETE ? BULK_DELETE_MAX : BULK_MAX;
        if (repos.size() > max) return BulkOutcome.failure(messages.msg("error.bulk.too_many", max));

        String requiredPhrase = switch (action) {
            case PUBLIC -> "PUBLIC";
            case DELETE -> "DELETE";
            default     -> null;
        };
        if (requiredPhrase != null && !requiredPhrase.equals(confirmation)) {
            return BulkOutcome.failure(messages.msg("error.bulk.confirm_mismatch", requiredPhrase));
        }

        for (String full : repos) {
            String[] parts = full == null ? new String[0] : full.split("/");
            if (parts.length != 2 || validation.isSafeName(parts[0]) || validation.isSafeName(parts[1])) {
                return BulkOutcome.failure(messages.msg("error.bulk.invalid"));
            }
        }

        List<BulkResult> results = new ArrayList<>();

        for (String full : repos) {
            String[] parts = full.split("/");
            log.info("Bulk {} on {}", action, full);
            try {
                applyBulkAction(token, action, parts[0], parts[1]);
                cacheEviction.evictRepoCaches(username, parts[0], parts[1]);
                results.add(new BulkResult(full, true, messages.msg("bulk.result.ok")));
            } catch (HttpClientErrorException e) {
                results.add(new BulkResult(full, false, errors.githubError(e, "error.repo.generic", null)));
            } catch (Exception e) {
                log.error("Bulk {} failed for {}", action, full, e);
                results.add(new BulkResult(full, false, messages.msg("error.repo.generic")));
            }
        }

        cacheEviction.evictUserCaches(username);

        return BulkOutcome.success(results);
    }
}