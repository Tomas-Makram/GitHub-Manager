package com.tomas.Github_Manager.controller;

import com.tomas.Github_Manager.model.BulkActionForm;
import com.tomas.Github_Manager.service.GitHubBulkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

import static com.tomas.Github_Manager.controller.ControllerSupport.principal;
import static com.tomas.Github_Manager.controller.ControllerSupport.token;

                    //Bulk operations on repositories from the home page
@Controller
public class BulkController {

    private final GitHubBulkService bulkService;

    public BulkController(GitHubBulkService bulkService) {
        this.bulkService = bulkService;
    }

    @PostMapping("/repos/bulk/action")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bulkAction(
            @RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
            @Valid @RequestBody BulkActionForm request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return bulkError(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }

        GitHubBulkService.BulkOutcome outcome = bulkService.execute(
                token(client), principal(client),
                request.getAction(), request.getRepos(), request.getConfirmation());

        if (outcome.hasError()) {
            return bulkError(outcome.error());
        }
        return ResponseEntity.ok(Map.of("results", outcome.results()));
    }

    private ResponseEntity<Map<String, Object>> bulkError(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}