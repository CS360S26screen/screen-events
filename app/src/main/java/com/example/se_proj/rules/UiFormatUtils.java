package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Keeps UI text generation consistent across adapters.
 */
public final class UiFormatUtils {

    private UiFormatUtils() {
    }

    public static String formatVisitorDate(String visitDate) {
        return "Date: " + safe(visitDate);
    }

    public static String formatFacultyVisitInfo(VisitorRequest request) {
        return "Date: " + safe(request.getVisitDate())
                + " | "
                + safe(request.getStartTime())
                + " - "
                + safe(request.getEndTime());
    }

    public static String formatRequestStatus(String status) {
        return "Status: " + RequestStatus.normalize(status).toUpperCase(Locale.ROOT);
    }

    public static String formatAuditTimestamp(Timestamp timestamp) {
        Date date = timestamp == null ? new Date(0) : timestamp.toDate();
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date);
    }

    public static AuditVisualState resolveAuditVisualState(String action, boolean isOverstayView) {
        if (isOverstayView || "OVERSTAYING".equalsIgnoreCase(action)) {
            return AuditVisualState.OVERSTAYING;
        }
        if ("Entry".equalsIgnoreCase(action)) {
            return AuditVisualState.ENTRY;
        }
        if ("Denied".equalsIgnoreCase(action)) {
            return AuditVisualState.DENIED;
        }
        return AuditVisualState.DEFAULT;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public enum AuditVisualState {
        OVERSTAYING,
        ENTRY,
        DENIED,
        DEFAULT
    }
}
