package com.military.ams.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.military.ams.exception.ApiException;

@Component
public class CurrentUserService {

    /**
     * Returns the authenticated application user, or fails with 401 when the
     * request is anonymous.
     */
    public AppUserPrincipal requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required. Please sign in.");
        }
        return principal;
    }
}
