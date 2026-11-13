package de.htwberlin.rezeptmanager_backend.dto.response;

import java.util.List;

public record RecipeResponse(
        Long id,
        String title,
        List<RecipeIngredientResponse> ingredients,
        String instructions,
        int prepTimeMinutes,
        int servings
) {
}