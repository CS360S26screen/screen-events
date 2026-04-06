package com.example.se_proj.rules;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestStatusTest {

    @Test
    public void normalize_trimsAndLowercasesStatusValues() {
        assertEquals(RequestStatus.APPROVED, RequestStatus.normalize("  ApPrOvEd "));
    }

    @Test
    public void canEdit_returnsTrueOnlyForPendingAndApprovedRequests() {
        assertTrue(RequestStatus.canEdit(RequestStatus.PENDING));
        assertTrue(RequestStatus.canEdit(RequestStatus.APPROVED));
        assertFalse(RequestStatus.canEdit(RequestStatus.REJECTED));
    }

    @Test
    public void canCancel_returnsFalse_whenGuestIsAlreadyOnCampus() {
        assertFalse(RequestStatus.canCancel(RequestStatus.PENDING, true));
        assertTrue(RequestStatus.canCancel(RequestStatus.APPROVED, false));
    }

    @Test
    public void isDuplicateBlockingStatus_coversEveryActiveSubmissionState() {
        assertTrue(RequestStatus.isDuplicateBlockingStatus(RequestStatus.PENDING));
        assertTrue(RequestStatus.isDuplicateBlockingStatus(RequestStatus.APPROVED));
        assertTrue(RequestStatus.isDuplicateBlockingStatus(RequestStatus.PENDING_ADHOC));
        assertFalse(RequestStatus.isDuplicateBlockingStatus(RequestStatus.CANCELLED));
    }

    @Test
    public void isTerminal_returnsTrue_forClosedOutcomes() {
        assertTrue(RequestStatus.isTerminal(RequestStatus.REJECTED));
        assertTrue(RequestStatus.isTerminal(RequestStatus.DENIED));
        assertTrue(RequestStatus.isTerminal(RequestStatus.CANCELLED));
        assertFalse(RequestStatus.isTerminal(RequestStatus.PENDING));
    }
}
