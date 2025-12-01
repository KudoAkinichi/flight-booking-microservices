package com.flightapp.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;

@SpringBootApplication(scanBasePackages = "com")
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.client")
@EnableReactiveMongoAuditing
public class BookingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}