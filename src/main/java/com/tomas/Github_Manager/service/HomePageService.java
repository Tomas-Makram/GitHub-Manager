package com.tomas.Github_Manager.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

                                //Collection data for the home page
@Service
public class HomePageService {

    //Data ready for display on the home page
    public record HomeView(Object username, Object name, Object avatar, List<Map<String, Object>> repos) {}

    private final GitHubUserService userService;

    public HomePageService(GitHubUserService userService) {
        this.userService = userService;
    }

    //Fetches user data and repositories (from the cache or GitHub)
    //If no name is available, it falls back to the login name
    public HomeView loadHome(String token, String username) {
        Map<String, Object> profile = userService.getUserProfile(token, username);
        List<Map<String, Object>> repos = userService.getUserRepositories(token, username);

        Object name = profile.get("name") != null ? profile.get("name") : profile.get("login");
        return new HomeView(profile.get("login"), name, profile.get("avatar_url"), repos);
    }
}