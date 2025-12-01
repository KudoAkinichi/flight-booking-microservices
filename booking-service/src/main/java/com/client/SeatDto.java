package com.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDto {
    private String seatNumber;
    private String seatClass;
    private Boolean isAvailable;
    private String seatType;
    private Double extraCharge;
}