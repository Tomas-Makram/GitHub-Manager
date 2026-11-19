package com.tomas.Github_Manager.controller;

import com.tomas.Github_Manager.model.CreateBranchForm;
import com.tomas.Github_Manager.model.RenameBranchForm;
import com.tomas.Github_Manager.service.GitHubBranchService;
import com.tomas.Github_Manager.service.GitHubErrorService;
import com.tomas.Github_Manager.service.RepoValidationService;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.tomas.Github_Manager.controller.ControllerSupport.principal;
import static com.tomas.Github_Manager.controller.ControllerSupport.repoRedirect;
import static com.tomas.Github_Manager.controller.ControllerSupport.token;

                        //Branch management: create, delete, rename
@Controller
public class BranchController {

    private final GitHubBranchService branchService;
    private final RepoValidationService validation;
    private final GitHubErrorService errors;

    public BranchController(GitHubBranchService branchService,
                            RepoValidationService validation,
                            GitHubErrorService errors) {
        this.branchService = branchService;
        this.validation = validation;
        this.errors = errors;
    }

    private String redirectWithError(RedirectAttributes ra, String owner, String repo,
                                     HttpClientErrorException e) {
        ra.addFlashAttribute("errorMessage", errors.githubError(e, "error.repo.generic", null));
        return repoRedirect(owner, repo);
    }

    @PostMapping("/repos/{owner}/{repoName}/branches")
    public String createBranch(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
                               @PathVariable String owner, @PathVariable String repoName,
                               @Valid @ModelAttribute("createBranchForm") CreateBranchForm form,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes) {
        validation.validateRepo(owner, repoName);
        if (bindingResult.hasErrors()) {
            String err = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("branchError", err);
            redirectAttributes.addFlashAttribute("errorMessage", err);
            return repoRedirect(owner, repoName);
        }
        try {
            branchService.createBranch(token(client), principal(client), owner, repoName, form.getNewBranchName());
            return repoRedirect(owner, repoName);
        } catch (HttpClientErrorException e) {
            return redirectWithError(redirectAttributes, owner, repoName, e);
        }
    }

    @PostMapping("/repos/{owner}/{repoName}/branches/delete")
    public String deleteBranch(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
                               @PathVariable String owner, @PathVariable String repoName,
                               @RequestParam String branchName,
                               RedirectAttributes redirectAttributes) {
        validation.validateRepo(owner, repoName);
        try {
            branchService.deleteBranch(token(client), principal(client), owner, repoName, branchName);
            return repoRedirect(owner, repoName);
        } catch (HttpClientErrorException e) {
            return redirectWithError(redirectAttributes, owner, repoName, e);
        }
    }

    @PostMapping("/repos/{owner}/{repoName}/branches/rename")
    public String renameBranch(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
                               @PathVariable String owner, @PathVariable String repoName,
                               @Valid @ModelAttribute("renameBranchForm") RenameBranchForm form,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes) {
        validation.validateRepo(owner, repoName);
        if (bindingResult.hasErrors()) {
            String err = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("renameError", err);
            redirectAttributes.addFlashAttribute("renameOldBranch", form.getOldBranchName());
            redirectAttributes.addFlashAttribute("errorMessage", err);
            return repoRedirect(owner, repoName);
        }
        try {
            branchService.renameBranch(token(client), principal(client), owner, repoName,
                    form.getOldBranchName(), form.getNewBranchName());
            return repoRedirect(owner, repoName);
        } catch (HttpClientErrorException e) {
            return redirectWithError(redirectAttributes, owner, repoName, e);
        }
    }
}