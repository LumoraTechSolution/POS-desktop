package com.lumora.pos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumora.pos.common.dto.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    // Login attempts allowed per IP within the refill window. Defaults match the
    // production posture (10 / 15 min); the dockerised dev/test stack overrides
    // RATE_LIMIT_LOGIN_CAPACITY to a high value so the e2e suite (which logs in
    // many times per run) isn't throttled.
    @Value("${RATE_LIMIT_LOGIN_CAPACITY:10}")
    private long loginCapacity;

    @Value("${RATE_LIMIT_LOGIN_WINDOW_MINUTES:15}")
    private long windowMinutes;

    private final ObjectMapper objectMapper;
    private Cache<String, Bucket> caches;

    @PostConstruct
    void init() {
        caches = Caffeine.newBuilder()
                .expireAfterWrite(windowMinutes, TimeUnit.MINUTES)
                .maximumSize(100_000)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only rate-limit credential-guessing endpoints. Session-verification
        // endpoints (/auth/me, /auth/refresh, /auth/logout) are called on every
        // page navigation and must not count against the login attempt quota.
        if (isCredentialEndpoint(path)) {
            String ip = getClientIP(request);
            Bucket bucket = caches.get(ip, this::createNewBucket);

            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");

                ApiResponse<?> apiResponse = ApiResponse.error("Too many login attempts from your IP. Please try again after 15 minutes.");
                response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isCredentialEndpoint(String path) {
        return path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/pin-login")
                || path.equals("/api/v1/super-admin/auth/login");
    }

    private Bucket createNewBucket(String key) {
        // Default: 10 requests per 15 minutes per IP (configurable — see fields above).
        Bandwidth limit = Bandwidth.classic(loginCapacity,
                Refill.intervally(loginCapacity, Duration.ofMinutes(windowMinutes)));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIP(HttpServletRequest request) {
        // Always use the direct TCP connection address — X-Forwarded-For is
        // trivially spoofed by clients and must never be trusted for rate limiting
        // unless the app sits behind a known reverse proxy that overwrites (not appends) the header.
        return request.getRemoteAddr();
    }
}
