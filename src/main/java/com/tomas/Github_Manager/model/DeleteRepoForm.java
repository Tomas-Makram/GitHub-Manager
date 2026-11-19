package com.tomas.Github_Manager.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteRepoForm {

    @NotBlank(message = "{validation.delete.confirm_required}")
    private String confirmName;
}
