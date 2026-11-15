package de.htwberlin.rezeptmanager_backend.service;

import de.htwberlin.rezeptmanager_backend.dto.request.RecipeIngredientRequest;
import de.htwberlin.rezeptmanager_backend.dto.request.RecipeRequest;
import de.htwberlin.rezeptmanager_backend.dto.response.RecipeResponse;
import de.htwberlin.rezeptmanager_backend.model.RecipeIngredient;
import de.htwberlin.rezeptmanager_backend.model.Unit;
import de.htwberlin.rezeptmanager_backend.repository.IngredientRepository;
import de.htwberlin.rezeptmanager_backend.repository.RecipeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecipeDeletionIntegrationTests {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void deletesRecipeAndItsIngredientLinks() {
        RecipeResponse created = recipeService.createRecipe(
                new RecipeRequest(
                        "Zum Löschen",
                        List.of(
                                new RecipeIngredientRequest(
                                        "Mehl",
                                        new BigDecimal("200"),
                                        Unit.GRAM
                                ),
                                new RecipeIngredientRequest(
                                        "Milch",
                                        new BigDecimal("300"),
                                        Unit.MILLILITER
                                )
                        ),
                        "Zutaten vermischen.",
                        10,
                        2
                )
        );

        List<Long> ingredientLinkIds = recipeRepository
                .findById(created.id())
                .orElseThrow()
                .getIngredients()
                .stream()
                .map(RecipeIngredient::getId)
                .toList();

        assertEquals(2, ingredientLinkIds.size());

        recipeService.deleteRecipe(created.id());

        entityManager.clear();

        assertTrue(
                recipeRepository.findById(created.id()).isEmpty()
        );
        assertEquals(0L, recipeRepository.count());

        for (Long linkId : ingredientLinkIds) {
            assertNull(
                    entityManager.find(
                            RecipeIngredient.class,
                            linkId
                    )
            );
        }

        assertEquals(2L, ingredientRepository.count());
    }

    @Test
    void preservesSharedIngredientsAndOtherRecipes() {
        RecipeResponse first = recipeService.createRecipe(
                request("Erstes Rezept", "200")
        );

        RecipeResponse second = recipeService.createRecipe(
                request("Zweites Rezept", "500")
        );

        Long sharedIngredientId = first.ingredients()
                .get(0)
                .ingredient()
                .id();

        recipeService.deleteRecipe(first.id());

        entityManager.clear();

        RecipeResponse remaining = recipeService.getRecipe(second.id());

        assertEquals(1L, recipeRepository.count());
        assertEquals(1L, ingredientRepository.count());
        assertEquals(second.id(), remaining.id());
        assertEquals("Zweites Rezept", remaining.title());

        assertEquals(
                sharedIngredientId,
                remaining.ingredients().get(0).ingredient().id()
        );

        assertEquals(
                0,
                new BigDecimal("500").compareTo(
                        remaining.ingredients().get(0).quantity()
                )
        );
    }

    @Test
    void rejectsUnknownIdAndPreservesExistingRecipe() {
        RecipeResponse existing = recipeService.createRecipe(
                request("Bleibt erhalten", "200")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recipeService.deleteRecipe(Long.MAX_VALUE)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        entityManager.clear();

        RecipeResponse remaining = recipeService.getRecipe(existing.id());

        assertEquals(existing.id(), remaining.id());
        assertEquals("Bleibt erhalten", remaining.title());
        assertEquals(1L, recipeRepository.count());
        assertEquals(1L, ingredientRepository.count());
    }

    private RecipeRequest request(String title, String quantity) {
        return new RecipeRequest(
                title,
                List.of(
                        new RecipeIngredientRequest(
                                "Mehl",
                                new BigDecimal(quantity),
                                Unit.GRAM
                        )
                ),
                "Zutaten vermischen.",
                10,
                2
        );
    }
}