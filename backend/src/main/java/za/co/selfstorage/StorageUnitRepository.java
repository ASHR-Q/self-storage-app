
package za.co.selfstorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StorageUnitRepository {

    private final List<StorageUnit> storageUnits = new ArrayList<>();

    public void add(StorageUnit unit) {
        boolean alreadyExists = storageUnits.stream()
                .anyMatch(existing ->
                        existing.getUnitId().equals(unit.getUnitId()));

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "Storage unit already exists: " + unit.getUnitId()
            );
        }

        storageUnits.add(unit);
    }

    public Optional<StorageUnit> findById(String unitId) {
        return storageUnits.stream()
                .filter(unit -> unit.getUnitId().equals(unitId))
                .findFirst();
    }

    public List<StorageUnit> findAll() {
        return List.copyOf(storageUnits);
    }
}

