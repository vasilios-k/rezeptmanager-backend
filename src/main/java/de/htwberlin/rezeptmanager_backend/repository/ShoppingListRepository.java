package de.htwberlin.rezeptmanager_backend.repository;

import de.htwberlin.rezeptmanager_backend.model.SavedShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingListRepository extends JpaRepository<SavedShoppingList, String> {
}
