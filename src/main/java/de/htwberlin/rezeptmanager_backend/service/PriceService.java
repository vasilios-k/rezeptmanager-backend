package de.htwberlin.rezeptmanager_backend.service;

import de.htwberlin.rezeptmanager_backend.dto.response.PriceSearchResponse;
import de.htwberlin.rezeptmanager_backend.dto.response.ProductPriceResponse;
import de.htwberlin.rezeptmanager_backend.integration.OpenPricesClient;
import de.htwberlin.rezeptmanager_backend.model.Unit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PriceService {
    private static final Pattern PIECE_LABEL = Pattern.compile(
            "^\\s*([0-9]{1,10})\\s*(?:stück(?:e)?|stueck(?:e)?|stk\\.?|st\\.?|pcs?\\.?|pieces?|eggs?|eier|ei|stuks?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
    );

    private final OpenPricesClient client;
    private final Map<SearchKey, CachedSearch> cache = new LinkedHashMap<>();

    public PriceService(OpenPricesClient client) {
        this.client = client;
    }

    public PriceSearchResponse search(String query, Unit unit, int page) {
        String term = query == null ? "" : query.strip();

        if (term.length() < 2 || term.length() > 60
                || page < 1 || page > 100 || unit == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ungültige Preissuche."
            );
        }

        Unit baseUnit = baseUnit(unit);

        if (baseUnit != Unit.GRAM
                && baseUnit != Unit.MILLILITER
                && baseUnit != Unit.PIECE) {
            return new PriceSearchResponse(List.of(), page, false);
        }

        SearchKey key = new SearchKey(
                term.toLowerCase(Locale.ROOT),
                baseUnit,
                page
        );

        synchronized (cache) {
            CachedSearch cached = cache.get(key);

            if (cached != null
                    && cached.expiresAt().isAfter(Instant.now())) {
                return cached.result();
            }
        }

        OpenPricesClient.ApiPage<OpenPricesClient.Product> products =
                client.searchProducts(term, page);

        List<Long> ids = products.items().stream()
                .filter(product -> product != null
                        && product.id() != null
                        && product.id() > 0)
                .filter(product -> hasCompatiblePack(product, baseUnit))
                .map(OpenPricesClient.Product::id)
                .distinct()
                .toList();

        Map<String, ProductPriceResponse> latestByProductAndShop =
                new HashMap<>();

        for (OpenPricesClient.Price price : client.getPrices(ids)) {
            ProductPriceResponse offer = toOffer(price, baseUnit, ids);

            if (offer == null) {
                continue;
            }

            String offerKey =
                    offer.productId() + ":" + price.location().id();

            latestByProductAndShop.merge(
                    offerKey,
                    offer,
                    (first, second) ->
                            second.priceDate().isAfter(first.priceDate())
                                    ? second
                                    : first
            );
        }

        List<ProductPriceResponse> offers =
                latestByProductAndShop.values().stream()
                        .sorted(
                                Comparator.comparing(
                                                ProductPriceResponse::priceDate
                                        )
                                        .reversed()
                                        .thenComparing(
                                                ProductPriceResponse::productName
                                        )
                                        .thenComparing(
                                                ProductPriceResponse::shop
                                        )
                        )
                        .toList();

        PriceSearchResponse result = new PriceSearchResponse(
                offers,
                page,
                products.pages() != null
                        && page < products.pages()
                        && page < 100
        );

        synchronized (cache) {
            cache.put(
                    key,
                    new CachedSearch(
                            Instant.now().plusSeconds(900),
                            result
                    )
            );

            if (cache.size() > 64) {
                cache.remove(cache.keySet().iterator().next());
            }
        }

        return result;
    }

    private ProductPriceResponse toOffer(
            OpenPricesClient.Price price,
            Unit unit,
            List<Long> ids
    ) {
        if (price == null
                || price.id() == null
                || price.product() == null
                || price.location() == null
                || price.location().id() == null
                || !ids.contains(price.product().id())
                || !"off".equals(price.product().source())
                || !"PRODUCT".equals(price.type())
                || !"EUR".equals(price.currency())
                || !"DE".equalsIgnoreCase(price.location().country())
                || Boolean.TRUE.equals(price.discounted())
                || price.duplicateOf() != null
                || price.price() == null
                || price.price().signum() <= 0
                || price.date() == null
                || price.date().isAfter(LocalDate.now(ZoneOffset.UTC))
                || (price.pricePer() != null
                && !price.pricePer().isBlank()
                && !"UNIT".equals(price.pricePer()))) {
            return null;
        }

        Pack pack = pack(price.product(), unit);

        if (pack == null
                || pack.unit() != unit
                || price.product().name() == null
                || price.product().name().isBlank()) {
            return null;
        }

        long cents;

        try {
            cents = price.price()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException error) {
            return null;
        }

        if (cents > 1_000_000_000L) {
            return null;
        }

        String productName = price.product().name().strip();

        if (price.product().brands() != null
                && !price.product().brands().isBlank()) {
            productName += " · " + price.product().brands().strip();
        }

        String shop = price.location().name();

        if (shop == null || shop.isBlank()) {
            shop = price.location().brand();
        }

        if (shop == null || shop.isBlank()) {
            shop = "Unbekannter Händler";
        }

        return new ProductPriceResponse(
                price.id(),
                price.product().id(),
                productName,
                pack.quantity(),
                pack.unit(),
                cents,
                "EUR",
                shop,
                price.location().city(),
                price.date(),
                "OPEN_PRICES"
        );
    }

    private boolean hasCompatiblePack(
            OpenPricesClient.Product product,
            Unit unit
    ) {
        Pack pack = pack(product, unit);
        return pack != null && pack.unit() == unit;
    }

    private Pack pack(
            OpenPricesClient.Product product,
            Unit requestedUnit
    ) {
        Pack structured = structuredPack(product);

        if (requestedUnit != Unit.PIECE
                || product.packageLabel() == null) {
            return structured;
        }

        Matcher matcher = PIECE_LABEL.matcher(product.packageLabel());

        if (!matcher.matches()) {
            return structured;
        }

        BigDecimal count = new BigDecimal(matcher.group(1));

        if (count.signum() <= 0
                || count.compareTo(
                BigDecimal.valueOf(1_000_000_000)
        ) > 0) {
            return null;
        }

        if (structured != null
                && structured.unit() == Unit.PIECE
                && structured.quantity().compareTo(count) != 0) {
            return null;
        }

        return new Pack(count, Unit.PIECE);
    }

    private Pack structuredPack(OpenPricesClient.Product product) {
        if (product.quantity() == null
                || product.quantity().signum() <= 0
                || product.unit() == null) {
            return null;
        }

        BigDecimal factor = BigDecimal.ONE;
        Unit unit;

        switch (product.unit().strip().toLowerCase(Locale.ROOT)) {
            case "g", "gram", "grams" -> unit = Unit.GRAM;
            case "kg" -> {
                unit = Unit.GRAM;
                factor = BigDecimal.valueOf(1000);
            }
            case "ml" -> unit = Unit.MILLILITER;
            case "cl" -> {
                unit = Unit.MILLILITER;
                factor = BigDecimal.TEN;
            }
            case "l" -> {
                unit = Unit.MILLILITER;
                factor = BigDecimal.valueOf(1000);
            }
            case "piece", "pieces", "pc", "pcs", "stk", "stück", "stueck" ->
                    unit = Unit.PIECE;
            default -> {
                return null;
            }
        }

        BigDecimal quantity = product.quantity().multiply(factor);

        if (quantity.compareTo(
                BigDecimal.valueOf(1_000_000_000)
        ) > 0) {
            return null;
        }

        if (unit == Unit.PIECE
                && quantity.stripTrailingZeros().scale() > 0) {
            return null;
        }

        return new Pack(quantity, unit);
    }

    private Unit baseUnit(Unit unit) {
        return switch (unit) {
            case KILOGRAM -> Unit.GRAM;
            case LITER -> Unit.MILLILITER;
            default -> unit;
        };
    }

    private record Pack(BigDecimal quantity, Unit unit) {}

    private record SearchKey(String query, Unit unit, int page) {}

    private record CachedSearch(
            Instant expiresAt,
            PriceSearchResponse result
    ) {}
}