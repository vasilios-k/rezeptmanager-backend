package de.htwberlin.rezeptmanager_backend.controller;

import de.htwberlin.rezeptmanager_backend.dto.response.PriceSearchResponse;
import de.htwberlin.rezeptmanager_backend.model.Unit;
import de.htwberlin.rezeptmanager_backend.service.PriceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prices")
public class PriceController {
    private final PriceService priceService;

    public PriceController(PriceService priceService) {
        this.priceService = priceService;
    }

    @GetMapping
    public PriceSearchResponse search(
            @RequestParam("query") String query,
            @RequestParam("unit") Unit unit,
            @RequestParam(value = "page", defaultValue = "1") int page
    ) {
        return priceService.search(query, unit, page);
    }
}