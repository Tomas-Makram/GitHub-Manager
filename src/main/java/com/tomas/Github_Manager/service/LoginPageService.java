package com.tomas.Github_Manager.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

                    //Logic for displaying a login failure message on the login page
@Service
public class LoginPageService {

    //For a GET request: returns true if there is an 'error=true' parameter or a flag in the session, and removes the flag from the session
    public boolean shouldShowLoginError(String error, HttpSession session) {
        if ("true".equals(error) || Boolean.TRUE.equals(session.getAttribute("loginError"))) {
            session.removeAttribute("loginError");
            return true;
        }
        return false;
    }

    //For a POST request: returns true if the parameter error=true
    public boolean isLoginError(String error) {
        return "true".equals(error);
    }
}