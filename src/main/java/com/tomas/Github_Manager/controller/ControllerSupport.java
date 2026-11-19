package com.tomas.Github_Manager.controller;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;

                    //Small helper functions shared among controllers
public final class ControllerSupport {

    private ControllerSupport() {}

    //Retrieves a GitHub token from the authorized client
    public static String token(OAuth2AuthorizedClient client) {
        return client.getAccessToken().getTokenValue();
    }

    //GitHub username to used as a cache key
    public static String principal(OAuth2AuthorizedClient client) {
        return client.getPrincipalName();
    }

    //Constructs the redirect link for a repository page
    public static String repoRedirect(String owner, String repo) {
        return "redirect:/repos/" + owner + "/" + repo;
    }
}