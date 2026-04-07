package com.example.se_proj.rules;

import com.example.se_proj.models.VisitorRequest;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Centralized UI text-formatting utilities shared across RecyclerView adapters.
 *
 * <p>Extracting formatting logic into a pure-Java utility keeps adapters thin and
 * ensures consistent presentation of dates, statuses, and timestamps throughout
 * the application. All methods are null-safe.</p>
 */
public final class UiFormatUtils {

    private UiFormatUtils() {
    }

    /**
     * Formats a visit date string for the admin pending-requests list.
     *
     * @param visitDate raw date string (e.g. {@code "09/04/2026"}).
     * @return display string prefixed with {@code "Date: "}.
     */
    public static String formatVisitorDate(String visitDate) {
        return "Date: " + safe(visitDate);
    }

    /**
     * Formats a faculty request's date and time window for the "My Requests" list.
     *
     * @param request the visitor request to format.
     * @return display string like {@code "Date: 09/04/2026 | 10:00 - 14:00"}.
     */
    public static String formatFacultyVisitInfo(VisitorRequest request) {
        return "Date: " + safe(request.getVisitDate())
                + " | "
                + safe(request.getStartTime())
                + " - "
                + safe(request.getEndTime());
    }

    /**
     * Normalizes and uppercases a request status for display (e.g. {@code "Status: APPROVED"}).
     *
     * @param status raw status value.
     * @return formatted display string.
     */
    public static String formatRequestStatus(String status) {
        return "Status: " + RequestStatus.normalize(status).toUpperCase(Locale.ROOT);
    }

    /**
     * Formats a Firestore {@link Timestamp} into a human-readable string for audit log rows.
     *
     * @param timestamp the Firestore timestamp (falls back to epoch if {@code null}).
     * @return formatted string like {@code "2026-04-07 14:30:05"}.
     */
    public static String formatAuditTimestamp(Timestamp timestamp) {
        Date date = timestamp == null ? new Date(0) : timestamp.toDate();
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date);
    }

    /**
     * Maps an audit-log action string to a visual state enum that drives card background
     * colors in the audit log RecyclerView.
     *
     * @param action        the audit log action (e.g. {@code "Entry"}, {@code "Denied"}).
     * @param isOverstayView whether the adapter is currently showing the overstay tab.
     * @return the {@link AuditVisualState} to apply.
     */
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

    /** Visual states that determine audit-log card background colors. */
    public enum AuditVisualState {
        /** Red tint — visitor has exceeded their approved time. */
        OVERSTAYING,
        /** Green tint — successful campus entry. */
        ENTRY,
        /** Orange tint — access was denied at the gate. */
        DENIED,
        /** White / no tint — all other actions. */
        DEFAULT
    }
}
