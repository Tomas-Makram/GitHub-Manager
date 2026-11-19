package com.tomas.Github_Manager.model;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDescriptionForm {

    @Size(max = 350, message = "{validation.repo.desc.size}")
    private String newDescription;
}
