package de.htwberlin.rezeptmanager_backend.integration;

import de.htwberlin.rezeptmanager_backend.controller.PriceController;
import de.htwberlin.rezeptmanager_backend.dto.response.PriceSearchResponse;
import de.htwberlin.rezeptmanager_backend.model.Unit;
import de.htwberlin.rezeptmanager_backend.service.PriceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OpenPricesIntegrationTests {
    private MockRestServiceServer server;
    private PriceService service;

    private final String date =
            LocalDate.now(ZoneOffset.UTC).minusDays(5).toString();

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
    void mapsGermanEuroPackagePricesAndCachesEquivalentQueries() {
        String product = product(1, "1000", "g");

        server.expect(method(HttpMethod.GET))
                .andExpect(queryParam(
                        "product_name__like",
                        "Mehl%20%26%20Zucker"
                ))
                .andExpect(queryParam("source", "off"))
                .andExpect(queryParam("page", "1"))
                .andRespond(withSuccess(
                        page(2, product),
                        MediaType.APPLICATION_JSON
                ));

        server.expect(queryParam("product_id__in", "1"))
                .andExpect(queryParam("currency", "EUR"))
                .andExpect(queryParam("price_is_discounted", "false"))
                .andRespond(withSuccess(
                        page(1, price(
                                10, product, "DE", "EUR",
                                date, "1.29", false, null
                        )),
                        MediaType.APPLICATION_JSON
                ));

        PriceSearchResponse result =
                service.search("Mehl & Zucker", Unit.GRAM, 1);

        assertEquals(1, result.offers().size());

        var offer = result.offers().getFirst();

        assertEquals(129, offer.priceCents());
        assertEquals(
                0,
                new BigDecimal("1000").compareTo(offer.packageQuantity())
        );
        assertEquals(Unit.GRAM, offer.packageUnit());
        assertEquals("Testmarkt", offer.shop());
        assertEquals("OPEN_PRICES", offer.source());
        assertTrue(result.hasMore());

        assertSame(
                result,
                service.search(
                        " mehl & zucker ",
                        Unit.KILOGRAM,
                        1
                )
        );
    }

    @Test
    void excludesForeignDiscountedFutureAndNonPackagePricesAndKeepsLatestPerStore() {
        String product = product(1, "1", "kg");
        expectProducts(product);

        String oldDate =
                LocalDate.now(ZoneOffset.UTC).minusDays(30).toString();

        String future =
                LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();

        server.expect(queryParam("product_id__in", "1"))
                .andRespond(withSuccess(
                        page(
                                1,
                                price(1, product, "DE", "EUR",
                                        oldDate, "1.19", false, "UNIT"),
                                price(2, product, "DE", "EUR",
                                        date, "1.29", false, "UNIT"),
                                price(3, product, "FR", "EUR",
                                        date, "0.99", false, null),
                                price(4, product, "DE", "USD",
                                        date, "0.99", false, null),
                                price(5, product, "DE", "EUR",
                                        date, "0.99", true, null),
                                price(6, product, "DE", "EUR",
                                        future, "0.99", false, null),
                                price(7, product, "DE", "EUR",
                                        date, "0.99", false, "KILOGRAM")
                        ),
                        MediaType.APPLICATION_JSON
                ));

        var result = service.search("Mehl", Unit.GRAM, 1);

        assertEquals(
                List.of(2L),
                result.offers().stream()
                        .map(offer -> offer.priceId())
                        .toList()
        );

        assertEquals(
                0,
                new BigDecimal("1000").compareTo(
                        result.offers().getFirst().packageQuantity()
                )
        );
    }

    @Test
    void normalizesLitresWithoutConvertingVolumeToMass() {
        String product = product(1, "0.5", "l");
        expectProducts(product);

        server.expect(queryParam("product_id__in", "1"))
                .andRespond(withSuccess(
                        page(1, price(
                                1, product, "DE", "EUR",
                                date, "0.89", false, null
                        )),
                        MediaType.APPLICATION_JSON
                ));

        var result = service.search("Milch", Unit.LITER, 1);

        assertEquals(
                Unit.MILLILITER,
                result.offers().getFirst().packageUnit()
        );

        assertEquals(
                0,
                new BigDecimal("500").compareTo(
                        result.offers().getFirst().packageQuantity()
                )
        );
    }

    @Test
    void skipsUnknownPackageUnitsAndDoesNotGuessSpoonConversions() {
        expectProducts(product(1, "1", "unknown"));

        assertTrue(
                service.search("Mehl", Unit.GRAM, 1).offers().isEmpty()
        );

        assertTrue(
                service.search("Salz", Unit.TEASPOON, 1).offers().isEmpty()
        );
    }

    @Test
    void rejectsInvalidQueriesWithoutCallingTheProvider() {
        assertEquals(
                HttpStatus.BAD_REQUEST,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.search("x", Unit.GRAM, 1)
                ).getStatusCode()
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.search("Mehl", Unit.GRAM, 0)
        );

        assertThrows(
                ResponseStatusException.class,
                () -> service.search("Mehl", Unit.GRAM, 101)
        );
    }

    @Test
    void returnsAnErrorForProviderFailureOrMalformedResponses() {
        server.expect(queryParam("product_name__like", "Mehl"))
                .andRespond(withServerError());

        server.expect(queryParam("product_name__like", "Milch"))
                .andRespond(withSuccess(
                        "{}",
                        MediaType.APPLICATION_JSON
                ));

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.search("Mehl", Unit.GRAM, 1)
                ).getStatusCode()
        );

        assertEquals(
                HttpStatus.BAD_GATEWAY,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.search("Milch", Unit.MILLILITER, 1)
                ).getStatusCode()
        );
    }

    @Test
    void exposesTheEndpointAndSerializesThePriceInCents()
            throws Exception {
        String product = product(1, "1000", "g");
        expectProducts(product);

        server.expect(queryParam("product_id__in", "1"))
                .andRespond(withSuccess(
                        page(1, price(
                                1, product, "DE", "EUR",
                                date, "1.29", false, null
                        )),
                        MediaType.APPLICATION_JSON
                ));

        var mvc = MockMvcBuilders
                .standaloneSetup(new PriceController(service))
                .build();

        mvc.perform(get("/api/prices")
                        .param("query", "Mehl")
                        .param("unit", "GRAM"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.offers[0].priceCents").value(129)
                )
                .andExpect(
                        jsonPath("$.offers[0].packageUnit").value("GRAM")
                );

        mvc.perform(get("/api/prices")
                        .param("query", "Mehl")
                        .param("unit", "invalid"))
                .andExpect(status().isBadRequest());
    }

    private void expectProducts(String product) {
        server.expect(queryParam("price_count__gte", "1"))
                .andRespond(withSuccess(
                        page(1, product),
                        MediaType.APPLICATION_JSON
                ));
    }

    private String product(long id, String quantity, String unit) {
        return """
                {"id":%d,"source":"off","product_name":"Mehl","brands":"Testmarke",
                 "product_quantity":%s,"product_quantity_unit":"%s","unused_field":true}
                """.formatted(id, quantity, unit);
    }

    private String price(
            long id,
            String product,
            String country,
            String currency,
            String date,
            String amount,
            boolean discounted,
            String pricePer
    ) {
        return """
                {"id":%d,"type":"PRODUCT","product":%s,
                 "location":{"id":12,"osm_name":"Testmarkt","osm_address_city":"Berlin","osm_address_country_code":"%s"},
                 "price":%s,"currency":"%s","date":"%s","price_is_discounted":%s,"price_per":%s}
                """.formatted(
                id, product, country, amount, currency, date, discounted,
                pricePer == null ? "null" : "\"" + pricePer + "\""
        );
    }

    private String page(int pages, String... items) {
        return "{\"pages\":" + pages
                + ",\"items\":[" + String.join(",", items) + "]}";
    }
}