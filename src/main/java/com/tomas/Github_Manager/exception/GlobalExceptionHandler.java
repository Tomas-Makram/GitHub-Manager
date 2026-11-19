package com.tomas.Github_Manager.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // Return the error message in the user's current language
    private String msg(String key, Object... args) {
        try {
            return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            return key;
        }
    }

    // Type of request
    private boolean isJsonRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        String requestedWith = request.getHeader("X-Requested-With");
        String uri = request.getRequestURI();
        return (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith)
                || uri.endsWith("/upload-folder")
                || uri.endsWith("/upload-plan")
                || uri.endsWith("/bulk/action");
    }

    // It handles the scenario where a user uploads a file that exceeds the permitted limit.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSize(MaxUploadSizeExceededException exc, HttpServletRequest request) {
        log.warn("Max upload size exceeded: {}", exc.getMessage());
        String errorMsg = msg("error.page.413.message");

        if (isJsonRequest(request)) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", errorMsg));
        }

        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.CONTENT_TOO_LARGE);
        mav.addObject("status", 413);
        mav.addObject("titleKey", "error.page.413.title");
        mav.addObject("messageKey", "error.page.413.message");
        mav.addObject("redirectUrl", "/");
        mav.addObject("redirectSeconds", 5);
        return mav;
    }

    // It handles requests JSON
    @ExceptionHandler(ResponseStatusException.class)
    public Object handleResponseStatus(ResponseStatusException exc, HttpServletRequest request) {
        if (isJsonRequest(request)) {
            String message = exc.getReason() != null ? exc.getReason() : msg("error.page." + exc.getStatusCode().value() + ".message");
            return ResponseEntity.status(exc.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", message));
        }
        throw exc;
    }

    @ExceptionHandler(GitHubServiceException.class)
    public Object handleGitHubServiceException(GitHubServiceException exc, HttpServletRequest request) {
        String errorMsg = msg(exc.getMessageKey(), exc.getArgs());
        if (isJsonRequest(request)) {
            return ResponseEntity.status(exc.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", errorMsg));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.valueOf(exc.getStatus()));
        mav.addObject("status", exc.getStatus());
        mav.addObject("titleKey", "error.page." + exc.getStatus() + ".title");
        mav.addObject("messageKey", exc.getMessageKey());
        mav.addObject("redirectUrl", "/");
        mav.addObject("redirectSeconds", 5);
        return mav;
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public Object handleValidationException(org.springframework.web.bind.MethodArgumentNotValidException exc, HttpServletRequest request) {
        String errorMsg = exc.getBindingResult().getAllErrors().isEmpty()
                ? "Validation error"
                : exc.getBindingResult().getAllErrors().get(0).getDefaultMessage();

        if (isJsonRequest(request)) {
            assert errorMsg != null;
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", errorMsg));
        }

        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.BAD_REQUEST);
        mav.addObject("status", 400);
        mav.addObject("titleKey", "error.page.400.title");
        mav.addObject("messageKey", errorMsg);
        mav.addObject("redirectUrl", "/");
        mav.addObject("redirectSeconds", 5);
        return mav;
    }
}