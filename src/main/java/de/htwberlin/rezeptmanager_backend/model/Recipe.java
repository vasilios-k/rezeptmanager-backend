package de.htwberlin.rezeptmanager_backend.model;

import java.util.List;

public class Recipe {

    private Long id;
    private String title;
    private List<RecipeIngredient> ingredients;
    private String instructions;
    private int prepTimeMinutes;
    private int servings;

    public Recipe(
            Long id,
            String title,
            List<RecipeIngredient> ingredients,
            String instructions,
            int prepTimeMinutes,
            int servings
    ) {
        this.id = id;
        this.title = title;
        this.ingredients = ingredients;
        this.instructions = instructions;
        this.prepTimeMinutes = prepTimeMinutes;
        this.servings = servings;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<RecipeIngredient> getIngredients() {
        return ingredients;
    }

    public String getInstructions() {
        return instructions;
    }

    public int getPrepTimeMinutes() {
        return prepTimeMinutes;
    }

    public int getServings() {
        return servings;
    }
}