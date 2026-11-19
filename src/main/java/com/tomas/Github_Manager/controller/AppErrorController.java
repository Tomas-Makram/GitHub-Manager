package com.tomas.Github_Manager.controller;

import com.tomas.Github_Manager.service.ErrorPageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

                                            // Error Handler Controller
@Controller
public class AppErrorController implements ErrorController {

    private final ErrorPageService errorPageService;

    public AppErrorController(ErrorPageService errorPageService) {
        this.errorPageService = errorPageService;
    }

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        ErrorPageService.ErrorInfo info = errorPageService.resolve(request);

        model.addAttribute("status", info.status());
        model.addAttribute("titleKey", info.titleKey());
        model.addAttribute("messageKey", info.messageKey());
        model.addAttribute("redirectUrl", errorPageService.redirectTarget());
        model.addAttribute("redirectSeconds", errorPageService.redirectSeconds());

        return "error";
    }
}