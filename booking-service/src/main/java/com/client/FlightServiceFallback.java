package com.client;

import com.dto.response.ApiResponse;
import com.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class FlightServiceFallback implements FlightServiceClient {

    @Override
    public Mono<ApiResponse<FlightDetailsDto>> getFlightById(String flightId) {
        log.error("Circuit Breaker: Flight Service unavailable for getFlightById({})", flightId);
        return Mono.error(new ServiceUnavailableException(
                "Flight Service is currently unavailable. Please try again later."
        ));
    }

    @Override
    public Mono<ApiResponse<List<SeatDto>>> getSeats(String flightId) {
        log.error("Circuit Breaker: Flight Service unavailable for getSeats({})", flightId);
        return Mono.error(new ServiceUnavailableException(
                "Flight Service is currently unavailable. Seat information cannot be retrieved."
        ));
    }

    @Override
    public Mono<ApiResponse<Void>> reserveSeats(String flightId, List<String> seatNumbers) {
        log.error("Circuit Breaker: Flight Service unavailable for reserveSeats({}, {})",
                flightId, seatNumbers);
        return Mono.error(new ServiceUnavailableException(
                "Flight Service is currently unavailable. Seat reservation failed. Please try again later."
        ));
    }

    @Override
    public Mono<ApiResponse<Void>> releaseSeats(String flightId, List<String> seatNumbers) {
        log.error("Circuit Breaker: Flight Service unavailable for releaseSeats({}, {})",
                flightId, seatNumbers);
        return Mono.error(new ServiceUnavailableException(
                "Flight Service is currently unavailable. Seat release failed."
        ));
    }
}