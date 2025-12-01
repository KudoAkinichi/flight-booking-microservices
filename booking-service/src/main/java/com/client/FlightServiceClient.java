package com.client;

import com.dto.response.ApiResponse;
import org.springframework.web.bind.annotation.*;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

import java.util.List;

@ReactiveFeignClient(name = "flight-service", fallback = FlightServiceFallback.class)
public interface FlightServiceClient {

    @GetMapping("/api/v1/flights/{flightId}")
    Mono<ApiResponse<FlightDetailsDto>> getFlightById(@PathVariable String flightId);

    @GetMapping("/api/v1/flights/{flightId}/seats")
    Mono<ApiResponse<List<SeatDto>>> getSeats(@PathVariable String flightId);

    @PutMapping("/api/v1/flights/{flightId}/seats/reserve")
    Mono<ApiResponse<Void>> reserveSeats(
            @PathVariable String flightId,
            @RequestBody List<String> seatNumbers);

    @PutMapping("/api/v1/flights/{flightId}/seats/release")
    Mono<ApiResponse<Void>> releaseSeats(
            @PathVariable String flightId,
            @RequestBody List<String> seatNumbers);
}