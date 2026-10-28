package za.co.selfstorage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReservationService {

    private final List<Reservation> reservations = new ArrayList<>();

    public Reservation createReservation(
            String customerId,
            String unitId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        for (Reservation existing : reservations) {
            boolean sameUnit = existing.getUnitId().equals(unitId);

            boolean datesOverlap =
                    startDate.isBefore(existing.getEndDate())
                    && endDate.isAfter(existing.getStartDate());

            boolean isActive =
                    existing.getStatus() != Reservation.Status.CANCELLED;

            if (sameUnit && datesOverlap && isActive) {
                throw new IllegalArgumentException(
                        "Storage unit is already reserved for these dates"
                );
            }
        }

        Reservation reservation = new Reservation(
                customerId,
                unitId,
                startDate,
                endDate
        );

        reservations.add(reservation);

        return reservation;
    }

    public List<Reservation> getReservations() {
        return List.copyOf(reservations);
    }
}
