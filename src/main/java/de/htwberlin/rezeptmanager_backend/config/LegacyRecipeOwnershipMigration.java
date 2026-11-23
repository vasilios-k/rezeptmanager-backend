package de.htwberlin.rezeptmanager_backend.config;

import de.htwberlin.rezeptmanager_backend.repository.RecipeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LegacyRecipeOwnershipMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(
            LegacyRecipeOwnershipMigration.class
    );

    private final RecipeRepository recipeRepository;
    private final String ownerSubject;

    public LegacyRecipeOwnershipMigration(
            RecipeRepository recipeRepository,
            @Value("${app.auth.legacy-owner-subject:}") String ownerSubject
    ) {
        this.recipeRepository = recipeRepository;
        this.ownerSubject = ownerSubject.strip();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (ownerSubject.isEmpty()) return;

        if (ownerSubject.length() > 255 || !ownerSubject.contains("|")
                || ownerSubject.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "LEGACY_OWNER_SUBJECT muss die Auth0 User ID des Besitzers enthalten."
            );
        }

        int migrated = recipeRepository.assignUnownedRecipes(ownerSubject);

        if (migrated > 0) {
            log.info("{} bestehende Rezepte wurden ihrem Besitzer zugeordnet.", migrated);
        }
    }
}