package com.tomas.Github_Manager.controller;

import com.tomas.Github_Manager.model.CreateRepoForm;
import com.tomas.Github_Manager.model.DeleteRepoForm;
import com.tomas.Github_Manager.model.UpdateDescriptionForm;
import com.tomas.Github_Manager.service.GitHubErrorService;
import com.tomas.Github_Manager.service.GitHubRepositoryService;
import com.tomas.Github_Manager.service.GitHubUserService;
import com.tomas.Github_Manager.service.MessageService;
import com.tomas.Github_Manager.service.RepoValidationService;
import com.tomas.Github_Manager.service.RepositoryPageService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.tomas.Github_Manager.controller.ControllerSupport.principal;
import static com.tomas.Github_Manager.controller.ControllerSupport.repoRedirect;
import static com.tomas.Github_Manager.controller.ControllerSupport.token;

//Creating repositories, viewing their details, modifying privacy settings and descriptions, and deleting them
@Controller
public class RepositoryController {

    private static final Logger log = LoggerFactory.getLogger(RepositoryController.class);

    private final GitHubUserService userService;
    private final GitHubRepositoryService repositoryService;
    private final RepositoryPageService repositoryPageService;
    private final RepoValidationService validation;
    private final GitHubErrorService errors;
    private final MessageService messages;

    public RepositoryController(GitHubUserService userService,
                                GitHubRepositoryService repositoryService,
                                RepositoryPageService repositoryPageService,
                                RepoValidationService validation,
                                GitHubErrorService errors,
                                MessageService messages) {
        this.userService = userService;
        this.repositoryService = repositoryService;
        this.repositoryPageService = repositoryPageService;
        this.validation = validation;
        this.errors = errors;
        this.messages = messages;
    }

    private String redirectWithError(RedirectAttributes ra, String owner, String repo,
                                     HttpClientErrorException e, String defaultKey) {
        ra.addFlashAttribute("errorMessage", errors.githubError(e, defaultKey, null));
        return repoRedirect(owner, repo);
    }

    @GetMapping("/repos/new")
    public String showCreateRepoForm(Model model) {
        if (!model.containsAttribute("createRepoForm")) {
            model.addAttribute("createRepoForm", new CreateRepoForm());
        }
        return "new-repo";
    }

    @PostMapping("/repos/new")
    public String createRepo(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
                             @Valid @ModelAttribute("createRepoForm") CreateRepoForm form,
                             BindingResult bindingResult,
                             Model model) {
        if (bindingResult.hasErrors()) {
            return "new-repo";
        }
        String repoName = form.getRepoName();
        String description = form.getDescription();
        boolean isPrivate = form.getIsPrivate();
        try {
            userService.createRepository(token(client), principal(client), repoName, description, isPrivate);
            return "redirect:/";
        } catch (HttpClientErrorException e) {
            model.addAttribute("errorMessage", errors.githubError(e, "error.repo.generic", repoName));
            return "new-repo";
        } catch (Exception e) {
            log.error("Failed to create repository {}", repoName, e);
            model.addAttribute("errorMessage", messages.msg("error.repo.generic"));
            return "new-repo";
        }
    }

    @GetMapping("/repos/{owner}/{repoName}")
    public String manageRepo(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
                             @PathVariable String owner, @PathVariable String repoName, Model model) {
        validation.validateRepo(owner, repoName);

        RepositoryPageService.RepositoryPage page =
                repositoryPageService.load(token(client), principal(client), owner, repoName);

        model.addAttribute("repo", page.repo());
        model.addAttribute("issues", page.issues());
        model.addAttribute("branches", page.branches());
        model.addAttribute("readmeHtml", page.readmeHtml());

        return "repo-details";
    }

    @PostMapping("/repos/{owner}/{repoName}/cache/refresh")
    public String refreshRepoCache(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
                                   @PathVariable String owner, @PathVariable String repoName) {
        validation.validateRepo(owner, repoName);
        repositoryService.refreshRepoCache(principal(client), owner, repoName);
        return repoRedirect(owner, repoName);
    }

    @PostMapping("/repos/{owner}/{repoName}/visibility")
    public String changeVisibility(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
                                   @PathVariable String owner, @PathVariable String repoName,
                                   @RequestParam boolean isPrivate,
                                   RedirectAttributes redirectAttributes) {
        validation.validateRepo(owner, repoName);
        try {
            repositoryService.changeVisibility(token(client), principal(client), owner, repoName, isPrivate);
            return repoRedirect(owner, repoName);
        } catch (HttpClientErrorException e) {
            return redirectWithError(redirectAttributes, owner, repoName, e, "error.repo.generic");
        }
    }

    @PostMapping("/repos/{owner}/{repoName}/description")
    public String updateDescription(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
                                    @PathVariable String owner, @PathVariable String repoName,
                                    @Valid @ModelAttribute("updateDescForm") UpdateDescriptionForm form,
                                    BindingResult bindingResult,
                                    RedirectAttributes redirectAttributes) {
        validation.validateRepo(owner, repoName);
        if (bindingResult.hasErrors()) {
            String err = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("descError", err);
            redirectAttributes.addFlashAttribute("errorMessage", err);
            return repoRedirect(owner, repoName);
        }
        try {
            repositoryService.changeDescription(token(client), principal(client), owner, repoName, form.getNewDescription());
            return repoRedirect(owner, repoName);
        } catch (HttpClientErrorException e) {
            return redirectWithError(redirectAttributes, owner, repoName, e, "error.description.update_failed");
        }
    }

    @PostMapping("/repos/{owner}/{repoName}/delete")
    public String deleteRepository(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient client,
                                   @PathVariable String owner, @PathVariable String repoName,
                                   @Valid @ModelAttribute("deleteRepoForm") DeleteRepoForm form,
                                   BindingResult bindingResult,
                                   RedirectAttributes redirectAttributes) {
        validation.validateRepo(owner, repoName);
        if (bindingResult.hasErrors()) {
            String err = bindingResult.getAllErrors().get(0).getDefaultMessage();
            redirectAttributes.addFlashAttribute("deleteError", err);
            redirectAttributes.addFlashAttribute("errorMessage", err);
            return repoRedirect(owner, repoName);
        }

        if (!repositoryService.isDeleteConfirmed(repoName, form.getConfirmName())) {
            String err = messages.msg("error.delete.confirm_mismatch");
            redirectAttributes.addFlashAttribute("deleteError", err);
            redirectAttributes.addFlashAttribute("errorMessage", err);
            return repoRedirect(owner, repoName);
        }

        try {
            repositoryService.removeRepository(token(client), principal(client), owner, repoName);
            return "redirect:/";
        } catch (HttpClientErrorException e) {
            return redirectWithError(redirectAttributes, owner, repoName, e, "error.repo.generic");
        }
    }
}