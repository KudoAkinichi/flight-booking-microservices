package com.flightapp.apigateway.security;

import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthFilter
        extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            String path = exchange.getRequest().getURI().getPath();

            // =======================
            // 1. PUBLIC ROUTES
            // =======================
            if (path.startsWith("/auth")
                    || path.startsWith("/api/v1.0/flight/search")
                    || path.startsWith("/api/v1.0/flight/airline/all")) {
                return chain.filter(exchange);
            }

            // =======================
            // 2. AUTH HEADER CHECK
            // =======================
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String token = authHeader.substring(7);

            Claims claims;
            try {
                claims = jwtUtil.validateToken(token);
            } catch (Exception e) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // =======================
            // 3. ROLE-BASED ACCESS
            // =======================
            String role = claims.get("role", String.class);

            // ADMIN ONLY APIs
            if (path.startsWith("/api/v1.0/flight/admin")
                    || (path.equals("/api/v1.0/flight")
                    && exchange.getRequest().getMethod().name().equals("POST"))) {

                if (!"ADMIN".equals(role)) {
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
            }

            // =======================
            // 4. PASS USER INFO
            // =======================
            exchange = exchange.mutate()
                    .request(
                            exchange.getRequest()
                                    .mutate()
                                    .header("X-User-Email", claims.getSubject())
                                    .header("X-User-Role", role)
                                    .build()
                    )
                    .build();

            return chain.filter(exchange);
        };
    }

    public static class Config {}
}

