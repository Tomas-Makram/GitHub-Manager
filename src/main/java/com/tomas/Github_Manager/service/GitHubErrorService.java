package com.tomas.Github_Manager.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

                        //Converts raw GitHub errors into translated, user-friendly messages
@Service
public class GitHubErrorService {

    private final MessageService messages;

    public GitHubErrorService(MessageService messages) {
        this.messages = messages;
    }

    public String githubError(HttpClientErrorException e, String defaultKey, String repoName) {
        int status = e.getStatusCode().value();
        String body = e.getResponseBodyAsString();

        if (status == 403 && body.contains("rate limit")) return messages.msg("error.rate_limit");
        if (status == 403) return messages.msg("error.permission");
        if (status == 422) {
            if (repoName != null && body.contains("name already exists")) return messages.msg("error.repo.name_exists", repoName);
            if (body.contains("Reference already exists")) return messages.msg("error.branch.exists");
            if (body.contains("description")) return messages.msg("error.repo.description_invalid");
        }
        return messages.msg(defaultKey);
    }
}