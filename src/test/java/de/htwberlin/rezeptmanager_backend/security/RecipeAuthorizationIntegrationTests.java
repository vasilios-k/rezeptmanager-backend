package de.htwberlin.rezeptmanager_backend.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.htwberlin.rezeptmanager_backend.config.LegacyRecipeOwnershipMigration;
import de.htwberlin.rezeptmanager_backend.model.Recipe;
import de.htwberlin.rezeptmanager_backend.repository.IngredientRepository;
import de.htwberlin.rezeptmanager_backend.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
@Import(RecipeAuthorizationIntegrationTests.LocalJwtConfiguration.class)
class RecipeAuthorizationIntegrationTests {

    private static final String ISSUER = "https://issuer.example.test/";
    private static final String AUDIENCE = "https://rezeptmanager.example.test";
    private static final String ALICE = "auth0|alice";
    private static final String BOB = "auth0|bob";
    private static final RSAKey SIGNING_KEY = key();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Test
    void healthRemainsPublic() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void requiresAuthenticationForEveryRecipeOperation() throws Exception {
        mvc.perform(get("/api/recipes"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/recipes/1"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Neues Rezept")))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/recipes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Geändertes Rezept")))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/recipes/1"))
                .andExpect(status().isUnauthorized());

        assertEquals(0, recipeRepository.count());
    }

    @Test
    void rejectsInvalidSignaturesAndInvalidTokenClaims() throws Exception {
        Instant future = Instant.now().plusSeconds(600);

        List<String> invalidTokens = List.of(
                "not-a-jwt",
                token(ALICE, ISSUER, AUDIENCE, future, key()),
                token(ALICE, "https://other.example.test/", AUDIENCE, future, SIGNING_KEY),
                token(ALICE, ISSUER, "https://other-api.example.test", future, SIGNING_KEY),
                token(ALICE, ISSUER, "a-frontend-client-id", future, SIGNING_KEY),
                token(ALICE, ISSUER, AUDIENCE, Instant.now().minusSeconds(600), SIGNING_KEY),
                token(ALICE, ISSUER, AUDIENCE, null, SIGNING_KEY),
                token(null, ISSUER, AUDIENCE, future, SIGNING_KEY),
                token(" ", ISSUER, AUDIENCE, future, SIGNING_KEY)
        );

        for (String value : invalidTokens) {
            mvc.perform(get("/api/recipes")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + value))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void assignsTheAuthenticatedUserWhenCreatingARecipe() throws Exception {
        long id = createRecipe(ALICE, "Alices Rezept");

        assertEquals(
                ALICE,
                recipeRepository.findById(id).orElseThrow().getOwnerSubject()
        );

        mvc.perform(get("/api/recipes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Alices Rezept"))
                .andExpect(jsonPath("$.ownerSubject").doesNotExist());
    }

    @Test
    void listsOnlyTheRecipesOfTheAuthenticatedUser() throws Exception {
        long aliceId = createRecipe(ALICE, "Alices Rezept");
        long bobId = createRecipe(BOB, "Bobs Rezept");

        mvc.perform(get("/api/recipes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(aliceId));

        mvc.perform(get("/api/recipes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(BOB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bobId));
    }

    @Test
    void cannotReadAnotherUsersRecipeByGuessingItsId() throws Exception {
        long id = createRecipe(ALICE, "Privates Rezept");

        mvc.perform(get("/api/recipes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(BOB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotChangeOrDeleteAnotherUsersRecipe() throws Exception {
        long id = createRecipe(ALICE, "Bleibt unverändert");

        mvc.perform(put("/api/recipes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(BOB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Fremde Änderung")))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/recipes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(BOB)))
                .andExpect(status().isNotFound());

        Recipe recipe = recipeRepository.findById(id).orElseThrow();

        assertEquals("Bleibt unverändert", recipe.getTitle());
        assertEquals(ALICE, recipe.getOwnerSubject());
        assertEquals(1, recipeRepository.count());
    }

    @Test
    void ownerCanStillUpdateAndDeleteTheirOwnRecipe() throws Exception {
        long id = createRecipe(ALICE, "Original");

        mvc.perform(put("/api/recipes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Überarbeitet")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Überarbeitet"));

        mvc.perform(delete("/api/recipes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE)))
                .andExpect(status().isNoContent());

        assertEquals(0, recipeRepository.count());
    }

    @Test
    void sharedIngredientsRemainAvailableToOtherUsers() throws Exception {
        long aliceId = createRecipe(ALICE, "Alices Mehlrezept");
        long bobId = createRecipe(BOB, "Bobs Mehlrezept");

        assertEquals(1, ingredientRepository.count());

        mvc.perform(delete("/api/recipes/" + aliceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/recipes/" + bobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(BOB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients[0].ingredient.name").value("Mehl"));

        assertEquals(1, ingredientRepository.count());
    }

    @Test
    void unassignedOldRecipesStayPrivateUntilExplicitlyMigrated() throws Exception {
        Recipe legacy = recipeRepository.saveAndFlush(new Recipe(
                "Vorhandenes Rezept",
                "Bisherige Anleitung",
                25,
                2
        ));

        long id = legacy.getId();

        mvc.perform(get("/api/recipes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/recipes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE)))
                .andExpect(status().isNotFound());

        new LegacyRecipeOwnershipMigration(recipeRepository, ALICE).run(null);

        mvc.perform(get("/api/recipes/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Vorhandenes Rezept"));

        assertEquals(id, recipeRepository.findById(id).orElseThrow().getId());
        assertEquals(1, recipeRepository.count());
    }

    @Test
    void migrationNeverTransfersAlreadyAssignedRecipes() throws Exception {
        long assignedId = createRecipe(BOB, "Bobs bestehendes Rezept");

        Recipe legacy = recipeRepository.saveAndFlush(new Recipe(
                "Altes Rezept",
                "Anleitung",
                10,
                2
        ));

        long legacyId = legacy.getId();

        new LegacyRecipeOwnershipMigration(recipeRepository, ALICE).run(null);
        new LegacyRecipeOwnershipMigration(recipeRepository, BOB).run(null);

        assertEquals(
                BOB,
                recipeRepository.findById(assignedId).orElseThrow().getOwnerSubject()
        );

        assertEquals(
                ALICE,
                recipeRepository.findById(legacyId).orElseThrow().getOwnerSubject()
        );

        assertEquals(2, recipeRepository.count());
    }

    @Test
    void migrationWithoutAnExplicitOwnerDoesNotClaimOldRecipes() {
        Recipe legacy = recipeRepository.saveAndFlush(
                new Recipe("Alt", "Anleitung", 10, 2)
        );

        long id = legacy.getId();

        new LegacyRecipeOwnershipMigration(recipeRepository, "").run(null);

        assertNull(
                recipeRepository.findById(id).orElseThrow().getOwnerSubject()
        );

        assertThrows(IllegalArgumentException.class, () ->
                new LegacyRecipeOwnershipMigration(
                        recipeRepository,
                        "name@example.com"
                ).run(null)
        );

        assertNull(
                recipeRepository.findById(id).orElseThrow().getOwnerSubject()
        );
    }

    @Test
    void allowsTheFrontendCorsPreflightWithAnAuthorizationHeader() throws Exception {
        mvc.perform(options("/api/recipes")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization,content-type"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("authorization")
                ));

        mvc.perform(options("/api/recipes")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "authorization"
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void priceSearchAlsoRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/prices")
                        .param("query", "x")
                        .param("unit", "GRAM"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/prices")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ALICE))
                        .param("query", "x")
                        .param("unit", "GRAM"))
                .andExpect(status().isBadRequest());
    }

    private long createRecipe(String owner, String title) throws Exception {
        String location = mvc.perform(post("/api/recipes")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(title)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.LOCATION);

        if (location == null) {
            throw new AssertionError("Location-Header fehlt.");
        }

        return Long.parseLong(
                location.substring(location.lastIndexOf('/') + 1)
        );
    }

    private String request(String title) {
        return """
                {
                  "title": "%s",
                  "ingredients": [
                    {"ingredientName": "Mehl", "quantity": 200, "unit": "GRAM"}
                  ],
                  "instructions": "Zutaten vermischen.",
                  "prepTimeMinutes": 10,
                  "servings": 2
                }
                """.formatted(title);
    }

    private String bearer(String subject) throws JOSEException {
        return "Bearer " + token(
                subject,
                ISSUER,
                AUDIENCE,
                Instant.now().plusSeconds(600),
                SIGNING_KEY
        );
    }

    private String token(
            String subject,
            String issuer,
            String audience,
            Instant expiresAt,
            RSAKey key
    ) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(Instant.now().minusSeconds(5)))
                .expirationTime(
                        expiresAt == null ? null : Date.from(expiresAt)
                )
                .build();

        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .build(),
                claims
        );

        signed.sign(new RSASSASigner(key));

        return signed.serialize();
    }

    private static RSAKey key() {
        try {
            return new RSAKeyGenerator(2048).generate();
        } catch (JOSEException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalJwtConfiguration {

        @Bean
        @Primary
        JwtDecoder testJwtDecoder(
                OAuth2TokenValidator<Jwt> accessTokenValidator
        ) throws JOSEException {
            NimbusJwtDecoder decoder = NimbusJwtDecoder
                    .withPublicKey(SIGNING_KEY.toRSAPublicKey())
                    .build();

            decoder.setJwtValidator(accessTokenValidator);

            return decoder;
        }
    }
}