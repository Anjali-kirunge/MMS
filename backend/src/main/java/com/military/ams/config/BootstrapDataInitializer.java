package com.military.ams.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.entity.AppUser;
import com.military.ams.entity.Role;
import com.military.ams.entity.RoleName;
import com.military.ams.repository.RoleRepository;
import com.military.ams.repository.UserRepository;

/**
 * Safety net that guarantees the role rows and a usable ADMIN account exist.
 * The SQL dump already seeds everything; this only fills gaps so the API can
 * never be deployed against a database it cannot authenticate against.
 */
@Component
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class BootstrapDataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public BootstrapDataInitializer(RoleRepository roleRepository,
                                    UserRepository passwordHolder,
                                    PasswordEncoder passwordEncoder,
                                    @Value("${app.bootstrap.admin-username}") String adminUsername,
                                    @Value("${app.bootstrap.admin-password}") String adminPassword) {
        this.roleRepository = roleRepository;
        this.userRepository = passwordHolder;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<RoleName> roleNames = List.of(RoleName.values());
        for (RoleName roleName : roleNames) {
            if (roleRepository.findByName(roleName.name()).isEmpty()) {
                roleRepository.save(new Role(roleName.name(), roleName.name().replace('_', ' ')));
            }
        }

        if (userRepository.findByUsername(adminUsername).isPresent()) {
            return;
        }
        Role adminRole = roleRepository.findByName(RoleName.ADMIN.name()).orElse(null);
        if (adminRole == null) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(adminUsername);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setFullName("System Administrator");
        admin.setRole(adminRole);
        admin.setEnabled(true);
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);
    }
}
