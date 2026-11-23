package de.htwberlin.rezeptmanager_backend.dto;

import de.htwberlin.rezeptmanager_backend.model.Unit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ShoppingListDocument(
        @PositiveOrZero Long version,
        @NotNull @Valid Data data
) {
    public static final String PRICE_KEY = "[1-9][0-9]*:(GRAM|MILLILITER|PIECE|TEASPOON|TABLESPOON|PINCH)";

    public record Data(
            @NotNull @Size(max = 1000) List<@NotNull @Valid Selection> selections,
            @NotNull @Size(max = 2000) List<@NotBlank @Size(max = 180) String> checkedKeys,
            @NotNull @Size(max = 2000) Map<@Pattern(regexp = PRICE_KEY) String, @NotNull @Valid SavedPrice> prices,
            @NotNull @Size(max = 2000) Map<@Pattern(regexp = PRICE_KEY) String, @NotBlank @Size(max = 180) String> pantry
    ) {
        public static Data empty() {
            return new Data(List.of(), List.of(), Map.of(), Map.of());
        }
    }

    public record Selection(@NotNull @Positive Long recipeId, @Positive int servings) {
    }

    public record SavedPrice(
            @Positive Long priceId,
            @Positive Long productId,
            @NotBlank @Size(max = 1000) String productName,
            @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("1000000000") BigDecimal packageQuantity,
            @NotNull Unit packageUnit,
            @NotNull @PositiveOrZero @Max(1_000_000_000) Long priceCents,
            @NotNull @Pattern(regexp = "EUR") String currency,
            @NotNull @Size(max = 500) String shop,
            @Size(max = 500) String city,
            @NotNull @PastOrPresent LocalDate priceDate,
            @NotNull @Pattern(regexp = "MANUAL|OPEN_PRICES") String source
    ) {
    }
}
