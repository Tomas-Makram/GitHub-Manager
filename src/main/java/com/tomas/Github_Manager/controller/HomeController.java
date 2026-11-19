package com.tomas.Github_Manager.controller;

import com.tomas.Github_Manager.service.GitHubUserService;
import com.tomas.Github_Manager.service.HomePageService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import static com.tomas.Github_Manager.controller.ControllerSupport.principal;
import static com.tomas.Github_Manager.controller.ControllerSupport.token;

                    //The home page and the updating of its cache
@Controller
public class HomeController {

    private final HomePageService homePageService;
    private final GitHubUserService userService;

    public HomeController(HomePageService homePageService, GitHubUserService userService) {
        this.homePageService = homePageService;
        this.userService = userService;
    }

    @GetMapping("/")
    public String home(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client, Model model) {
        if (client == null) return "redirect:/oauth2/authorization/github";

        HomePageService.HomeView view = homePageService.loadHome(token(client), principal(client));

        model.addAttribute("username", view.username());
        model.addAttribute("name", view.name());
        model.addAttribute("avatar", view.avatar());
        model.addAttribute("repos", view.repos());

        return "index";
    }

    @PostMapping("/cache/refresh")
    public String refreshHomeCache(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client) {
        userService.refreshUserCache(principal(client));
        return "redirect:/";
    }
}