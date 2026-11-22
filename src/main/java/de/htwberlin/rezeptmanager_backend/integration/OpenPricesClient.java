package de.htwberlin.rezeptmanager_backend.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OpenPricesClient {
    private static final Logger log =
            LoggerFactory.getLogger(OpenPricesClient.class);

    private final RestClient client;

    public OpenPricesClient() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(25));

        client = RestClient.builder()
                .baseUrl("https://prices.openfoodfacts.org/api/v1")
                .requestFactory(factory)
                .defaultHeader(
                        "User-Agent",
                        "Rezeptmanager-WebTech/1.0 (+https://github.com/vasilios-k/rezeptmanager-backend)"
                )
                .defaultHeader("Accept", "application/json")
                .build();
    }

    OpenPricesClient(RestClient client) {
        this.client = client;
    }

    public ApiPage<Product> searchProducts(String query, int page) {
        try {
            ApiPage<Product> result = client.get()
                    .uri(builder -> builder.path("/products")
                            .queryParam("product_name__like", "{query}")
                            .queryParam("price_count__gte", 1)
                            .queryParam("source", "off")
                            .queryParam("size", 20)
                            .queryParam("page", page)
                            .build(query))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return requirePage(result);
        } catch (RestClientException error) {
            throw unavailable(error);
        }
    }

    public List<Price> getPrices(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        String ids = productIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        try {
            ApiPage<Price> result = client.get()
                    .uri(builder -> builder.path("/prices")
                            .queryParam("product_id__in", ids)
                            .queryParam("currency", "EUR")
                            .queryParam("type", "PRODUCT")
                            .queryParam("price_is_discounted", false)
                            .queryParam("duplicate_of__isnull", true)
                            .queryParam("order_by", "-date")
                            .queryParam("size", 100)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            return requirePage(result).items();
        } catch (RestClientException error) {
            throw unavailable(error);
        }
    }

    private <T> ApiPage<T> requirePage(ApiPage<T> page) {
        if (page == null || page.items() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Ungültige Antwort des Preisdienstes."
            );
        }

        return page;
    }

    private ResponseStatusException unavailable(RestClientException error) {
        log.warn(
                "Open Prices ist nicht erreichbar: {}",
                error.getClass().getSimpleName()
        );

        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Der Preisdienst ist gerade nicht erreichbar. Bitte später erneut versuchen.",
                error
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiPage<T>(
            List<T> items,
            Integer pages
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(
            Long id,
            String source,
            @JsonProperty("product_name") String name,
            String brands,
            @JsonProperty("product_quantity") BigDecimal quantity,
            @JsonProperty("product_quantity_unit") String unit
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(
            Long id,
            @JsonProperty("osm_name") String name,
            @JsonProperty("osm_brand") String brand,
            @JsonProperty("osm_address_city") String city,
            @JsonProperty("osm_address_country_code") String country
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Price(
            Long id,
            String type,
            Product product,
            Location location,
            BigDecimal price,
            String currency,
            LocalDate date,
            @JsonProperty("price_per") String pricePer,
            @JsonProperty("price_is_discounted") Boolean discounted,
            @JsonProperty("duplicate_of") Long duplicateOf
    ) {}
}