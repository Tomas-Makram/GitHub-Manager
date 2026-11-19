package com.tomas.Github_Manager.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBranchForm {

    @NotBlank(message = "{validation.branch.name.required}")
    @Size(min = 1, max = 250, message = "{validation.branch.name.size}")
    @Pattern(regexp = "^[A-Za-z0-9._/-]+$", message = "{validation.branch.name.invalid}")
    private String newBranchName;
}
