package de.htwberlin.rezeptmanager_backend.repository;

import de.htwberlin.rezeptmanager_backend.model.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IngredientRepository
        extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByNormalizedName(String normalizedName);

    @Modifying
    @Query(
            value = """
                    INSERT INTO ingredients (name, normalized_name)
                    VALUES (:name, :normalizedName)
                    ON CONFLICT DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("name") String name,
            @Param("normalizedName") String normalizedName
    );
}