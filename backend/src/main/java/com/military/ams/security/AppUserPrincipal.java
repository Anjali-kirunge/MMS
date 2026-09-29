package com.military.ams.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.military.ams.entity.RoleName;

/**
 * Authenticated application user. The base id is carried on the principal so
 * that services can enforce base-level scoping without hitting the database.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String fullName;
    private final RoleName role;
    private final Long baseId;
    private final boolean enabled;

    public AppUserPrincipal(Long id, String username, String password, String fullName,
                            RoleName role, Long baseId, boolean enabled) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
        this.baseId = baseId;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public RoleName getRole() {
        return role;
    }

    public Long getBaseId() {
        return baseId;
    }

    public boolean isAdmin() {
        return role == RoleName.ADMIN;
    }

    public boolean isBaseCommander() {
        return role == RoleName.BASE_COMMANDER;
    }

    public boolean isLogisticsOfficer() {
        return role == RoleName.LOGISTICS_OFFICER;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
