package de.htwberlin.rezeptmanager_backend;

import de.htwberlin.rezeptmanager_backend.dto.request.RecipeIngredientRequest;
import de.htwberlin.rezeptmanager_backend.dto.request.RecipeRequest;
import de.htwberlin.rezeptmanager_backend.dto.response.RecipeResponse;
import de.htwberlin.rezeptmanager_backend.model.Recipe;
import de.htwberlin.rezeptmanager_backend.model.RecipeIngredient;
import de.htwberlin.rezeptmanager_backend.model.Unit;
import de.htwberlin.rezeptmanager_backend.repository.IngredientRepository;
import de.htwberlin.rezeptmanager_backend.repository.RecipeRepository;
import de.htwberlin.rezeptmanager_backend.service.RecipeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "auth0|test-owner")
class RecipeServiceIntegrationTests {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void savesRecipeAndLoadsIngredientsInTheirOriginalOrder() {
        RecipeRequest request = new RecipeRequest(
                "Testrezept",
                List.of(
                        ingredient("Mehl", "200", Unit.GRAM),
                        ingredient("Milch", "300", Unit.MILLILITER)
                ),
                "Zutaten vermischen.",
                10,
                2
        );

        RecipeResponse created = recipeService.createRecipe(request);

        entityManager.clear();

        RecipeResponse loaded = recipeService.getRecipe(created.id());

        assertNotNull(created.id());
        assertEquals(created.id(), loaded.id());
        assertEquals("Testrezept", loaded.title());
        assertEquals(2, loaded.ingredients().size());
        assertEquals("Mehl", loaded.ingredients().get(0).ingredient().name());
        assertEquals("Milch", loaded.ingredients().get(1).ingredient().name());

        assertEquals(
                0,
                new BigDecimal("200").compareTo(
                        loaded.ingredients().get(0).quantity()
                )
        );

        assertEquals(1L, recipeRepository.count());
        assertEquals(2L, ingredientRepository.count());
    }

    @Test
    void reusesIngredientDespiteDifferentCaseAndQuantity() {
        RecipeResponse first = recipeService.createRecipe(
                request("Erstes Rezept", "Mehl", "200")
        );

        RecipeResponse second = recipeService.createRecipe(
                request("Zweites Rezept", " mehl ", "500")
        );

        entityManager.clear();

        RecipeResponse loadedFirst = recipeService.getRecipe(first.id());
        RecipeResponse loadedSecond = recipeService.getRecipe(second.id());

        assertEquals(2L, recipeRepository.count());
        assertEquals(1L, ingredientRepository.count());

        assertEquals(
                loadedFirst.ingredients().get(0).ingredient().id(),
                loadedSecond.ingredients().get(0).ingredient().id()
        );

        assertEquals(
                0,
                new BigDecimal("500").compareTo(
                        loadedSecond.ingredients().get(0).quantity()
                )
        );
    }

    @Test
    void updatesRecipeAndSynchronizesIngredients() {
        RecipeResponse created = recipeService.createRecipe(
                new RecipeRequest(
                        "Pfannkuchen",
                        List.of(
                                ingredient("Mehl", "200", Unit.GRAM),
                                ingredient("Milch", "300", Unit.MILLILITER),
                                ingredient("Eier", "2", Unit.PIECE)
                        ),
                        "Bisherige Anleitung.",
                        25,
                        2
                )
        );

        Recipe original = recipeRepository.findById(created.id())
                .orElseThrow();

        Long originalFlourLinkId = original.getIngredients().get(0).getId();
        Long removedEggLinkId = original.getIngredients().get(2).getId();

        RecipeResponse updated = recipeService.updateRecipe(
                created.id(),
                new RecipeRequest(
                        "Überarbeitete Pfannkuchen",
                        List.of(
                                ingredient("Milch", "400", Unit.MILLILITER),
                                ingredient("Mehl", "0.25", Unit.KILOGRAM),
                                ingredient("Zucker", "20", Unit.GRAM)
                        ),
                        "Neue Anleitung.",
                        30,
                        3
                )
        );

        entityManager.clear();

        RecipeResponse loaded = recipeService.getRecipe(created.id());

        assertEquals(created.id(), updated.id());
        assertEquals(created.id(), loaded.id());
        assertEquals("Überarbeitete Pfannkuchen", loaded.title());
        assertEquals("Neue Anleitung.", loaded.instructions());
        assertEquals(30, loaded.prepTimeMinutes());
        assertEquals(3, loaded.servings());

        assertEquals(3, loaded.ingredients().size());
        assertEquals("Milch", loaded.ingredients().get(0).ingredient().name());
        assertEquals("Mehl", loaded.ingredients().get(1).ingredient().name());
        assertEquals("Zucker", loaded.ingredients().get(2).ingredient().name());

        assertEquals(Unit.KILOGRAM, loaded.ingredients().get(1).unit());

        assertEquals(
                0,
                new BigDecimal("0.25").compareTo(
                        loaded.ingredients().get(1).quantity()
                )
        );

        Recipe reloaded = recipeRepository.findById(created.id())
                .orElseThrow();

        assertEquals(
                originalFlourLinkId,
                reloaded.getIngredients().get(1).getId()
        );

        assertNull(
                entityManager.find(
                        RecipeIngredient.class,
                        removedEggLinkId
                )
        );

        assertEquals(1L, recipeRepository.count());
        assertEquals(4L, ingredientRepository.count());
    }

    @Test
    void rejectsDuplicateIngredientsWhenUpdating() {
        RecipeResponse created = recipeService.createRecipe(
                request("Originalrezept", "Mehl", "200")
        );

        RecipeRequest duplicateRequest = new RecipeRequest(
                "Geänderter Titel",
                List.of(
                        ingredient("Mehl", "200", Unit.GRAM),
                        ingredient(" mehl ", "100", Unit.GRAM)
                ),
                "Geänderte Anleitung.",
                20,
                4
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recipeService.updateRecipe(
                        created.id(),
                        duplicateRequest
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        entityManager.clear();

        RecipeResponse unchanged = recipeService.getRecipe(created.id());

        assertEquals("Originalrezept", unchanged.title());
        assertEquals(1, unchanged.ingredients().size());

        assertEquals(
                0,
                new BigDecimal("200").compareTo(
                        unchanged.ingredients().get(0).quantity()
                )
        );
    }

    @Test
    void returnsNotFoundWhenUpdatingUnknownRecipe() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> recipeService.updateRecipe(
                        Long.MAX_VALUE,
                        request("Unbekanntes Rezept", "Mehl", "200")
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals(0L, recipeRepository.count());
    }

    private RecipeRequest request(
            String title,
            String ingredientName,
            String quantity
    ) {
        return new RecipeRequest(
                title,
                List.of(
                        ingredient(
                                ingredientName,
                                quantity,
                                Unit.GRAM
                        )
                ),
                "Zutaten vermischen.",
                10,
                2
        );
    }

    private RecipeIngredientRequest ingredient(
            String name,
            String quantity,
            Unit unit
    ) {
        return new RecipeIngredientRequest(
                name,
                new BigDecimal(quantity),
                unit
        );
    }
}