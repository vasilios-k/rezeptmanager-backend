package de.htwberlin.rezeptmanager_backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @OneToMany(
            mappedBy = "recipe",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderColumn(name = "ingredient_position")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    @Column(nullable = false, columnDefinition = "text")
    private String instructions;

    @Column(name = "prep_time_minutes", nullable = false)
    private int prepTimeMinutes;

    @Column(nullable = false)
    private int servings;

    protected Recipe() {
    }

    public Recipe(
            String title,
            String instructions,
            int prepTimeMinutes,
            int servings
    ) {
        this.title = title;
        this.instructions = instructions;
        this.prepTimeMinutes = prepTimeMinutes;
        this.servings = servings;
    }

    public void updateDetails(
            String title,
            String instructions,
            int prepTimeMinutes,
            int servings
    ) {
        this.title = title;
        this.instructions = instructions;
        this.prepTimeMinutes = prepTimeMinutes;
        this.servings = servings;
    }

    public void replaceIngredients(
            List<RecipeIngredient> updatedIngredients
    ) {
        ingredients.clear();
        ingredients.addAll(updatedIngredients);
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