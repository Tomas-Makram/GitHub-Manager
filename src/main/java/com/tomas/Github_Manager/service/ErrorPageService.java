package com.tomas.Github_Manager.service;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class ErrorPageService {

    public record ErrorInfo(int status, String titleKey, String messageKey) {}

    public ErrorInfo resolve(HttpServletRequest request) {
        Object statusAttr = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = (statusAttr instanceof Integer code) ? code : 500;

        return switch (status) {
            case 400 -> new ErrorInfo(status, "error.page.400.title", "error.page.400.message");
            case 401 -> new ErrorInfo(status, "error.page.401.title", "error.page.401.message");
            case 403 -> new ErrorInfo(status, "error.page.403.title", "error.page.403.message");
            case 404 -> new ErrorInfo(status, "error.page.404.title", "error.page.404.message");
            case 405 -> new ErrorInfo(status, "error.page.405.title", "error.page.405.message");
            case 413 -> new ErrorInfo(status, "error.page.413.title", "error.page.413.message");
            case 429 -> new ErrorInfo(status, "error.page.429.title", "error.page.429.message");
            case 503 -> new ErrorInfo(status, "error.page.503.title", "error.page.503.message");
            default  -> new ErrorInfo(status, "error.page.500.title", "error.page.500.message");
        };
    }

    public String redirectTarget() {
        return "/";
    }

    public int redirectSeconds() {
        return 5;
    }
}
