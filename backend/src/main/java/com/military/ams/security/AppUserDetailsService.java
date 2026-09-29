package com.military.ams.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.military.ams.entity.AppUser;
import com.military.ams.entity.RoleName;
import com.military.ams.repository.UserRepository;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
        return toPrincipal(user);
    }

    public static AppUserPrincipal toPrincipal(AppUser user) {
        RoleName role = RoleName.valueOf(user.getRole().getName());
        Long baseId = user.getBase() != null ? user.getBase().getId() : null;
        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getFullName(),
                role,
                baseId,
                Boolean.TRUE.equals(user.getEnabled()));
    }
}
