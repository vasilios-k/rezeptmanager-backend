package de.htwberlin.rezeptmanager_backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRecipeRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotEmpty
        @Size(max = 100)
        List<@NotNull @Valid CreateRecipeIngredientRequest> ingredients,

        @NotBlank
        @Size(max = 10000)
        String instructions,

        @NotNull
        @PositiveOrZero
        Integer prepTimeMinutes,

        @NotNull
        @Positive
        Integer servings

) {
}