package de.htwberlin.rezeptmanager_backend.service;

import de.htwberlin.rezeptmanager_backend.dto.request.RecipeIngredientRequest;
import de.htwberlin.rezeptmanager_backend.dto.request.RecipeRequest;
import de.htwberlin.rezeptmanager_backend.dto.response.IngredientResponse;
import de.htwberlin.rezeptmanager_backend.dto.response.RecipeIngredientResponse;
import de.htwberlin.rezeptmanager_backend.dto.response.RecipeResponse;
import de.htwberlin.rezeptmanager_backend.model.Ingredient;
import de.htwberlin.rezeptmanager_backend.model.Recipe;
import de.htwberlin.rezeptmanager_backend.model.RecipeIngredient;
import de.htwberlin.rezeptmanager_backend.repository.IngredientRepository;
import de.htwberlin.rezeptmanager_backend.repository.RecipeRepository;
import de.htwberlin.rezeptmanager_backend.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final CurrentUser currentUser;

    public RecipeService(
            RecipeRepository recipeRepository,
            IngredientRepository ingredientRepository,
            CurrentUser currentUser
    ) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<RecipeResponse> getRecipes() {
        return recipeRepository.findAllByOwnerSubjectOrderByIdAsc(currentUser.subject())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecipeResponse getRecipe(Long id) {
        return toResponse(findRecipe(id));
    }

    @Transactional
    public RecipeResponse createRecipe(RecipeRequest request) {
        String ownerSubject = currentUser.subject();
        validateIngredientNames(request);

        Recipe recipe = new Recipe(
                request.title().strip(),
                request.instructions().strip(),
                request.prepTimeMinutes(),
                request.servings()
        );

        recipe.assignOwner(ownerSubject);

        updateIngredients(recipe, request.ingredients());

        Recipe savedRecipe = recipeRepository.saveAndFlush(recipe);
        return toResponse(savedRecipe);
    }

    @Transactional
    public RecipeResponse updateRecipe(
            Long id,
            RecipeRequest request
    ) {
        Recipe recipe = findRecipe(id);

        validateIngredientNames(request);

        recipe.updateDetails(
                request.title().strip(),
                request.instructions().strip(),
                request.prepTimeMinutes(),
                request.servings()
        );

        updateIngredients(recipe, request.ingredients());

        recipeRepository.flush();

        return toResponse(recipe);
    }

    @Transactional
    public void deleteRecipe(Long id) {
        Recipe recipe = findRecipe(id);

        recipeRepository.delete(recipe);
        recipeRepository.flush();
    }

    private Recipe findRecipe(Long id) {
        return recipeRepository.findByIdAndOwnerSubject(id, currentUser.subject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Rezept nicht gefunden."
                ));
    }

    private void updateIngredients(
            Recipe recipe,
            List<RecipeIngredientRequest> requests
    ) {
        Map<Long, RecipeIngredient> existingIngredients = new HashMap<>();

        for (RecipeIngredient item : recipe.getIngredients()) {
            existingIngredients.put(
                    item.getIngredient().getId(),
                    item
            );
        }

        List<RecipeIngredient> updatedIngredients = new ArrayList<>();

        for (RecipeIngredientRequest request : requests) {
            Ingredient ingredient = findOrCreateIngredient(
                    request.ingredientName()
            );

            RecipeIngredient item = existingIngredients.get(
                    ingredient.getId()
            );

            if (item == null) {
                item = new RecipeIngredient(
                        recipe,
                        ingredient,
                        request.quantity(),
                        request.unit()
                );
            } else {
                item.updateAmount(
                        request.quantity(),
                        request.unit()
                );
            }

            updatedIngredients.add(item);
        }

        recipe.replaceIngredients(updatedIngredients);
    }

    private void validateIngredientNames(RecipeRequest request) {
        Set<String> names = new HashSet<>();

        for (RecipeIngredientRequest item : request.ingredients()) {
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