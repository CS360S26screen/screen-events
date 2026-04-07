package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;
import com.google.firebase.Timestamp;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class UiFormatUtilsTest {

    @Test
    public void formatVisitorDate_prefixesTheVisitDateForAdminCards() {
        assertEquals("Date: 09/04/2026", UiFormatUtils.formatVisitorDate("09/04/2026"));
    }

    @Test
    public void formatFacultyVisitInfo_combinesDateAndApprovedWindow() {
        assertEquals(
                "Date: 09/04/2026 | 10:00 - 11:00",
                UiFormatUtils.formatFacultyVisitInfo(request())
        );
    }

    @Test
    public void formatRequestStatus_normalizesStatusTextBeforeDisplay() {
        assertEquals("Status: APPROVED", UiFormatUtils.formatRequestStatus(" Approved "));
    }

    @Test
    public void formatAuditTimestamp_usesStableDatabaseFriendlyPattern() {
        String expected = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(0));

        assertEquals(
                expected,
                UiFormatUtils.formatAuditTimestamp(new Timestamp(0, 0))
        );
    }

    @Test
    public void resolveAuditVisualState_prioritizesOverstayViewOverActionType() {
        assertEquals(
                UiFormatUtils.AuditVisualState.OVERSTAYING,
                UiFormatUtils.resolveAuditVisualState("Entry", true)
        );
        assertEquals(
                UiFormatUtils.AuditVisualState.ENTRY,
                UiFormatUtils.resolveAuditVisualState("Entry", false)
        );
        assertEquals(
                UiFormatUtils.AuditVisualState.DENIED,
                UiFormatUtils.resolveAuditVisualState("Denied", false)
        );
    }

    private static VisitorRequest request() {
        return new VisitorRequest(
                "req-1",
                "Guest One",
                "3520212345671",
                "Meeting",
                "09/04/2026",
                "10:00",
                "11:00",
                "FAC-1",
                "creator-1",
                "faculty",
                RequestStatus.APPROVED,
                null,
                null,
                false
        );
    }
}
