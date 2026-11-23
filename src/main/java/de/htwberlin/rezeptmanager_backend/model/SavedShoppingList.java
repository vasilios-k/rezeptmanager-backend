package de.htwberlin.rezeptmanager_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "shopping_lists")
public class SavedShoppingList {
    @Id
    @Column(name = "owner_subject", length = 255, nullable = false)
    private String ownerSubject;

    @Version
    private Long version;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    protected SavedShoppingList() {
    }

    public SavedShoppingList(String ownerSubject, String payload) {
        this.ownerSubject = ownerSubject;
        this.payload = payload;
    }

    public Long getVersion() {
        return version;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
