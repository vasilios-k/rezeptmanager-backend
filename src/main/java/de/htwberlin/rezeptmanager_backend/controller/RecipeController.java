package de.htwberlin.rezeptmanager_backend.controller;

import de.htwberlin.rezeptmanager_backend.dto.request.RecipeRequest;
import de.htwberlin.rezeptmanager_backend.dto.response.RecipeResponse;
import de.htwberlin.rezeptmanager_backend.service.RecipeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping
    public List<RecipeResponse> getRecipes() {
        return recipeService.getRecipes();
    }

    @GetMapping("/{id}")
    public RecipeResponse getRecipe(
            @PathVariable("id") Long id
    ) {
        return recipeService.getRecipe(id);
    }

    @PostMapping
    public ResponseEntity<RecipeResponse> createRecipe(
            @Valid @RequestBody RecipeRequest request
    ) {
        RecipeResponse recipe = recipeService.createRecipe(request);

        return ResponseEntity
                .created(URI.create("/api/recipes/" + recipe.id()))
                .body(recipe);
    }

    @PutMapping("/{id}")
    public RecipeResponse updateRecipe(
            @PathVariable("id") Long id,
            @Valid @RequestBody RecipeRequest request
    ) {
        return recipeService.updateRecipe(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(
            @PathVariable("id") Long id
    ) {
        recipeService.deleteRecipe(id);

        return ResponseEntity.noContent().build();
    }
}