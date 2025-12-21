package com.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFlightRequest {

    @NotBlank
    private String flightNumber;

    @NotBlank
    private String airlineCode;

    @NotBlank
    private String airlineName;

    private String airlineLogoUrl;

    @NotBlank
    private String origin;

    @NotBlank
    private String destination;

    @NotNull
    private LocalDateTime departureDateTime;

    @NotNull
    private LocalDateTime arrivalDateTime;

    @Min(1)
    private int totalSeats;

    @Min(0)
    private double baseFare;

    @NotBlank
    private String currency;

    private String aircraftType;
}
