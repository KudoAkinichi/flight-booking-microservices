package com.flightapp.apigateway.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthFilter implements GatewayFilter {

    @Autowired
    private JwtUtil jwtUtil;

    private static final List<String> PUBLIC_ENDPOINTS = List.of(
            "/auth/register",
            "/auth/login",
            "/auth/ping",
            "/api/v1.0/flight/search",
            "/api/v1.0/flight/airline/all"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Allow public endpoints
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Check for Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            if (!jwtUtil.validateToken(token)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Extract user info and add to headers
            String email = jwtUtil.extractEmail(token);
            List<String> roles = jwtUtil.extractRoles(token);

            // Check role-based access
            if (!hasAccess(path, roles)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            // Add user info to request headers for downstream services
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Email", email)
                    .header("X-User-Roles", String.join(",", roles))
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_ENDPOINTS.stream().anyMatch(path::startsWith) ||
                path.matches("/api/v1\\.0/flight/\\d+");  // Allow GET flight by ID
    }

    private boolean hasAccess(String path, List<String> roles) {
        // Admin-only endpoints
        if (path.contains("/airline/add") ||
                path.contains("/airline/inventory/add") ||
                path.contains("/airline/") && !path.contains("/all") ||
                path.matches(".*/flight/\\d+/seats.*")) {
            return roles.contains("ROLE_ADMIN");
        }

        // User and Admin can access booking endpoints
        if (path.contains("/booking") || path.contains("/ticket")) {
            return roles.contains("ROLE_USER") || roles.contains("ROLE_ADMIN");
        }

        return true;
    }
}