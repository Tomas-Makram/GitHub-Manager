package com.tomas.Github_Manager.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

                            //Collecting data from the warehouse details page
@Service
public class RepositoryPageService {

    // Data ready for display on the warehouse page
    public record RepositoryPage(Map<String, Object> repo,
                                 List<Map<String, Object>> issues,
                                 List<Map<String, Object>> branches,
                                 String readmeHtml) {}

    private final GitHubRepositoryService repositoryService;
    private final GitHubBranchService branchService;
    private final GitHubContentService contentService;

    public RepositoryPageService(GitHubRepositoryService repositoryService,
                                 GitHubBranchService branchService,
                                 GitHubContentService contentService) {
        this.repositoryService = repositoryService;
        this.branchService = branchService;
        this.contentService = contentService;
    }

    //It retrieves details, issues, branches, and the README
    // All of which utilize the cache, with the username included in the key to ensure isolation
    public RepositoryPage load(String token, String username, String owner, String repoName) {
        return new RepositoryPage(
                repositoryService.getRepository(token, username, owner, repoName),
                repositoryService.getOpenIssues(token, username, owner, repoName),
                branchService.getBranches(token, username, owner, repoName),
                contentService.getReadmeHtml(token, username, owner, repoName));
    }
}