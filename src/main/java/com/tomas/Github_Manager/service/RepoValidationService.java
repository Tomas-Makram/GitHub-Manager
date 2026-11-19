package com.tomas.Github_Manager.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

//Verifies the owner and repository names before incorporating them into GitHub API URLs
@Service
public class RepoValidationService {

    //GitHub owner and repository names: letters, numbers, dots, and hyphens only
    private static final Pattern SAFE_NAME = Pattern.compile("^[A-Za-z0-9._-]{1,100}$");

    // Returns true if the name is not null and matches the safe pattern
    public boolean isSafeName(String value) {
        return value == null || !SAFE_NAME.matcher(value).matches();
    }

    //Throws a 400 error if the owner or repository name is unsafe
    public void validateRepo(String owner, String repo) {
        if (isSafeName(owner) || isSafeName(repo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}