package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestValidationUtilsTest {

    @Test
    public void validateScheduledRequest_rejectsVisitWindowsWhereEndTimeIsBeforeStartTime() {
        RequestValidationUtils.ValidationResult result = RequestValidationUtils.validateScheduledRequest(
                "Guest One",
                "3520212345671",
                "Meeting",
                "09/04/2026",
                "14:00",
                "13:00",
                LocalDate.of(2026, 4, 7),
                LocalTime.of(9, 0)
        );

        assertFalse(result.isValid());
        assertEquals("End time must be after start time", result.getMessage());
    }

    @Test
    public void validateScheduledRequest_rejectsPastVisitDates() {
        RequestValidationUtils.ValidationResult result = RequestValidationUtils.validateScheduledRequest(
                "Guest One",
                "3520212345671",
                "Meeting",
                "06/04/2026",
                "10:00",
                "11:00",
                LocalDate.of(2026, 4, 7),
                LocalTime.of(9, 0)
        );

        assertFalse(result.isValid());
        assertEquals("Visit date cannot be in the past", result.getMessage());
    }

    @Test
    public void validateScheduledRequest_rejectsInvalidCnicValues() {
        RequestValidationUtils.ValidationResult result = RequestValidationUtils.validateScheduledRequest(
                "Guest One",
                "12345",
                "Meeting",
                "09/04/2026",
                "10:00",
                "11:00",
                LocalDate.of(2026, 4, 7),
                LocalTime.of(9, 0)
        );

        assertFalse(result.isValid());
        assertEquals("Please enter a valid 13-digit CNIC", result.getMessage());
    }

    @Test
    public void validateScheduledRequest_rejectsTodayWindowsThatAlreadyEnded() {
        RequestValidationUtils.ValidationResult result = RequestValidationUtils.validateScheduledRequest(
                "Guest One",
                "3520212345671",
                "Meeting",
                "07/04/2026",
                "08:00",
                "09:00",
                LocalDate.of(2026, 4, 7),
                LocalTime.of(9, 0)
        );

        assertFalse(result.isValid());
        assertEquals("Visit window has already ended", result.getMessage());
    }

    @Test
    public void validateScheduledRequest_acceptsFutureWindowsWithValidData() {
        RequestValidationUtils.ValidationResult result = RequestValidationUtils.validateScheduledRequest(
                "Guest One",
                "3520212345671",
                "Meeting",
                "09/04/2026",
                "10:00",
                "11:00",
                LocalDate.of(2026, 4, 7),
                LocalTime.of(9, 0)
        );

        assertTrue(result.isValid());
    }

    @Test
    public void matchesDuplicateCandidate_returnsTrueOnlyForMatchingHostDateCnicAndActiveStatus() {
        VisitorRequest duplicate = request(
                "req-1",
                "35202-1234567-1",
                "FAC-1",
                RequestStatus.APPROVED,
                false
        );

        VisitorRequest differentStatus = request(
                "req-2",
                "3520212345671",
                "FAC-1",
                RequestStatus.CANCELLED,
                false
        );

        assertTrue(RequestValidationUtils.matchesDuplicateCandidate(
                duplicate,
                "FAC-1",
                "3520212345671",
                "09/04/2026"
        ));
        assertFalse(RequestValidationUtils.matchesDuplicateCandidate(
                differentStatus,
                "FAC-1",
                "3520212345671",
                "09/04/2026"
        ));
    }

    @Test
    public void hasBlockingStudentPass_detectsActiveOrPendingRequests() {
        VisitorRequest pending = request("req-1", "3520212345671", "23F-001", RequestStatus.PENDING, false);
        VisitorRequest checkedOut = request("req-2", "3520212345672", "23F-001", RequestStatus.CANCELLED, false);

        assertTrue(RequestValidationUtils.hasBlockingStudentPass(Arrays.asList(pending, checkedOut)));
        assertFalse(RequestValidationUtils.hasBlockingStudentPass(Collections.singletonList(checkedOut)));
    }

    @Test
    public void normalizeCnic_removesFormattingCharactersBeforeStorageOrSearch() {
        assertEquals("3520212345671", RequestValidationUtils.normalizeCnic("35202-1234567-1"));
    }

    private static VisitorRequest request(
            String id,
            String cnic,
            String hostId,
            String status,
            boolean onCampus
    ) {
        return new VisitorRequest(
                id,
                "Guest One",
                cnic,
                "Meeting",
                "09/04/2026",
                "10:00",
                "11:00",
                hostId,
                "creator-1",
                "faculty",
                status,
                null,
                null,
                onCampus
        );
    }
}
