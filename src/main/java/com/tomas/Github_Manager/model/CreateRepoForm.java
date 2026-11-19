package com.tomas.Github_Manager.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CreateRepoForm {

    @NotBlank(message = "{validation.repo.name.required}")
    @Size(min = 1, max = 100, message = "{validation.repo.name.size}")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "{validation.repo.name.invalid}")
    private String repoName;

    @Size(max = 350, message = "{validation.repo.desc.size}")
    private String description;

    private boolean isPrivate;

    public CreateRepoForm() {}

    public CreateRepoForm(String repoName, String description, boolean isPrivate) {
        this.repoName = repoName;
        this.description = description;
        this.isPrivate = isPrivate;
    }

    public boolean getIsPrivate() {
        return isPrivate;
    }

    public void setIsPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
}
