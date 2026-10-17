package de.htwberlin.rezeptmanager_backend.model;

import java.math.BigDecimal;

public class RecipeIngredient {

    private Ingredient ingredient;
    private BigDecimal quantity;
    private Unit unit;

    public RecipeIngredient(
            Ingredient ingredient,
            BigDecimal quantity,
            Unit unit
    ) {
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.unit = unit;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Unit getUnit() {
        return unit;
    }
}