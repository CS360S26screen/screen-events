package com.example.se_proj.rules;

import java.util.List;

/**
 * Pure stateless helpers for validating car registration requests.
 *
 * <p>All methods are static and have no side-effects, making them fully unit-testable
 * without Android or Firebase dependencies.</p>
 *
 * <p><b>Design pattern:</b> Rules Engine / utility class. Activities reuse these validation
 * and quota rules instead of duplicating vehicle registration checks in UI controllers.</p>
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

    /**
     * Immutable result returned by validation methods.
     *
     * <p><b>Design pattern:</b> Value Object. The object carries a validation outcome and
     * user-facing message without exposing mutable state.</p>
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final String message;

        /**
         * Creates a validation result.
         *
         * @param valid {@code true} when validation succeeded.
         * @param message user-facing validation message, or {@code null} for an empty message.
         */
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message != null ? message : "";
        }

        /**
         * Indicates whether validation succeeded.
         *
         * @return {@code true} when the checked value passed validation.
         */
        public boolean isValid() { return valid; }

        /**
         * Returns the validation message.
         *
         * @return the user-facing validation message, or an empty string when validation passed.
         */
        public String getMessage() { return message; }
    }

    // -------------------------------------------------------------------------
    // Field validators
    // -------------------------------------------------------------------------

    /**
     * Validates a license plate field.
     *
     * @param plate raw license plate text entered by the user.
     * @return a validation result describing success or the first plate-specific failure.
     */
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

    /**
     * Validates a car model field.
     *
     * @param model raw car model text entered by the user.
     * @return a validation result describing success or the first model-specific failure.
     */
    public static ValidationResult validateModel(String model) {
        if (model == null || model.trim().isEmpty()) {
            return new ValidationResult(false, "Car model cannot be empty");
        }
        return new ValidationResult(true, "");
    }

    /**
     * Validates both plate and model together.
     * Returns the first failing result, or a passing result if both are valid.
     *
     * @param plate raw license plate text entered by the user.
     * @param model raw car model text entered by the user.
     * @return a validation result describing success or the first failing field.
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
     *
     * @param existingCount number of pending plus approved vehicles for the user.
     * @return {@code true} when the user has reached the configured vehicle limit.
     */
    public static boolean isAtCarLimit(int existingCount) {
        return existingCount >= MAX_VEHICLES_PER_USER;
    }

    /**
     * Returns {@code true} if {@code plate} (case-insensitive) already appears in
     * {@code existingPlates}.
     *
     * @param existingPlates known plates already registered or pending in the system.
     * @param plate candidate plate to check.
     * @return {@code true} when the candidate plate duplicates an existing plate.
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
