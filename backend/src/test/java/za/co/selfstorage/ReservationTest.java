package za.co.selfstorage;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {

    @Test
    void newReservationShouldBePending() {
        Reservation reservation = new Reservation(
                "customer-1",
                "A101",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 1)
        );

        assertEquals(Reservation.Status.PENDING, reservation.getStatus());
        assertEquals("customer-1", reservation.getCustomerId());
        assertEquals("A101", reservation.getUnitId());
        assertNotNull(reservation.getReservationId());
    }

    @Test
    void pendingReservationCanBeConfirmed() {
        Reservation reservation = createReservation();

        reservation.confirm();

        assertEquals(Reservation.Status.CONFIRMED, reservation.getStatus());
    }

    @Test
    void reservationCanBeCancelled() {
        Reservation reservation = createReservation();

        reservation.cancel();

        assertEquals(Reservation.Status.CANCELLED, reservation.getStatus());
    }

    @Test
    void endDateMustBeAfterStartDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Reservation(
                        "customer-1",
                        "A101",
                        LocalDate.of(2026, 12, 1),
                        LocalDate.of(2026, 11, 1)
                )
        );
    }

    @Test
    void cancelledReservationCannotBeCancelledAgain() {
        Reservation reservation = createReservation();
        reservation.cancel();

        assertThrows(IllegalStateException.class, reservation::cancel);
    }

    private Reservation createReservation() {
        return new Reservation(
                "customer-1",
                "A101",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 12, 1)
        );
    }
}
