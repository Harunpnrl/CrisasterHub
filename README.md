# CrisasterHub

JavaFX disaster-relief coordination application with victim, volunteer and operator workflows.

## Requirements

- JDK 21
- MySQL 8+
- Internet access for Leaflet tiles and reverse geocoding

## Database setup

Run the bootstrap script once before starting the application:

```bash
mysql -u root -p < src/main/resources/db/operator_audit_log.sql
```

The application reads these values with system-property priority and environment fallback:

```text
DB_URL=jdbc:mysql://localhost:3306/crisis_db
DB_USERNAME=crisis_app
DB_PASSWORD=change-this-password
VOLUNTEER_RADIUS_KM=50
```

Java does not load `.env` files automatically. Configure these as real environment variables or pass them as JVM properties, for example:

```bash
mvnw.cmd clean javafx:run -DDB_URL=jdbc:mysql://localhost:3306/crisis_db -DDB_USERNAME=crisis_app -DDB_PASSWORD=secret
```

## Build

```bash
mvnw.cmd clean verify
```

## Workflow

1. A victim submits a geolocated pending request.
2. A volunteer registers a geolocated resource.
3. Nearby volunteers see compatible pending requests and claim one.
4. Claim creates an assignment and changes the request to `In Progress`.
5. Delivery changes the assignment to `Delivered` and the request to `Fulfilled`.
6. Operators can inspect and manage requests, resources and assignments.
