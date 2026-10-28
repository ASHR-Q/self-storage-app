package za.co.selfstorage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageUnitTest {

    @Test
    void newStorageUnitShouldBeAvailable() {
        StorageUnit unit = new StorageUnit("A101", "Small", 500.00);

        assertTrue(unit.isAvailable());
        assertEquals("A101", unit.getUnitId());
        assertEquals("Small", unit.getSize());
        assertEquals(500.00, unit.getMonthlyPrice());
    }

    @Test
    void negativePriceShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StorageUnit("A101", "Small", -100.00)
        );
    }

    @Test
    void emptyUnitIdShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StorageUnit("", "Small", 500.00)
        );
    }
}
