
package za.co.selfstorage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageUnitRepositoryTest {

    @Test
    void shouldAddAndFindStorageUnit() {
        StorageUnitRepository repository = new StorageUnitRepository();
        StorageUnit unit = new StorageUnit("A101", "Small", 500.00);

        repository.add(unit);

        assertTrue(repository.findById("A101").isPresent());
        assertEquals(
                "A101",
                repository.findById("A101").get().getUnitId()
        );
    }

    @Test
    void shouldRejectDuplicateUnitIds() {
        StorageUnitRepository repository = new StorageUnitRepository();

        repository.add(new StorageUnit("A101", "Small", 500.00));

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.add(
                        new StorageUnit("A101", "Large", 900.00)
                )
        );
    }

    @Test
    void shouldReturnEmptyWhenUnitDoesNotExist() {
        StorageUnitRepository repository = new StorageUnitRepository();

        assertTrue(repository.findById("A999").isEmpty());
    }
}

