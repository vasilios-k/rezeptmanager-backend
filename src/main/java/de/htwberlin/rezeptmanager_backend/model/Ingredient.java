package de.htwberlin.rezeptmanager_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "ingredients",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ingredients_normalized_name",
                columnNames = "normalized_name"
        )
)
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    protected Ingredient() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}