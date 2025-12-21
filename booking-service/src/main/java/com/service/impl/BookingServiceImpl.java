package com.service.impl;

import com.client.FlightDetailsDto;
import com.client.FlightServiceClient;
import com.dto.request.BookingRequest;
import com.dto.response.*;
import com.exception.BookingNotFoundException;
import com.exception.ServiceUnavailableException;
import com.model.Booking;
import com.model.Passenger;
import com.repository.BookingRepository;
import com.service.BookingService;
import com.util.Constants;
import com.util.DateTimeUtil;
import com.util.PNRGenerator;
import com.validator.BookingValidator;
import com.validator.CancellationValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final FlightServiceClient flightServiceClient;
    private final BookingValidator bookingValidator;
    private final CancellationValidator cancellationValidator;

    @Override
    public Mono<BookingResponse> createBooking(BookingRequest request) {
        log.info("Creating booking for flight: {}", request.getFlightId());

        return flightServiceClient.getFlightById(request.getFlightId())
                .map(ApiResponse::getData)
                .flatMap(flightDto -> {
                    // Convert DTO to temporary Flight object for validation
                    var tempFlight = convertToFlightForValidation(flightDto);

                    // Validate booking request
                    bookingValidator.validateBookingRequest(request, tempFlight);

                    // Create booking
                    Booking booking = buildBooking(request, flightDto);

                    // Save booking first
                    return bookingRepository.save(booking)
                            .flatMap(savedBooking ->
                                    // Then reserve seats in flight service
                                    flightServiceClient.reserveSeats(
                                                    request.getFlightId(),
                                                    request.getSeatNumbers()
                                            )
                                            .doOnSuccess(v -> {
                                                log.info("Seats reserved successfully for booking: {}",
                                                        savedBooking.getPnr());
                                            })
                                            .publishOn(Schedulers.boundedElastic())
                                            .doOnError(error -> {
                                                log.error("Failed to reserve seats, rolling back booking: {}",
                                                        savedBooking.getPnr());
                                                // Rollback: delete the booking
                                                bookingRepository.deleteById(savedBooking.getId()).subscribe();
                                            })
                                            .thenReturn(savedBooking)
                            )
                            .map(savedBooking -> convertToBookingResponse(savedBooking));
                })
                .onErrorResume(ServiceUnavailableException.class, ex -> {
                    log.error("Circuit breaker activated: {}", ex.getMessage());
                    return Mono.error(ex);
                })
                .doOnSuccess(response ->
                        log.info("Booking created successfully with PNR: {}", response.getPnr()))
                .doOnError(error ->
                        log.error("Error creating booking: {}", error.getMessage()));
    }

    @Override
    public Mono<TicketResponse> getBookingByPnr(String pnr) {
        log.info("Fetching booking with PNR: {}", pnr);

        return bookingRepository.findByPnr(pnr.toUpperCase())
                .switchIfEmpty(Mono.error(new BookingNotFoundException(pnr)))
                .flatMap(booking ->
                        flightServiceClient.getFlightById(booking.getFlightId())
                                .map(ApiResponse::getData)
                                .map(flightDto -> convertToTicketResponse(booking, flightDto))
                                .onErrorResume(ServiceUnavailableException.class, ex -> {
                                    // If flight service is down, still return booking info
                                    log.warn("Flight service unavailable, returning booking without flight details");
                                    return Mono.just(convertToTicketResponseWithoutFlight(booking));
                                })
                );
    }

    @Override
    public Flux<BookingResponse> getBookingHistory(String email) {
        log.info("Fetching booking history for email: {}", email);

        return bookingRepository.findByContactEmailOrderByBookingDateTimeDesc(email.toLowerCase())
                .flatMap(booking ->
                        flightServiceClient.getFlightById(booking.getFlightId())
                                .map(apiResponse -> apiResponse.getData())
                                .map(flightDto -> convertToBookingResponse(booking))
                                .onErrorResume(error -> {
                                    log.warn("Could not fetch flight details for booking {}, using cached data",
                                            booking.getPnr());
                                    return Mono.just(convertToBookingResponseWithoutFlightDetails(booking));
                                })
                )
                .switchIfEmpty(Flux.empty());
    }

    @Override
    public Mono<CancellationResponse> cancelBooking(String pnr) {
        log.info("Cancelling booking with PNR: {}", pnr);

        return bookingRepository.findByPnr(pnr.toUpperCase())
                .switchIfEmpty(Mono.error(new BookingNotFoundException(pnr)))
                .flatMap(booking -> {
                    // Validate cancellation
                    cancellationValidator.validateCancellation(booking);

                    // Calculate refund
                    double refundAmount = cancellationValidator.calculateRefundAmount(booking);

                    // Update booking status
                    booking.setStatus(Constants.STATUS_CANCELLED);
                    booking.setCancellationDateTime(DateTimeUtil.getCurrentTimestamp());
                    booking.setCancellationReason("Cancelled by user");
                    booking.setRefundAmount(refundAmount);

                    // Release seats in flight service
                    return flightServiceClient.releaseSeats(
                                    booking.getFlightId(),
                                    booking.getSeatNumbers()
                            )
                            .doOnSuccess(v ->
                                    log.info("Seats released successfully for PNR: {}", pnr))
                            .doOnError(error ->
                                    log.error("Failed to release seats for PNR: {}", pnr))
                            .then(bookingRepository.save(booking))
                            .map(cancelledBooking -> buildCancellationResponse(cancelledBooking, refundAmount));
                })
                .doOnError(error ->
                        log.error("Error cancelling booking: {}", error.getMessage()));
    }

    /**
     * Build Booking entity from request
     */
    private Booking buildBooking(BookingRequest request, FlightDetailsDto flightDto) {
        String pnr = generateUniquePNR();

        List<Passenger> passengers = request.getPassengers().stream()
                .map(passengerDto -> Passenger.builder()
                        .name(passengerDto.getName())
                        .gender(passengerDto.getGender())
                        .age(passengerDto.getAge())
                        .seatNumber(passengerDto.getSeatNumber())
                        .mealPreference(passengerDto.getMealPreference())
                        .build())
                .collect(Collectors.toList());

        double totalFare = calculateTotalFare(flightDto, request.getSeatNumbers());

        return Booking.builder()
                .pnr(pnr)
                .flightId(flightDto.getId())
                .flightNumber(flightDto.getFlightNumber())
                .route(flightDto.getOrigin() + "-" + flightDto.getDestination())
                .contactEmail(request.getContactEmail().toLowerCase())
                .contactName(request.getContactName())
                .passengers(passengers)
                .seatNumbers(request.getSeatNumbers())
                .totalFare(totalFare)
                .currency(flightDto.getCurrency())
                .status(Constants.STATUS_CONFIRMED)
                .journeyDate(flightDto.getDepartureDateTime())
                .bookingDateTime(DateTimeUtil.getCurrentTimestamp())
                .build();
    }

    /**
     * Generate unique PNR
     */
    private String generateUniquePNR() {
        String pnr;
        do {
            pnr = PNRGenerator.generatePNR();
        } while (Boolean.TRUE.equals(bookingRepository.existsByPnr(pnr).block()));

        return pnr;
    }

    /**
     * Calculate total fare (simplified - without accessing seat details)
     */
    private double calculateTotalFare(FlightDetailsDto flight, List<String> seatNumbers) {
        // Base fare calculation
        double baseFare = flight.getBaseFare() * seatNumbers.size();

        // Simplified seat charges estimation
        double estimatedSeatCharges = 200 * seatNumbers.size(); // Average charge

        return baseFare + estimatedSeatCharges;
    }

    /**
     * Convert DTO to temporary flight object for validation
     */
    private com.model.Flight convertToFlightForValidation(FlightDetailsDto dto) {
        return com.model.Flight.builder()
                .id(dto.getId())
                .flightNumber(dto.getFlightNumber())
                .availableSeats(dto.getAvailableSeats())
                .baseFare(dto.getBaseFare())
                .seats(List.of()) // Empty list, validator will check availability count
                .build();
    }


    /**
     * Convert to BookingResponse
     */
    private BookingResponse convertToBookingResponse(Booking booking) {
        List<PassengerInfo> passengerInfos = new ArrayList<>();
        for (Passenger p : booking.getPassengers()) {
            PassengerInfo build = PassengerInfo.builder()
                    .name(p.getName())
                    .gender(p.getGender())
                    .age(p.getAge())
                    .seatNumber(p.getSeatNumber())
                    .mealPreference(p.getMealPreference())
                    .build();
            passengerInfos.add(build);
        }

        return BookingResponse.builder()
                .bookingId(booking.getId())
                .pnr(booking.getPnr())
                .status(booking.getStatus())
                .flightNumber(booking.getFlightNumber())
                .route(booking.getRoute())
                .contactName(booking.getContactName())
                .contactEmail(booking.getContactEmail())
                .passengers(passengerInfos)
                .seatNumbers(booking.getSeatNumbers())
                .totalFare(booking.getTotalFare())
                .currency(booking.getCurrency())
                .journeyDate(booking.getJourneyDate())
                .bookingDateTime(booking.getBookingDateTime())
                .message("Booking confirmed successfully")
                .build();
    }

    private BookingResponse convertToBookingResponseWithoutFlightDetails(Booking booking) {
        List<PassengerInfo> passengerInfos = new ArrayList<>();
        for (Passenger p : booking.getPassengers()) {
            PassengerInfo build = PassengerInfo.builder()
                    .name(p.getName())
                    .gender(p.getGender())
                    .age(p.getAge())
                    .seatNumber(p.getSeatNumber())
                    .mealPreference(p.getMealPreference())
                    .build();
            passengerInfos.add(build);
        }

        return BookingResponse.builder()
                .bookingId(booking.getId())
                .pnr(booking.getPnr())
                .status(booking.getStatus())
                .flightNumber(booking.getFlightNumber())
                .route(booking.getRoute())
                .contactName(booking.getContactName())
                .contactEmail(booking.getContactEmail())
                .passengers(passengerInfos)
                .seatNumbers(booking.getSeatNumbers())
                .totalFare(booking.getTotalFare())
                .currency(booking.getCurrency())
                .journeyDate(booking.getJourneyDate())
                .bookingDateTime(booking.getBookingDateTime())
                .message("Booking details (flight service temporarily unavailable)")
                .build();
    }

    /**
     * Convert to TicketResponse
     */
    private TicketResponse convertToTicketResponse(Booking booking, FlightDetailsDto flight) {
        FlightDetails flightDetails = FlightDetails.builder()
                .flightNumber(flight.getFlightNumber())
                .airlineName(flight.getAirlineName())
                .airlineLogoUrl(flight.getAirlineLogoUrl())
                .origin(flight.getOrigin())
                .destination(flight.getDestination())
                .departureDateTime(flight.getDepartureDateTime())
                .arrivalDateTime(flight.getArrivalDateTime())
                .duration(DateTimeUtil.calculateDuration(
                        flight.getDepartureDateTime(),
                        flight.getArrivalDateTime()))
                .aircraftType(flight.getAircraftType())
                .build();

        BookingDetails bookingDetails = BookingDetails.builder()
                .contactName(booking.getContactName())
                .contactEmail(booking.getContactEmail())
                .seatNumbers(booking.getSeatNumbers())
                .bookingDateTime(booking.getBookingDateTime())
                .journeyDate(booking.getJourneyDate())
                .build();

        List<PassengerInfo> passengers = new ArrayList<>();
        for (Passenger p : booking.getPassengers()) {
            PassengerInfo build = PassengerInfo.builder()
                    .name(p.getName())
                    .gender(p.getGender())
                    .age(p.getAge())
                    .seatNumber(p.getSeatNumber())
                    .mealPreference(p.getMealPreference())
                    .build();
            passengers.add(build);
        }

        FareBreakdown fareBreakdown = FareBreakdown.builder()
                .baseFare(flight.getBaseFare() * booking.getPassengers().size())
                .taxes(0.0)
                .seatCharges(booking.getTotalFare() - (flight.getBaseFare() * booking.getPassengers().size()))
                .mealCharges(0.0)
                .totalFare(booking.getTotalFare())
                .currency(booking.getCurrency())
                .build();

        return TicketResponse.builder()
                .pnr(booking.getPnr())
                .bookingId(booking.getId())
                .status(booking.getStatus())
                .flightDetails(flightDetails)
                .bookingDetails(bookingDetails)
                .passengers(passengers)
                .fareBreakdown(fareBreakdown)
                .build();
    }

    private TicketResponse convertToTicketResponseWithoutFlight(Booking booking) {
        BookingDetails bookingDetails = BookingDetails.builder()
                .contactName(booking.getContactName())
                .contactEmail(booking.getContactEmail())
                .seatNumbers(booking.getSeatNumbers())
                .bookingDateTime(booking.getBookingDateTime())
                .journeyDate(booking.getJourneyDate())
                .build();

        List<PassengerInfo> passengers = new ArrayList<>();
        for (Passenger p : booking.getPassengers()) {
            PassengerInfo build = PassengerInfo.builder()
                    .name(p.getName())
                    .gender(p.getGender())
                    .age(p.getAge())
                    .seatNumber(p.getSeatNumber())
                    .mealPreference(p.getMealPreference())
                    .build();
            passengers.add(build);
        }

        FareBreakdown fareBreakdown = FareBreakdown.builder()
                .totalFare(booking.getTotalFare())
                .currency(booking.getCurrency())
                .build();

        return TicketResponse.builder()
                .pnr(booking.getPnr())
                .bookingId(booking.getId())
                .status(booking.getStatus())
                .bookingDetails(bookingDetails)
                .passengers(passengers)
                .fareBreakdown(fareBreakdown)
                .build();
    }

    /**
     * Build CancellationResponse
     */
    private CancellationResponse buildCancellationResponse(Booking booking, double refundAmount) {
        return CancellationResponse.builder()
                .pnr(booking.getPnr())
                .status(Constants.STATUS_CANCELLED)
                .message("Booking cancelled successfully")
                .refundAmount(refundAmount)
                .currency(booking.getCurrency())
                .cancellationDateTime(booking.getCancellationDateTime())
                .cancellationReason(booking.getCancellationReason())
                .build();
    }

    @Override
    public Flux<BookingResponse> getMyBookings(String email) {
        log.info("Fetching bookings for authenticated user: {}", email);

        return bookingRepository
                .findByContactEmailOrderByBookingDateTimeDesc(email.toLowerCase())
                .map(this::convertToBookingResponse)
                .switchIfEmpty(Flux.empty());
    }

}