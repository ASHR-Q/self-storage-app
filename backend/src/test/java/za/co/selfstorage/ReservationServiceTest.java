package za.co.selfstorage;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {

    @Test
    void shouldCreateReservation() {
        ReservationService service = new ReservationService();

        Reservation reservation = service.createReservation(
                "customer-1",
                "A101",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 1)
        );

        assertNotNull(reservation);
        assertEquals("customer-1", reservation.getCustomerId());
        assertEquals("A101", reservation.getUnitId());
        assertEquals(
                Reservation.Status.PENDING,
                reservation.getStatus()
        );
    }
   
@Test
void shouldRejectOverlappingReservations() {
    ReservationService service = new ReservationService();

    service.createReservation(
            "customer-1",
            "A101",
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 12, 1)
    );

    assertThrows(IllegalArgumentException.class, () ->
            service.createReservation(
                    "customer-2",
                    "A101",
                    LocalDate.of(2026, 11, 15),
                    LocalDate.of(2026, 12, 15)
            )
    );
}

@Test
void shouldAllowReservationsForDifferentDates() {
    ReservationService service = new ReservationService();

    service.createReservation(
            "customer-1",
            "A101",
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 12, 1)
    );

    Reservation secondReservation = service.createReservation(
            "customer-2",
            "A101",
            LocalDate.of(2026, 12, 1),
            LocalDate.of(2027, 1, 1)
    );

    assertNotNull(secondReservation);
    assertEquals("customer-2", secondReservation.getCustomerId());
}

@Test
void shouldAllowReservationsForDifferentUnitsOnSameDates() {
    ReservationService service = new ReservationService();

    service.createReservation(
            "customer-1",
            "A101",
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 12, 1)
    );

    Reservation secondReservation = service.createReservation(
            "customer-2",
            "A102",
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 12, 1)
    );

    assertNotNull(secondReservation);
    assertEquals("A102", secondReservation.getUnitId());
}

}
