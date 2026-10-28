package za.co.selfstorage;

public class StorageUnit {

    private final String unitId;
    private final String size;
    private final double monthlyPrice;
    private boolean available;

    public StorageUnit(String unitId, String size, double monthlyPrice) {
    if (unitId == null || unitId.isBlank()) {
        throw new IllegalArgumentException("Unit ID cannot be empty");
    }

    if (size == null || size.isBlank()) {
        throw new IllegalArgumentException("Unit size cannot be empty");
    }

    if (monthlyPrice <= 0 || !Double.isFinite(monthlyPrice)) {
        throw new IllegalArgumentException("Monthly price must be greater than zero");
    }

    this.unitId = unitId;
    this.size = size;
    this.monthlyPrice = monthlyPrice;
    this.available = true;
}

    public String getUnitId() {
        return unitId;
    }

    public String getSize() {
        return size;
    }

    public double getMonthlyPrice() {
        return monthlyPrice;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
