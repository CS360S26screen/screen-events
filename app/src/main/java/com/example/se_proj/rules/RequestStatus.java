package com.example.se_proj.rules;

import java.util.Locale;

/**
 * Centralizes request status values so the UI, validation, and Firestore queries stay consistent.
 */
public final class RequestStatus {

    public static final String PENDING = "pending";
    public static final String PENDING_ADHOC = "pending_adhoc";
    public static final String APPROVED = "approved";
    public static final String REJECTED = "rejected";
    public static final String DENIED = "denied";
    public static final String CANCELLED = "cancelled";

    private RequestStatus() {
    }

    public static String normalize(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean canEdit(String status) {
        String normalized = normalize(status);
        return PENDING.equals(normalized) 
                || APPROVED.equals(normalized) 
                || PENDING_ADHOC.equals(normalized);
    }

    public static boolean canCancel(String status, boolean onCampus) {
        return !onCampus && canEdit(status);
    }

    public static boolean isDuplicateBlockingStatus(String status) {
        String normalized = normalize(status);
        return PENDING.equals(normalized)
                || APPROVED.equals(normalized)
                || PENDING_ADHOC.equals(normalized);
    }

    public static boolean isTerminal(String status) {
        String normalized = normalize(status);
        return REJECTED.equals(normalized)
                || DENIED.equals(normalized)
                || CANCELLED.equals(normalized);
    }
}
