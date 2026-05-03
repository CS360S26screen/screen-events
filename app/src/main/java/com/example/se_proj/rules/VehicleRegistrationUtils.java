package com.example.se_proj.rules;

import java.util.List;

/**
 * Pure stateless helpers for validating car registration requests.
 *
 * <p>All methods are static and have no side-effects, making them fully unit-testable
 * without Android or Firebase dependencies.</p>
 */
public final class VehicleRegistrationUtils {

    /** Maximum number of vehicles (pending + approved) allowed per user. */
    public static final int MAX_VEHICLES_PER_USER = 2;

    /** Maximum allowed license plate length. */
    public static final int MAX_PLATE_LENGTH = 12;

    private VehicleRegistrationUtils() {}

    // -------------------------------------------------------------------------
    // Validation result
    // -------------------------------------------------------------------------

    /** Immutable result returned by validation methods. */
    public static final class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message != null ? message : "";
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }

    // -------------------------------------------------------------------------
    // Field validators
    // -------------------------------------------------------------------------

    /** Returns a failure if {@code plate} is blank or exceeds the max length. */
    public static ValidationResult validatePlate(String plate) {
        if (plate == null || plate.trim().isEmpty()) {
            return new ValidationResult(false, "License plate cannot be empty");
        }
        if (plate.trim().length() > MAX_PLATE_LENGTH) {
            return new ValidationResult(false, "License plate is too long (max "
                    + MAX_PLATE_LENGTH + " characters)");
        }
        return new ValidationResult(true, "");
    }

    /** Returns a failure if {@code model} is blank. */
    public static ValidationResult validateModel(String model) {
        if (model == null || model.trim().isEmpty()) {
            return new ValidationResult(false, "Car model cannot be empty");
        }
        return new ValidationResult(true, "");
    }

    /**
     * Validates both plate and model together.
     * Returns the first failing result, or a passing result if both are valid.
     */
    public static ValidationResult validateCarRequest(String plate, String model) {
        ValidationResult plateResult = validatePlate(plate);
        if (!plateResult.isValid()) return plateResult;
        return validateModel(model);
    }

    // -------------------------------------------------------------------------
    // Business rules
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the user already has {@code MAX_VEHICLES_PER_USER}
     * or more vehicles (counting both approved and pending).
     */
    public static boolean isAtCarLimit(int existingCount) {
        return existingCount >= MAX_VEHICLES_PER_USER;
    }

    /**
     * Returns {@code true} if {@code plate} (case-insensitive) already appears in
     * {@code existingPlates}.
     */
    public static boolean hasDuplicatePlate(List<String> existingPlates, String plate) {
        if (existingPlates == null || plate == null) return false;
        String normalized = plate.trim().toUpperCase();
        for (String p : existingPlates) {
            if (normalized.equals(p != null ? p.trim().toUpperCase() : "")) {
                return true;
            }
        }
        return false;
    }
}
