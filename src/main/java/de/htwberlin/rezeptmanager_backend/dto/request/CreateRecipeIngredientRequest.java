package de.htwberlin.rezeptmanager_backend.dto.request;

import de.htwberlin.rezeptmanager_backend.model.Unit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateRecipeIngredientRequest(

        @NotBlank
        @Size(max = 100)
        String ingredientName,

        @NotNull
        @DecimalMin(value = "0", inclusive = false)
        @Digits(integer = 10, fraction = 3)
        BigDecimal quantity,

        @NotNull
        Unit unit

) {
}