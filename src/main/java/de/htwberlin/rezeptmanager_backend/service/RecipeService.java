package de.htwberlin.rezeptmanager_backend.service;

import de.htwberlin.rezeptmanager_backend.model.Ingredient;
import de.htwberlin.rezeptmanager_backend.model.Recipe;
import de.htwberlin.rezeptmanager_backend.model.RecipeIngredient;
import de.htwberlin.rezeptmanager_backend.model.Unit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class RecipeService {

    private final List<Recipe> recipes;

    public RecipeService() {
        Ingredient flour = new Ingredient(1L, "Mehl");
        Ingredient eggs = new Ingredient(2L, "Eier");
        Ingredient milk = new Ingredient(3L, "Milch");
        Ingredient water = new Ingredient(4L, "Wasser");
        Ingredient dryYeast = new Ingredient(5L, "Trockenhefe");
        Ingredient salt = new Ingredient(6L, "Salz");

        recipes = List.of(
                new Recipe(
                        1L,
                        "Pfannkuchen",
                        List.of(
                                new RecipeIngredient(
                                        flour,
                                        new BigDecimal("200"),
                                        Unit.GRAM
                                ),
                                new RecipeIngredient(
                                        eggs,
                                        new BigDecimal("2"),
                                        Unit.PIECE
                                ),
                                new RecipeIngredient(
                                        milk,
                                        new BigDecimal("300"),
                                        Unit.MILLILITER
                                )
                        ),
                        "Zutaten verrühren und portionsweise "
                                + "in einer Pfanne ausbacken.",
                        25,
                        2
                ),
                new Recipe(
                        2L,
                        "Brot",
                        List.of(
                                new RecipeIngredient(
                                        flour,
                                        new BigDecimal("500"),
                                        Unit.GRAM
                                ),
                                new RecipeIngredient(
                                        water,
                                        new BigDecimal("300"),
                                        Unit.MILLILITER
                                ),
                                new RecipeIngredient(
                                        dryYeast,
                                        new BigDecimal("7"),
                                        Unit.GRAM
                                ),
                                new RecipeIngredient(
                                        salt,
                                        new BigDecimal("1"),
                                        Unit.TEASPOON
                                )
                        ),
                        "Zutaten verkneten. Den Teig gehen lassen, "
                                + "einen Laib formen und im Ofen backen.",
                        90,
                        4
                )
        );
    }

    public List<Recipe> getRecipes() {
        return recipes;
    }
}