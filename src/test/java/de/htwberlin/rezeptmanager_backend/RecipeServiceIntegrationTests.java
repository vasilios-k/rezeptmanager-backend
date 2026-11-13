package de.htwberlin.rezeptmanager_backend;

import de.htwberlin.rezeptmanager_backend.dto.request.CreateRecipeIngredientRequest;
import de.htwberlin.rezeptmanager_backend.dto.request.CreateRecipeRequest;
import de.htwberlin.rezeptmanager_backend.dto.response.RecipeResponse;
import de.htwberlin.rezeptmanager_backend.model.Unit;
import de.htwberlin.rezeptmanager_backend.repository.IngredientRepository;
import de.htwberlin.rezeptmanager_backend.repository.RecipeRepository;
import de.htwberlin.rezeptmanager_backend.service.RecipeService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
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
        CreateRecipeRequest request = new CreateRecipeRequest(
                "Testrezept",
                List.of(
                        new CreateRecipeIngredientRequest(
                                "Mehl",
                                new BigDecimal("200"),
                                Unit.GRAM
                        ),
                        new CreateRecipeIngredientRequest(
                                "Milch",
                                new BigDecimal("300"),
                                Unit.MILLILITER
                        )
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

    private CreateRecipeRequest request(
            String title,
            String ingredientName,
            String quantity
    ) {
        return new CreateRecipeRequest(
                title,
                List.of(
                        new CreateRecipeIngredientRequest(
                                ingredientName,
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