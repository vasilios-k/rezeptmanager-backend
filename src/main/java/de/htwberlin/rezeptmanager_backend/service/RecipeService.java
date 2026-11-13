package de.htwberlin.rezeptmanager_backend.service;

import de.htwberlin.rezeptmanager_backend.dto.request.CreateRecipeIngredientRequest;
import de.htwberlin.rezeptmanager_backend.dto.request.CreateRecipeRequest;
import de.htwberlin.rezeptmanager_backend.dto.response.IngredientResponse;
import de.htwberlin.rezeptmanager_backend.dto.response.RecipeIngredientResponse;
import de.htwberlin.rezeptmanager_backend.dto.response.RecipeResponse;
import de.htwberlin.rezeptmanager_backend.model.Ingredient;
import de.htwberlin.rezeptmanager_backend.model.Recipe;
import de.htwberlin.rezeptmanager_backend.repository.IngredientRepository;
import de.htwberlin.rezeptmanager_backend.repository.RecipeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;

    public RecipeService(
            RecipeRepository recipeRepository,
            IngredientRepository ingredientRepository
    ) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Transactional(readOnly = true)
    public List<RecipeResponse> getRecipes() {
        return recipeRepository.findAllByOrderByIdAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(Long id) {
        Recipe recipe = recipeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Rezept nicht gefunden."
                ));

        return toResponse(recipe);
    }

    @Transactional
    public RecipeResponse createRecipe(CreateRecipeRequest request) {
        validateIngredientNames(request);

        Recipe recipe = new Recipe(
                request.title().strip(),
                request.instructions().strip(),
                request.prepTimeMinutes(),
                request.servings()
        );

        for (CreateRecipeIngredientRequest item : request.ingredients()) {
            Ingredient ingredient = findOrCreateIngredient(
                    item.ingredientName()
            );

            recipe.addIngredient(
                    ingredient,
                    item.quantity(),
                    item.unit()
            );
        }

        Recipe savedRecipe = recipeRepository.saveAndFlush(recipe);
        return toResponse(savedRecipe);
    }

    private void validateIngredientNames(CreateRecipeRequest request) {
        Set<String> names = new HashSet<>();

        for (CreateRecipeIngredientRequest item : request.ingredients()) {
            String normalizedName = normalizeIngredientName(
                    item.ingredientName()
            );

            if (!names.add(normalizedName)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Bitte jede Zutat nur einmal pro Rezept angeben."
                );
            }
        }
    }

    private Ingredient findOrCreateIngredient(String ingredientName) {
        String name = ingredientName.strip().replaceAll("\\s+", " ");
        String normalizedName = normalizeIngredientName(name);

        return ingredientRepository.findByNormalizedName(normalizedName)
                .orElseGet(() -> {
                    ingredientRepository.insertIfAbsent(
                            name,
                            normalizedName
                    );

                    return ingredientRepository
                            .findByNormalizedName(normalizedName)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Die Zutat konnte nicht geladen werden."
                            ));
                });
    }

    private String normalizeIngredientName(String name) {
        return name.strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private RecipeResponse toResponse(Recipe recipe) {
        List<RecipeIngredientResponse> ingredients = recipe.getIngredients()
                .stream()
                .map(item -> new RecipeIngredientResponse(
                        new IngredientResponse(
                                item.getIngredient().getId(),
                                item.getIngredient().getName()
                        ),
                        item.getQuantity(),
                        item.getUnit()
                ))
                .toList();

        return new RecipeResponse(
                recipe.getId(),
                recipe.getTitle(),
                ingredients,
                recipe.getInstructions(),
                recipe.getPrepTimeMinutes(),
                recipe.getServings()
        );
    }
}