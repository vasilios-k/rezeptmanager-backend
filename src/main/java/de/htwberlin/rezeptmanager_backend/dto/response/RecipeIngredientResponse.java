package de.htwberlin.rezeptmanager_backend.dto.response;

import de.htwberlin.rezeptmanager_backend.model.Unit;

import java.math.BigDecimal;

public record RecipeIngredientResponse(
        IngredientResponse ingredient,
        BigDecimal quantity,
        Unit unit
) {
}