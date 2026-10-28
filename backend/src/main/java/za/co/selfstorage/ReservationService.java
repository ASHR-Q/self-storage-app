package za.co.selfstorage;

import java.time.LocalDate;

public class ReservationService {

    public Reservation createReservation(
            String customerId,
            String unitId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new Reservation(customerId, unitId, startDate, endDate);
    }
}
