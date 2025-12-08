package com.flightapp.apigateway.config;

import com.flightapp.apigateway.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway configuration for routing and JWT authentication
 */
@Configuration
public class GatewayConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // ============================================================
                // PUBLIC ENDPOINTS (No Authentication Required)
                // ============================================================

                // Search Flights
                .route("flight-search", r -> r
                        .path("/api/v1.0/flight/search")
                        .and()
                        .method("POST")
                        .uri("lb://flight-service"))

                // Get All Airlines
                .route("flight-airline-all", r -> r
                        .path("/api/v1.0/flight/airline/all")
                        .and()
                        .method("GET")
                        .uri("lb://flight-service"))

                // Get Flight Details by ID
                .route("flight-details", r -> r
                        .path("/api/v1.0/flight/{flightId}")
                        .and()
                        .method("GET")
                        .uri("lb://flight-service"))

                // ============================================================
                // ADMIN ONLY ENDPOINTS (JWT + ROLE_ADMIN Required)
                // ============================================================

                // Add Airline
                .route("flight-airline-add", r -> r
                        .path("/api/v1.0/flight/airline/add")
                        .and()
                        .method("POST")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://flight-service"))

                // Get Airline by ID
                .route("flight-airline-get", r -> r
                        .path("/api/v1.0/flight/airline/{id}")
                        .and()
                        .method("GET")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://flight-service"))

                // Add Flight Inventory
                .route("flight-inventory-add", r -> r
                        .path("/api/v1.0/flight/airline/inventory/add")
                        .and()
                        .method("POST")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://flight-service"))

                // Modify Flight Seats
                .route("flight-seat-operations", r -> r
                        .path("/api/v1.0/flight/{flightId}/seats")
                        .and()
                        .method("PUT")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://flight-service"))

                // ============================================================
                // USER & ADMIN ENDPOINTS (JWT Required)
                // ============================================================

                // Create Booking
                .route("booking-create", r -> r
                        .path("/api/v1.0/flight/booking/{flightId}")
                        .and()
                        .method("POST")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://booking-service"))

                // Get Ticket by PNR
                .route("booking-ticket-get", r -> r
                        .path("/api/v1.0/flight/ticket/{pnr}")
                        .and()
                        .method("GET")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://booking-service"))

                // Get Booking History
                .route("booking-history", r -> r
                        .path("/api/v1.0/flight/booking/history/{emailId}")
                        .and()
                        .method("GET")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://booking-service"))

                // Cancel Booking
                .route("booking-cancel", r -> r
                        .path("/api/v1.0/flight/booking/cancel/{pnr}")
                        .and()
                        .method("DELETE")
                        .filters(f -> f.filter(jwtAuthFilter))
                        .uri("lb://booking-service"))

                .build();
    }
}