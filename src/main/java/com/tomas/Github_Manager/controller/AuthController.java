package com.tomas.Github_Manager.controller;

import com.tomas.Github_Manager.service.LoginPageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

                                    // Login and logout pages
@Controller
public class AuthController {

    private final LoginPageService loginPageService;

    public AuthController(LoginPageService loginPageService) {
        this.loginPageService = loginPageService;
    }

    @GetMapping("/login")
    public String loginPageGet(@RequestParam(value = "error", required = false) String error,
                               Model model, HttpSession session) {
        if (loginPageService.shouldShowLoginError(error, session)) {
            model.addAttribute("loginError", true);
        }
        return "login";
    }

    @PostMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error, Model model) {
        if (loginPageService.isLoginError(error)) {
            model.addAttribute("loginError", true);
        }
        return "login";
    }

    @GetMapping("/logout-success")
    public String logoutPageGet() {
        return "logout";
    }

    @PostMapping("/logout-success")
    public String logoutPage() {
        return "logout";
    }

    @GetMapping("/logout-confirm")
    public String showLogoutConfirmPage() {
        return "logout-confirm";
    }
}