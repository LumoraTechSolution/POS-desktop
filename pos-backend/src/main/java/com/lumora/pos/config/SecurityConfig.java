package com.lumora.pos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.pos.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

/**
 * Spring Security configuration.
 * - Stateless sessions (JWT)
 * - CSRF disabled (API-only, token-based auth)
 * - Public endpoints: /api/v1/auth/**, /actuator/health
 * - All other endpoints require authentication
 * JWT filter will be added in Step 2 (Auth implementation).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Desktop product activation — the device has no user session yet.
                        .requestMatchers("/api/v1/activation/**").permitAll()
                        .requestMatchers("/api/v1/super-admin/auth/**").permitAll()
                        .requestMatchers("/api/v1/super-admin/**").hasRole("SUPERADMIN")
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .access((authentication, ctx) -> {
                                boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
                                return new AuthorizationDecision(!isProd);
                            })
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Prometheus scrape endpoint. Permitted at the app layer; in prod
                        // it MUST be reachable only from the metrics network (docker
                        // compose `monitoring` profile) — never exposed publicly. If
                        // tighter control is needed, switch to a separate management
                        // port via `management.server.port`.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/actuator/**").hasAnyRole("SUPERADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint()))
                .addFilterBefore(rateLimitFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Returns 401 (Unauthorized) for unauthenticated requests so the frontend's
     * axios interceptor can trigger a token refresh. Without this, Spring Security 6
     * defaults to 403 for missing/invalid auth, which the client treats as a
     * permission failure rather than a stale-token failure.
     */
    @Bean
    public AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Object> body = ApiResponse.error("Authentication required");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            response.getWriter().flush();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
