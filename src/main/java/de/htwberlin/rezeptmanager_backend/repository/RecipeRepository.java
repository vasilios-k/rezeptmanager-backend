package de.htwberlin.rezeptmanager_backend.repository;

import de.htwberlin.rezeptmanager_backend.model.Recipe;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository
        extends JpaRepository<Recipe, Long> {

    @EntityGraph(attributePaths = {
            "ingredients",
            "ingredients.ingredient"
    })
    List<Recipe> findAllByOwnerSubjectOrderByIdAsc(String ownerSubject);

    @EntityGraph(attributePaths = {
            "ingredients",
            "ingredients.ingredient"
    })
    Optional<Recipe> findByIdAndOwnerSubject(Long id, String ownerSubject);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Recipe r set r.ownerSubject = :ownerSubject "
            + "where r.ownerSubject is null")
    int assignUnownedRecipes(@Param("ownerSubject") String ownerSubject);

    @Override
    @EntityGraph(attributePaths = {
            "ingredients",
            "ingredients.ingredient"
    })
    Optional<Recipe> findById(Long id);
}