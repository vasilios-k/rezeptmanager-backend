package de.htwberlin.rezeptmanager_backend.service;

import de.htwberlin.rezeptmanager_backend.dto.ShoppingListDocument;
import de.htwberlin.rezeptmanager_backend.dto.ShoppingListDocument.Data;
import de.htwberlin.rezeptmanager_backend.dto.ShoppingListDocument.Selection;
import de.htwberlin.rezeptmanager_backend.model.Recipe;
import de.htwberlin.rezeptmanager_backend.model.SavedShoppingList;
import de.htwberlin.rezeptmanager_backend.model.Unit;
import de.htwberlin.rezeptmanager_backend.repository.RecipeRepository;
import de.htwberlin.rezeptmanager_backend.repository.ShoppingListRepository;
import de.htwberlin.rezeptmanager_backend.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ShoppingListService {
    private final ShoppingListRepository lists;
    private final RecipeRepository recipes;
    private final CurrentUser currentUser;
    private final JsonMapper json;

    public ShoppingListService(ShoppingListRepository lists, RecipeRepository recipes,
                               CurrentUser currentUser, JsonMapper json) {
        this.lists = lists;
        this.recipes = recipes;
        this.currentUser = currentUser;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ShoppingListDocument get() {
        String owner = currentUser.subject();
        return lists.findById(owner)
                .map(saved -> new ShoppingListDocument(saved.getVersion(),
                        normalize(json.readValue(saved.getPayload(), Data.class), owner)))
                .orElseGet(() -> new ShoppingListDocument(null, Data.empty()));
    }

    @Transactional
    public ShoppingListDocument save(ShoppingListDocument request) {
        String owner = currentUser.subject();
        SavedShoppingList saved = lists.findById(owner).orElse(null);
        Long currentVersion = saved == null ? null : saved.getVersion();

        if (!Objects.equals(currentVersion, request.version())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Die Einkaufsliste wurde auf einem anderen Gerät geändert.");
        }

        Data data = normalize(request.data(), owner);
        String payload = json.writeValueAsString(data);
        if (payload.length() > 2_000_000) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                    "Die Einkaufsliste ist zu groß.");
        }

        if (saved == null) saved = new SavedShoppingList(owner, payload);
        else saved.setPayload(payload);

        saved = lists.saveAndFlush(saved);
        return new ShoppingListDocument(saved.getVersion(), data);
    }

    private Data normalize(Data data, String owner) {
        Map<Long, Recipe> ownRecipes = recipes.findAllByOwnerSubjectOrderByIdAsc(owner)
                .stream().collect(Collectors.toMap(Recipe::getId, Function.identity()));
        Map<Long, Selection> selections = new LinkedHashMap<>();

        for (Selection selection : data.selections()) {
            Recipe recipe = ownRecipes.get(selection.recipeId());
            // Gelöschte und fremde Rezept-IDs werden gleich behandelt.
            if (recipe != null) {
                selections.put(recipe.getId(), new Selection(recipe.getId(),
                        Math.max(recipe.getServings(), selection.servings())));
            }
        }

        data.prices().forEach((key, price) -> {
            Unit baseUnit = switch (price.packageUnit()) {
                case KILOGRAM -> Unit.GRAM;
                case LITER -> Unit.MILLILITER;
                default -> price.packageUnit();
            };
            if (!key.endsWith(":" + baseUnit.name())
                    || (price.packageUnit() == Unit.PIECE
                    && price.packageQuantity().stripTrailingZeros().scale() > 0)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Preis und Zutatenmenge müssen zusammenpassen.");
            }
        });
        data.pantry().forEach((key, signature) -> {
            if (!signature.startsWith(key + ":")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Die Vorratsmarkierung passt nicht zur Zutat.");
            }
        });

        var normalizedSelections = selections.values().stream().toList();
        boolean changed = !normalizedSelections.equals(data.selections());
        return new Data(normalizedSelections,
                changed ? java.util.List.of() : data.checkedKeys().stream().distinct().toList(),
                data.prices(), data.pantry());
    }
}
