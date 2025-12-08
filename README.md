# Flight Booking Microservices

Lightweight Java Spring Boot microservices for a flight booking system. This repository contains multiple modules (Config Server, Eureka discovery, API Gateway, Flight Service, Booking Service) that demonstrate a small production-like microservice architecture with centralized configuration, service discovery, and inter-service communication.

## Table of contents

- [What it does](#what-it-does)
- [Key features](#key-features)
- [Architecture & modules](#architecture--modules)
- [Quick start — local (Maven)](#quick-start--local-maven)
- [Quick start — Docker Compose](#quick-start--docker-compose)
- [Configuration](#configuration)
- [Development notes & helpful commands](#development-notes--helpful-commands)
- [Where to get help](#where-to-get-help)
- [Contributing](#contributing)
- [Maintainers & license](#maintainers--license)

## Microservice Responsibilities

### 1. Flight Service

* Add airline
* Add flight inventory
* Search flights by date, source, and destination
* Provide flight details to booking service
* Maintains available and total seats

### 2. Booking Service

* Create a booking for a selected flight
* Validate flight using OpenFeign call
* Store passenger details
* Maintain booking history and ticket retrieval
* Publish a booking-created event to RabbitMQ
* Handle cancellation (only allowed 24 hours before the journey)

### 3. Eureka Server

* Registers and discovers all running microservices
* Removes the need to hard-code URLs

### 4. Config Server

* Hosts application configuration (e.g., in Git)
* All services fetch config from here

### 5. API Gateway

* Routes `/api/v1.0/flight/**` to Flight Service
* Routes `/api/v1.0/flight/booking/**` to Booking Service
* Helps in centralized routing and potential future cross-cutting concerns

### 6. RabbitMQ

* Booking service publishes a JSON message whenever a booking is successful
* Future services (email, SMS, analytics) can consume these messages

---

## Key features

- Modular microservice design with clear package layout (controller, service, repository, dto, model, exception)
- Centralized configuration via Config Server
- Service discovery with Eureka
- REST APIs for searching flights and creating bookings
- MongoDB for flight persistence (Flight Service)
- RabbitMQ support and messaging hooks in Booking Service (configurable)

## Architecture & modules

- Root POM: `pom.xml` — parent Maven project
- `config-server/` — central configuration server (`src/main/java/com/flightapp/config/ConfigServerApplication.java`)
- `eureka-server/` — service discovery (`src/main/java/com/flightapp/eureka/EurekaServerApplication.java`)
- `api-gateway/` — API Gateway (`src/main/java/com/flightapp/gateway/ApiGatewayApplication.java`)
- `flight-service/` — Flight inventory and search
  - Main: `flight-service/src/main/java/com/flightapp/flight/flightapp/flight/FlightServiceApplication.java`
  - Mongo config: `flight-service/src/main/java/com/config/MongoConfig.java`
- `booking-service/` — Booking management
  - Main: `booking-service/src/main/java/com/flightapp/flightapp/BookingServiceApplication.java`
  - RabbitMQ config: `booking-service/src/main/java/com/config/RabbitMQConfig.java`

Refer to each module's `src/main/resources/application.yml` for ports and configuration.

## Quick start — local (Maven)

1. Build the whole project (skip tests for faster builds):

```powershell
mvn -f pom.xml clean package -DskipTests
```

2. Start supporting infra (if you use local MongoDB/RabbitMQ). If you don't have them, either run them locally or use the Docker Compose below.

3. Start servers in the recommended order:

```powershell
# Run Config Server
cd config-server; mvn spring-boot:run

# In a new terminal: Run Eureka
cd ../eureka-server; mvn spring-boot:run

# Run Flight Service
cd ../flight-service; mvn spring-boot:run

# Run Booking Service
cd ../booking-service; mvn spring-boot:run

# Run API Gateway last
cd ../api-gateway; mvn spring-boot:run
```

Notes:

- Each module can be started individually; check `application.yml` in each module for configured ports and config server URL.
- If services fail to register with Eureka, confirm the Config Server and Eureka URLs in each service's `application.yml`.

## Quick start — Docker Compose

This repository contains a `docker-compose.yml` to run the stack (if you prefer containers). To run the full stack:

```powershell
# from repository root
docker-compose up --build
```

Adjust the compose file or module `application.yml` files if you need custom ports or external DB endpoints.

## Configuration

- Centralized config: `config-server/src/main/resources/application.yml`
- Service config files: `*/src/main/resources/application.yml` for each module
- Flight service MongoDB configuration is in `flight-service/src/main/java/com/config/MongoConfig.java` and the module `application.yml`.

When making local changes to configuration, use the appropriate module's `application.yml` for testing and restart the service.

## Development notes & helpful commands

- Run tests for a single module:

```powershell
mvn -pl flight-service test
mvn -pl booking-service test
```

- Run formatting / static analysis using your preferred IDE settings (project follows standard Spring Boot Java layout).
- Search entrypoints:
  - Booking controller: `booking-service/src/main/java/com/controller/BookingController.java`
  - Flight controller: `flight-service/src/main/java/com/controller/FlightController.java`
