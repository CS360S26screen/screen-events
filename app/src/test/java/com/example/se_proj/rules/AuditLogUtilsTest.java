package com.example.se_proj.rules;

import com.example.se_proj.models.AuditLog;
import com.example.se_proj.models.VisitorRequest;
import com.google.firebase.Timestamp;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuditLogUtilsTest {

    @Test
    public void mergeAndSortDistinct_deduplicatesByDocumentIdAndSortsNewestFirst() {
        AuditLog oldest = log("log-1", "Entry", 1000L);
        AuditLog newest = log("log-2", "Denied", 3000L);
        AuditLog duplicateNewest = log("log-2", "Denied", 3000L);

        List<AuditLog> merged = AuditLogUtils.mergeAndSortDistinct(
                Arrays.asList(oldest, newest),
                Collections.singletonList(duplicateNewest)
        );

        assertEquals(2, merged.size());
        assertEquals("log-2", merged.get(0).getId());
        assertEquals("log-1", merged.get(1).getId());
    }

    @Test
    public void mergeAndSortDistinct_usesCompositeKey_whenIdsAreMissing() {
        AuditLog first = log("", "Denied", 3000L);
        AuditLog second = log("", "Denied", 3000L);

        List<AuditLog> merged = AuditLogUtils.mergeAndSortDistinct(
                Collections.singletonList(first),
                Collections.singletonList(second)
        );

        assertEquals(1, merged.size());
    }

    @Test
    public void isOverstaying_returnsTrueOnlyForOnCampusGuestsWhoseWindowHasEnded() {
        VisitorRequest overstaying = request(true, "07/04/2026", "10:00", "11:00");
        VisitorRequest notCheckedIn = request(false, "07/04/2026", "10:00", "11:00");

        LocalDateTime now = LocalDateTime.of(2026, 4, 7, 11, 0);

        assertTrue(AuditLogUtils.isOverstaying(overstaying, now));
        assertFalse(AuditLogUtils.isOverstaying(notCheckedIn, now));
    }

    @Test
    public void shouldSendExitReminder_returnsTrue_onlyInsideConfiguredReminderWindow() {
        VisitorRequest request = request(true, "07/04/2026", "10:00", "11:00");

        assertTrue(AuditLogUtils.shouldSendExitReminder(
                request,
                LocalDateTime.of(2026, 4, 7, 10, 50),
                15
        ));
        assertFalse(AuditLogUtils.shouldSendExitReminder(
                request,
                LocalDateTime.of(2026, 4, 7, 10, 30),
                15
        ));
    }

    @Test
    public void toOverstayAuditLog_copiesVisitorAndHostFieldsIntoAuditEntry() {
        VisitorRequest request = request(true, "07/04/2026", "10:00", "11:00");

        AuditLog auditLog = AuditLogUtils.toOverstayAuditLog(request);

        assertEquals("Guest One", auditLog.getVisitorName());
        assertEquals("3520212345671", auditLog.getVisitorCNIC());
        assertEquals("FAC-1", auditLog.getHostId());
        assertEquals("OVERSTAYING", auditLog.getAction());
        assertEquals("Scheduled exit: 11:00", auditLog.getReason());
    }

    private static AuditLog log(String id, String action, long millis) {
        return new AuditLog(
                id,
                "Guest One",
                "3520212345671",
                "FAC-1",
                action,
                action.equals("Denied") ? "Expired Window" : "",
                new Timestamp(millis / 1000, 0)
        );
    }

    private static VisitorRequest request(
            boolean onCampus,
            String visitDate,
            String startTime,
            String endTime
    ) {
        return new VisitorRequest(
                "req-1",
                "Guest One",
                "3520212345671",
                "Meeting",
                visitDate,
                startTime,
                endTime,
                "FAC-1",
                "creator-1",
                "faculty",
                RequestStatus.APPROVED,
                null,
                null,
                onCampus
        );
    }
}
