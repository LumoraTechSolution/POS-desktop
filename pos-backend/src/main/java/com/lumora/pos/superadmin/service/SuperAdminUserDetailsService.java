package com.lumora.pos.superadmin.service;

import com.lumora.pos.superadmin.entity.SuperAdminEntity;
import com.lumora.pos.superadmin.repository.SuperAdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Spring Security UserDetailsService for Super Admins.
 *
 * Loaded by the JwtAuthenticationFilter when it encounters a SUPERADMIN token.
 * This service does NOT interact with tenant-scoped data in any way.
 * It purely validates that the super admin account still exists and is active.
 */
@Service
@RequiredArgsConstructor
public class SuperAdminUserDetailsService {

    private final SuperAdminRepository superAdminRepository;

    /**
     * Load super admin by their ID (extracted from JWT subject claim).
     * Used by the JwtAuthenticationFilter for token-based auth.
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(UUID id) {
        SuperAdminEntity superAdmin = superAdminRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Super admin not found with id: " + id));

        if (!superAdmin.isActive()) {
            throw new UsernameNotFoundException("Super admin account is deactivated: " + id);
        }

        return buildUserDetails(superAdmin);
    }

    /**
     * Load super admin by email.
     * Used during the login flow in SuperAdminAuthService.
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserByEmail(String email) {
        SuperAdminEntity superAdmin = superAdminRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Super admin not found with email: " + email));

        if (!superAdmin.isActive()) {
            throw new UsernameNotFoundException("Super admin account is deactivated: " + email);
        }

        return buildUserDetails(superAdmin);
    }

    private UserDetails buildUserDetails(SuperAdminEntity superAdmin) {
        return new User(
                superAdmin.getEmail(),
                superAdmin.getPasswordHash(),
                superAdmin.isActive(),
                true, // accountNonExpired
                true, // credentialsNonExpired
                true, // accountNonLocked
                List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))
        );
    }
}
