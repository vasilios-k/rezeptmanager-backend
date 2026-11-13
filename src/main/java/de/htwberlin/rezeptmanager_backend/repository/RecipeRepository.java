package de.htwberlin.rezeptmanager_backend.repository;

import de.htwberlin.rezeptmanager_backend.model.Recipe;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository
        extends JpaRepository<Recipe, Long> {

    @EntityGraph(attributePaths = {
            "ingredients",
            "ingredients.ingredient"
    })
    List<Recipe> findAllByOrderByIdAsc();

    @Override
    @EntityGraph(attributePaths = {
            "ingredients",
            "ingredients.ingredient"
    })
    Optional<Recipe> findById(Long id);
}