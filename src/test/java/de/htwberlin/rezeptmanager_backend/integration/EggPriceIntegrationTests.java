package de.htwberlin.rezeptmanager_backend.integration;

import de.htwberlin.rezeptmanager_backend.dto.response.ProductPriceResponse;
import de.htwberlin.rezeptmanager_backend.model.Unit;
import de.htwberlin.rezeptmanager_backend.service.PriceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EggPriceIntegrationTests {
    private MockRestServiceServer server;
    private PriceService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://prices.example.test/api/v1");

        server = MockRestServiceServer.bindTo(builder).build();

        service = new PriceService(
                new OpenPricesClient(builder.build())
        );
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void readsExplicitPieceCountsWhenNumericMetadataIsMissing() {
        String[] products = {
                product(1, "6 pcs", "0", null),
                product(2, "10pcs", "0", null),
                product(3, "12 Stück", "0", null),
                product(4, "6 Stk.", "0", null),
                product(5, "6 eggs", "0", null),
                product(6, "6\u00a0pcs", "0", null),
                product(7, null, "10", "pcs")
        };

        expectSearch(products);

        var offers = service.search("Eier", Unit.PIECE, 1).offers();

        assertEquals(7, offers.size());

        assertEquals(
                List.of(6L, 6L, 6L, 6L, 10L, 10L, 12L),
                offers.stream()
                        .map(offer ->
                                offer.packageQuantity().longValueExact()
                        )
                        .sorted()
                        .toList()
        );

        assertTrue(
                offers.stream().allMatch(
                        offer -> offer.packageUnit() == Unit.PIECE
                )
        );
    }

    @Test
    void keepsBothExplicitWeightAndExplicitPieceCount() {
        String[] products = {
                product(1, "10pcs", "600", "g")
        };

        expectSearch(products);
        expectSearch(products);

        ProductPriceResponse pieces =
                service.search("Eier", Unit.PIECE, 1)
                        .offers().getFirst();

        ProductPriceResponse weight =
                service.search("Eier", Unit.GRAM, 1)
                        .offers().getFirst();

        assertEquals(10, pieces.packageQuantity().intValueExact());
        assertEquals(Unit.PIECE, pieces.packageUnit());

        assertEquals(600, weight.packageQuantity().intValueExact());
        assertEquals(Unit.GRAM, weight.packageUnit());
    }

    @Test
    void rejectsUnknownAmbiguousAndContradictoryCounts() {
        String[] products = {
                product(1, "10unknown", "0", null),
                product(2, "10", "0", null),
                product(3, "6 x 2 pcs", "0", null),
                product(4, "0 pcs", "0", null),
                product(5, "6.5 pcs", "0", null),
                product(6, "6 pcs", "10", "pcs"),
                product(7, "6 g", "6", "g")
        };

        server.expect(queryParam("product_name__like", "Eier"))
                .andRespond(withSuccess(
                        page(products),
                        MediaType.APPLICATION_JSON
                ));

        assertTrue(
                service.search("Eier", Unit.PIECE, 1)
                        .offers().isEmpty()
        );
    }

    private void expectSearch(String[] products) {
        server.expect(queryParam("product_name__like", "Eier"))
                .andRespond(withSuccess(
                        page(products),
                        MediaType.APPLICATION_JSON
                ));

        String ids = IntStream.rangeClosed(1, products.length)
                .mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));

        String[] prices = IntStream.range(0, products.length)
                .mapToObj(index -> price(index + 1, products[index]))
                .toArray(String[]::new);

        server.expect(queryParam("product_id__in", ids))
                .andRespond(withSuccess(
                        page(prices),
                        MediaType.APPLICATION_JSON
                ));
    }

    private String product(
            int id,
            String label,
            String quantity,
            String unit
    ) {
        return """
                {"id":%d,"source":"off","product_name":"Eier %d","brands":"Testmarke",
                 "product_quantity":%s,"product_quantity_unit":%s,"quantity":%s}
                """.formatted(
                id,
                id,
                quantity,
                jsonString(unit),
                jsonString(label)
        );
    }

    private String price(int id, String product) {
        return """
                {"id":%d,"type":"PRODUCT","product":%s,
                 "location":{"id":12,"osm_name":"Testmarkt","osm_address_country_code":"DE"},
                 "price":1.99,"currency":"EUR","date":"%s","price_is_discounted":false,"price_per":"UNIT"}
                """.formatted(
                id,
                product,
                LocalDate.now(ZoneOffset.UTC).minusDays(1)
        );
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String page(String... items) {
        return "{\"pages\":1,\"items\":["
                + String.join(",", items)
                + "]}";
    }
}