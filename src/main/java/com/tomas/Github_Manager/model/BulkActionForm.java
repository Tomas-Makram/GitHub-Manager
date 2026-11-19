package com.tomas.Github_Manager.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkActionForm {

    @NotBlank(message = "{validation.bulk.action_required}")
    private String action;

    @NotEmpty(message = "{validation.bulk.repos_required}")
    private List<String> repos;

    private String confirmation;
}
