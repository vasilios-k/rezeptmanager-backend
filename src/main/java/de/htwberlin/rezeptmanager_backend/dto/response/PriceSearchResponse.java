package de.htwberlin.rezeptmanager_backend.dto.response;

import java.util.List;

public record PriceSearchResponse(
        List<ProductPriceResponse> offers,
        int page,
        boolean hasMore
) {
}