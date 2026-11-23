package de.htwberlin.rezeptmanager_backend.controller;

import de.htwberlin.rezeptmanager_backend.dto.ShoppingListDocument;
import de.htwberlin.rezeptmanager_backend.service.ShoppingListService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shopping-list")
public class ShoppingListController {
    private final ShoppingListService service;

    public ShoppingListController(ShoppingListService service) {
        this.service = service;
    }

    @GetMapping
    public ShoppingListDocument get() {
        return service.get();
    }

    @PutMapping
    public ShoppingListDocument save(@Valid @RequestBody ShoppingListDocument request) {
        return service.save(request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<Void> concurrentChange() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
}
