package com.military.ams.security;

import org.springframework.stereotype.Component;

import com.military.ams.exception.ForbiddenOperationException;

/**
 * Central place where base-level data scoping is decided.
 * <ul>
 *   <li>ADMIN - may target any base (or all bases when null is passed)</li>
 *   <li>BASE_COMMANDER - always locked to the base on their account; requesting
 *       a different base is rejected with 403</li>
 *   <li>LOGISTICS_OFFICER - may target any base (they move stock between bases)</li>
 * </ul>
 */
@Component
public class AccessScopeService {

    private final CurrentUserService currentUserService;

    public AccessScopeService(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    public Long resolveBaseId(Long requestedBaseId) {
        AppUserPrincipal principal = currentUserService.requireCurrentUser();
        if (principal.isBaseCommander()) {
            if (principal.getBaseId() == null) {
                throw new ForbiddenOperationException("No base is assigned to your account. Contact an administrator.");
            }
            if (requestedBaseId != null && !requestedBaseId.equals(principal.getBaseId())) {
                throw new ForbiddenOperationException("You can only access your own base.");
            }
            return principal.getBaseId();
        }
        return requestedBaseId;
    }

    /** Effective base for the caller, used when the endpoint has no base filter. */
    public Long effectiveBaseId() {
        return resolveBaseId(null);
    }

    public AppUserPrincipal currentUser() {
        return currentUserService.requireCurrentUser();
    }
}
