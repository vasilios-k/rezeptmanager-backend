package de.htwberlin.rezeptmanager_backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {

    public String subject() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || authentication.getName().length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Bitte zuerst anmelden."
            );
        }

        return authentication.getName();
    }
}