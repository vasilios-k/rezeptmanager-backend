package de.htwberlin.rezeptmanager_backend.model;

public class Ingredient {

    private Long id;
    private String name;

    public Ingredient(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}