package com.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightDetailsDto {
    private String id;
    private String flightNumber;
    private String airlineCode;
    private String airlineName;
    private String airlineLogoUrl;
    private String origin;
    private String destination;
    private LocalDateTime departureDateTime;
    private LocalDateTime arrivalDateTime;
    private String aircraftType;
    private Integer totalSeats;
    private Integer availableSeats;
    private Double baseFare;
    private String currency;
    private String status;
}