package za.co.selfstorage;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReservationServiceTest {

    @Test
    void shouldCreateReservation() {
        ReservationService service = createService();

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
   ReservationService service = createService();

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
    ReservationService service = createService();

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
    ReservationService service = createService();

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

private ReservationService createService() {
    StorageUnitRepository repository = new StorageUnitRepository();

    repository.add(new StorageUnit("A101", "Small", 500.00));
    repository.add(new StorageUnit("A102", "Medium", 700.00));

    return new ReservationService(repository);
}

@Test
void shouldRejectReservationForNonExistentStorageUnit() {
    ReservationService service = createService();

    assertThrows(
            IllegalArgumentException.class,
            () -> service.createReservation(
                    "customer1",
                    "A999",
                    LocalDate.of(2026, 11, 1),
                    LocalDate.of(2026, 12, 1)
            )
    );
}

@Test
void shouldRejectReservationForUnavailableStorageUnit() {
    StorageUnitRepository repository = new StorageUnitRepository();

    StorageUnit unit = new StorageUnit("A101", "Small", 500.00);
    unit.setAvailable(false);
    repository.add(unit);

    ReservationService service = new ReservationService(repository);

    assertThrows(
            IllegalArgumentException.class,
            () -> service.createReservation(
                    "customer1",
                    "A101",
                    LocalDate.of(2026, 11, 1),
                    LocalDate.of(2026, 12, 1)
            )
    );
}

}
