package com.lumora.pos.config;

import com.lumora.pos.auth.entity.RoleEntity;
import com.lumora.pos.auth.entity.UserEntity;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenProvider Unit Tests")
class JwtTokenProviderTest {

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    private UUID userId;
    private UUID tenantId;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        // Stub the mock — ReflectionTestUtils.setField does not work on Mockito mocks
        String secret = "abcdefghijklmnopqrstuvwxyz1234567890";
        when(jwtProperties.getSecret()).thenReturn(secret);
        when(jwtProperties.getExpirationMs()).thenReturn(3600000L); // 1 hour

        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        userDetails = new User("test@example.com", "dummy", Collections.emptyList());
    }

    @Test
    @DisplayName("Should generate a valid access token with tenant claim")
    void shouldGenerateAccessTokenWithClaims() {
        String token = jwtTokenProvider.generateAccessToken(userDetails, userId, tenantId);

        assertThat(token).isNotBlank();
        
        UUID extractedUserId = jwtTokenProvider.getUserIdFromToken(token);
        assertThat(extractedUserId).isEqualTo(userId);

        UUID extractedTenantId = jwtTokenProvider.getTenantIdFromToken(token);
        assertThat(extractedTenantId).isEqualTo(tenantId);
    }

    @Test
    @DisplayName("Should validate tokens correctly")
    void shouldValidateToken() {
        String token = jwtTokenProvider.generateAccessToken(userDetails, userId, tenantId);
        
        boolean isValid = jwtTokenProvider.validateToken(token);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should extract authorities matching userDetails")
    void shouldExtractAuthorities() {
        String token = jwtTokenProvider.generateAccessToken(userDetails, userId, tenantId);
        
        java.util.List<String> authorities = jwtTokenProvider.getAuthoritiesFromToken(token);
        assertThat(authorities).isEmpty();
    }
}
