# Transfer Service
## Overview

The Transfer Service handles account-to-account transfers by coordinating with the Ledger Service. It ensures atomic transfers, idempotency, and concurrent-safe operations, while providing structured logging and fault-tolerant calls to downstream services.

## Features

- Atomic transfers (balance update + ledger entries via Ledger Service)

- Idempotency enforcement using idempotencyKey

- Batch transfer support with concurrent-safe processing

- Circuit breaker protection for the Ledger Service

- Structured JSON logging for operations and failures

- Swagger/OpenAPI available in dev profile only

## API Documentation

The service provides OpenAPI/Swagger documentation, but only when running with the dev Spring profile:

- Swagger UI: http://localhost:8080/swagger-ui.html

- OpenAPI JSON/YAML: http://localhost:8080/v3/api-docs

Use this documentation to explore all available endpoints, request/response formats, and example payloads.
When running in prod or other profiles, Swagger UI and API docs are disabled for security reasons.
To access Swagger locally, start the application with the dev profile.

## Startup Instructions
### Prerequisites

Java 21

## Running Locally (H2)

````
    git clone https://github.com/Ntobe/transfer.git
    cd transfer
    ./gradlew clean build
    ./gradlew bootRun --args='--spring.profiles.active=dev' 
````

## Testing

- Unit and integration tests use H2 in-memory database by default.

- Circuit breaker, concurrency, and idempotency tests are included.

````
    ./gradlew test
````

## Quick Docker Startup (H2 In-Memory, Dev Profile)

You can run the Transfer Server locally with Docker without any external database, using H2 in-memory:

1. Build the Docker Image

From the project root, run:

````
    docker build -t transfer-server .
````

2. Run the Transfer Server Container

````
   docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev transfer-server
````
- -p 8080:8080 exposes the application on localhost:8080

- SPRING_PROFILES_ACTIVE=dev enables the Swagger UI

3. Access the Application
- Swagger UI: http://localhost:8080/swagger-ui.html

- OpenAPI JSON/YAML: http://localhost:8080/v3/api-docs

  All data is stored in-memory (H2) and will be lost when the container stops.

4. Stop the Container

````
    docker stop <container_id_or_name>
````