package com.military.ams.entity;

/**
 * Application roles. Kept as an enum so role handling is compile-time safe on
 * the backend; the database stores the name as a string in the roles table.
 */
public enum RoleName {
    ADMIN,
    BASE_COMMANDER,
    LOGISTICS_OFFICER;

    public String authority() {
        return "ROLE_" + name();
    }
}
