package za.co.selfstorage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReservationService {

    private final List<Reservation> reservations = new ArrayList<>();
    private final StorageUnitRepository storageUnitRepository;

    public ReservationService(StorageUnitRepository storageUnitRepository) {
    this.storageUnitRepository = storageUnitRepository;
}
    public Reservation createReservation(
            String customerId,
            String unitId,
            LocalDate startDate,
            LocalDate endDate
    ) {
	StorageUnit unit = storageUnitRepository.findById(unitId)
        .orElseThrow(() -> new IllegalArgumentException(
                "Storage unit does not exist: " + unitId
        ));
	if (!unit.isAvailable()) {
    throw new IllegalArgumentException(
            "Storage unit is not available: " + unitId
    );
}
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
        unit.getUnitId(),
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

