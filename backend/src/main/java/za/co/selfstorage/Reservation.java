package za.co.selfstorage;

import java.time.LocalDate;
import java.util.UUID;

public class Reservation {

    public enum Status {
        PENDING,
        CONFIRMED,
        CANCELLED
    }

    private final String reservationId;
    private final String customerId;
    private final String unitId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private Status status;

    public Reservation(
            String customerId,
            String unitId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be empty");
        }

        if (unitId == null || unitId.isBlank()) {
            throw new IllegalArgumentException("Unit ID cannot be empty");
        }

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Reservation dates are required");
        }

        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException(
                    "End date must be after start date"
            );
        }

        this.reservationId = UUID.randomUUID().toString();
        this.customerId = customerId;
        this.unitId = unitId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = Status.PENDING;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getUnitId() {
        return unitId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Status getStatus() {
        return status;
    }

    public void confirm() {
        if (status != Status.PENDING) {
            throw new IllegalStateException(
                    "Only pending reservations can be confirmed"
            );
        }

        status = Status.CONFIRMED;
    }

    public void cancel() {
        if (status == Status.CANCELLED) {
            throw new IllegalStateException(
                    "Reservation is already cancelled"
            );
        }

        status = Status.CANCELLED;
    }
}
