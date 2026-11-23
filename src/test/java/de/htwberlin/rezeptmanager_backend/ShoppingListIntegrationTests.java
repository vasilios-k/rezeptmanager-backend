package de.htwberlin.rezeptmanager_backend;

import de.htwberlin.rezeptmanager_backend.dto.ShoppingListDocument;
import de.htwberlin.rezeptmanager_backend.dto.ShoppingListDocument.Data;
import de.htwberlin.rezeptmanager_backend.dto.ShoppingListDocument.SavedPrice;
import de.htwberlin.rezeptmanager_backend.dto.ShoppingListDocument.Selection;
import de.htwberlin.rezeptmanager_backend.model.Unit;
import de.htwberlin.rezeptmanager_backend.repository.RecipeRepository;
import de.htwberlin.rezeptmanager_backend.repository.ShoppingListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ShoppingListIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired ShoppingListRepository lists;
    @Autowired RecipeRepository recipes;

    @BeforeEach
    void reset() {
        lists.deleteAll();
        recipes.deleteAll();
    }

    @Test
    void bothRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/shopping-list")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/shopping-list").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ShoppingListDocument(null, Data.empty()))))
                .andExpect(status().isUnauthorized());
        assertEquals(0, lists.count());
    }

    @Test
    void aNewAccountHasAnEmptyUnpersistedList() throws Exception {
        ShoppingListDocument document = load("alice");
        assertNull(document.version());
        assertEquals(Data.empty(), document.data());
        assertEquals(0, lists.count());
    }

    @Test
    void anotherDeviceRestoresSelectionsChecksPricesAndPantry() throws Exception {
        long id = recipe("alice");
        var price = price(new BigDecimal("1000"), Unit.GRAM, "EUR", LocalDate.of(2025, 1, 1));
        Data data = new Data(List.of(new Selection(id, 4)), List.of("1:GRAM:400"),
                Map.of("1:GRAM", price), Map.of("1:GRAM", "1:GRAM:400"));
        ShoppingListDocument saved = save("alice", null, data);

        assertNotNull(saved.version());
        assertEquals(data, load("alice").data());
        assertEquals(Data.empty(), load("bob").data());
        assertEquals(1, lists.count());
        mvc.perform(get("/api/shopping-list").with(jwt().jwt(t -> t.subject("auth0|alice"))))
                .andExpect(jsonPath("$.ownerSubject").doesNotExist());
    }

    @Test
    void eachAccountWritesOnlyItsOwnDocument() throws Exception {
        save("alice", null, checked("alice-item"));
        save("bob", null, checked("bob-item"));
        assertEquals(List.of("alice-item"), load("alice").data().checkedKeys());
        assertEquals(List.of("bob-item"), load("bob").data().checkedKeys());
        assertEquals(2, lists.count());
    }

    @Test
    void ignoresForeignAndDeletedRecipesWithoutRevealingTheirExistence() throws Exception {
        long foreign = recipe("alice");
        Data data = new Data(List.of(new Selection(foreign, 2), new Selection(Long.MAX_VALUE, 2)),
                List.of("stale"), Map.of(), Map.of());
        var saved = save("bob", null, data);
        assertEquals(List.of(), saved.data().selections());
        assertEquals(List.of(), saved.data().checkedKeys());
        assertEquals("auth0|alice", recipes.findById(foreign).orElseThrow().getOwnerSubject());
    }

    @Test
    void respectsMinimumServingsAndDropsStaleChecks() throws Exception {
        long id = recipe("alice");
        Data data = new Data(List.of(new Selection(id, 1)), List.of("old-amount"), Map.of(), Map.of());
        var saved = save("alice", null, data);
        assertEquals(List.of(new Selection(id, 2)), saved.data().selections());
        assertEquals(List.of(), saved.data().checkedKeys());
    }

    @Test
    void removesARecipeDeletedOnAnotherDeviceWhenReading() throws Exception {
        long id = recipe("alice");
        save("alice", null, new Data(List.of(new Selection(id, 2)), List.of("old"), Map.of(), Map.of()));
        recipes.deleteById(id);
        assertEquals(List.of(), load("alice").data().selections());
        assertEquals(List.of(), load("alice").data().checkedKeys());
    }

    @Test
    void refusesStaleVersionsInsteadOfOverwritingAnotherDevice() throws Exception {
        var first = save("alice", null, Data.empty());
        var updated = save("alice", first.version(), checked("new"));
        assertTrue(updated.version() > first.version());

        mvc.perform(put("/api/shopping-list").with(jwt().jwt(t -> t.subject("auth0|alice")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ShoppingListDocument(first.version(), checked("old")))))
                .andExpect(status().isConflict());
        assertEquals(List.of("new"), load("alice").data().checkedKeys());
    }

    @Test
    void refusesASecondInitialImport() throws Exception {
        save("alice", null, checked("first"));
        mvc.perform(put("/api/shopping-list").with(jwt().jwt(t -> t.subject("auth0|alice")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ShoppingListDocument(null, checked("old-browser")))))
                .andExpect(status().isConflict());
        assertEquals(List.of("first"), load("alice").data().checkedKeys());
    }

    @Test
    void rejectsMalformedPayloadsBeforeWriting() throws Exception {
        for (String body : List.of(
                "{\"version\":-1,\"data\":null}",
                "{\"version\":null,\"data\":{\"selections\":[{\"recipeId\":1,\"servings\":0}],\"checkedKeys\":[],\"prices\":{},\"pantry\":{}}}",
                "{\"version\":null,\"data\":{\"selections\":[],\"checkedKeys\":[],\"prices\":{},\"pantry\":{\"invalid\":\"x\"}}}"
        )) {
            mvc.perform(put("/api/shopping-list").with(jwt().jwt(t -> t.subject("auth0|alice")))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, lists.count());
    }

    @Test
    void validatesCurrencyDateAndCompatiblePackageUnits() throws Exception {
        for (SavedPrice invalid : List.of(
                price(BigDecimal.ONE, Unit.GRAM, "USD", LocalDate.of(2025, 1, 1)),
                price(BigDecimal.ONE, Unit.GRAM, "EUR", LocalDate.now().plusDays(1)),
                price(BigDecimal.ONE, Unit.LITER, "EUR", LocalDate.of(2025, 1, 1)),
                price(new BigDecimal("1.5"), Unit.PIECE, "EUR", LocalDate.of(2025, 1, 1))
        )) {
            String key = invalid.packageUnit() == Unit.PIECE ? "1:PIECE" : "1:GRAM";
            Data data = new Data(List.of(), List.of(), Map.of(key, invalid), Map.of());
            mvc.perform(put("/api/shopping-list").with(jwt().jwt(t -> t.subject("auth0|alice")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new ShoppingListDocument(null, data))))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, lists.count());
    }

    @Test
    void concurrentWritesWithTheSameVersionCannotBothSucceed() throws Exception {
        var first = save("alice", null, Data.empty());
        assertConcurrentWrites(first.version());
    }

    @Test
    void twoDevicesCannotBothCreateAnInitialDocument() throws Exception {
        assertConcurrentWrites(null);
        assertEquals(1, lists.count());
    }

    private void assertConcurrentWrites(Long version) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> one = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return mvc.perform(put("/api/shopping-list").with(jwt().jwt(t -> t.subject("auth0|alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ShoppingListDocument(version, checked("one")))))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> two = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return mvc.perform(put("/api/shopping-list").with(jwt().jwt(t -> t.subject("auth0|alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new ShoppingListDocument(version, checked("two")))))
                        .andReturn().getResponse().getStatus();
            };
            var left = executor.submit(one);
            var right = executor.submit(two);
            start.countDown();
            assertEquals(List.of(200, 409), List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS))
                    .stream().sorted().toList());
        }
    }

    private ShoppingListDocument load(String user) throws Exception {
        String body = mvc.perform(get("/api/shopping-list").with(jwt().jwt(t -> t.subject("auth0|" + user))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(body, ShoppingListDocument.class);
    }

    private ShoppingListDocument save(String user, Long version, Data data) throws Exception {
        String body = mvc.perform(put("/api/shopping-list").with(jwt().jwt(t -> t.subject("auth0|" + user)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new ShoppingListDocument(version, data))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readValue(body, ShoppingListDocument.class);
    }

    private long recipe(String user) throws Exception {
        String location = mvc.perform(post("/api/recipes").with(jwt().jwt(t -> t.subject("auth0|" + user)))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"title":"Pfannkuchen", "ingredients":[{"ingredientName":"Mehl","quantity":200,"unit":"GRAM"}],
                     "instructions":"Ausbacken.", "prepTimeMinutes":20, "servings":2}
                    """))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        assertNotNull(location);
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private Data checked(String key) {
        return new Data(List.of(), List.of(key), Map.of(), Map.of());
    }

    private SavedPrice price(BigDecimal quantity, Unit unit, String currency, LocalDate date) {
        return new SavedPrice(null, null, "Mehl", quantity, unit, 119L, currency,
                "Beispielmarkt", null, date, "MANUAL");
    }
}
