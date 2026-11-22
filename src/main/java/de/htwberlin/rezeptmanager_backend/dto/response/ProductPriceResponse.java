package de.htwberlin.rezeptmanager_backend.dto.response;

import de.htwberlin.rezeptmanager_backend.model.Unit;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductPriceResponse(
        Long priceId,
        Long productId,
        String productName,
        BigDecimal packageQuantity,
        Unit packageUnit,
        long priceCents,
        String currency,
        String shop,
        String city,
        LocalDate priceDate,
        String source
) {
}