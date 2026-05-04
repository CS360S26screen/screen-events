package com.example.se_proj.rules;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VehicleRegistrationUtilsTest {

    // -------------------------------------------------------------------------
    // validatePlate
    // -------------------------------------------------------------------------

    @Test
    public void validatePlate_rejectsNull() {
        assertFalse(VehicleRegistrationUtils.validatePlate(null).isValid());
    }

    @Test
    public void validatePlate_rejectsEmpty() {
        assertFalse(VehicleRegistrationUtils.validatePlate("").isValid());
        assertFalse(VehicleRegistrationUtils.validatePlate("   ").isValid());
    }

    @Test
    public void validatePlate_rejectsTooLong() {
        String longPlate = "ABCDEFGHIJKLM"; // 13 chars > MAX_PLATE_LENGTH (12)
        assertFalse(VehicleRegistrationUtils.validatePlate(longPlate).isValid());
    }

    @Test
    public void validatePlate_acceptsValidPlate() {
        assertTrue(VehicleRegistrationUtils.validatePlate("ABC-1234").isValid());
        assertTrue(VehicleRegistrationUtils.validatePlate("LEA-123").isValid());
    }

    // -------------------------------------------------------------------------
    // validateModel
    // -------------------------------------------------------------------------

    @Test
    public void validateModel_rejectsEmpty() {
        assertFalse(VehicleRegistrationUtils.validateModel("").isValid());
        assertFalse(VehicleRegistrationUtils.validateModel(null).isValid());
    }

    @Test
    public void validateModel_acceptsValidModel() {
        assertTrue(VehicleRegistrationUtils.validateModel("Honda Civic 2019").isValid());
    }

    // -------------------------------------------------------------------------
    // validateCarRequest
    // -------------------------------------------------------------------------

    @Test
    public void validateCarRequest_failsOnEmptyPlate() {
        VehicleRegistrationUtils.ValidationResult r =
                VehicleRegistrationUtils.validateCarRequest("", "Honda Civic");
        assertFalse(r.isValid());
        assertFalse(r.getMessage().isEmpty());
    }

    @Test
    public void validateCarRequest_failsOnEmptyModel() {
        VehicleRegistrationUtils.ValidationResult r =
                VehicleRegistrationUtils.validateCarRequest("ABC-1234", "");
        assertFalse(r.isValid());
    }

    @Test
    public void validateCarRequest_passesWhenBothValid() {
        VehicleRegistrationUtils.ValidationResult r =
                VehicleRegistrationUtils.validateCarRequest("ABC-1234", "Toyota Corolla 2020");
        assertTrue(r.isValid());
        assertEquals("", r.getMessage());
    }

    // -------------------------------------------------------------------------
    // isAtCarLimit
    // -------------------------------------------------------------------------

    @Test
    public void isAtCarLimit_falseWhenUnderLimit() {
        assertFalse(VehicleRegistrationUtils.isAtCarLimit(0));
        assertFalse(VehicleRegistrationUtils.isAtCarLimit(1));
    }

    @Test
    public void isAtCarLimit_trueAtExactLimit() {
        assertTrue(VehicleRegistrationUtils.isAtCarLimit(2));
    }

    @Test
    public void isAtCarLimit_trueWhenAboveLimit() {
        assertTrue(VehicleRegistrationUtils.isAtCarLimit(3));
    }

    // -------------------------------------------------------------------------
    // hasDuplicatePlate
    // -------------------------------------------------------------------------

    @Test
    public void hasDuplicatePlate_falseForEmptyList() {
        assertFalse(VehicleRegistrationUtils.hasDuplicatePlate(
                Collections.emptyList(), "ABC-1234"));
    }

    @Test
    public void hasDuplicatePlate_falseWhenNotPresent() {
        List<String> existing = Arrays.asList("XYZ-9999", "DEF-5678");
        assertFalse(VehicleRegistrationUtils.hasDuplicatePlate(existing, "ABC-1234"));
    }

    @Test
    public void hasDuplicatePlate_trueWhenExactMatch() {
        List<String> existing = Arrays.asList("ABC-1234", "DEF-5678");
        assertTrue(VehicleRegistrationUtils.hasDuplicatePlate(existing, "ABC-1234"));
    }

    @Test
    public void hasDuplicatePlate_isCaseInsensitive() {
        List<String> existing = Arrays.asList("abc-1234");
        assertTrue(VehicleRegistrationUtils.hasDuplicatePlate(existing, "ABC-1234"));
    }

    @Test
    public void hasDuplicatePlate_handlesNullList() {
        assertFalse(VehicleRegistrationUtils.hasDuplicatePlate(null, "ABC-1234"));
    }

    @Test
    public void hasDuplicatePlate_handlesNullPlate() {
        List<String> existing = Arrays.asList("ABC-1234");
        assertFalse(VehicleRegistrationUtils.hasDuplicatePlate(existing, null));
    }
}
