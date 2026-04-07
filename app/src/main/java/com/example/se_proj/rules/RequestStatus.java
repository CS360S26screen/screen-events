package com.example.se_proj.rules;

import java.util.Locale;

/**
 * Centralizes visitor-request status constants and status-based predicates.
 *
 * <p>This class follows the <b>Value Object</b> pattern — all status values are
 * lowercase string constants that match exactly what is stored in Firestore.
 * Every comparison in the app should go through these constants (or
 * {@link #normalize(String)}) to avoid case/whitespace mismatches.</p>
 *
 * <h3>Status Lifecycle</h3>
 * <pre>
 *   pending / pending_adhoc
 *       |          |
 *       v          v
 *   approved   denied / rejected
 *       |
 *       v
 *   cancelled  (by host, before visitor enters campus)
 * </pre>
 */
public final class RequestStatus {

    /** Pre-scheduled request awaiting admin approval. */
    public static final String PENDING = "pending";

    /** Walk-in (ad-hoc) request awaiting faculty/host approval from their device. */
    public static final String PENDING_ADHOC = "pending_adhoc";

    /** Request approved — visitor may enter during the approved window. */
    public static final String APPROVED = "approved";

    /** Request rejected by the security administrator. */
    public static final String REJECTED = "rejected";

    /** Walk-in request denied by the faculty/host. */
    public static final String DENIED = "denied";

    /** Request cancelled by the creating host before the visitor arrived. */
    public static final String CANCELLED = "cancelled";

    private RequestStatus() {
    }

    /**
     * Trims and lower-cases a raw status string for safe comparison.
     *
     * @param status raw status value (may be {@code null}).
     * @return normalized status, or empty string if {@code null}.
     */
    public static String normalize(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns {@code true} if the request is in an editable state
     * (pending, approved, or pending_adhoc).
     *
     * @param status the current request status.
     * @return whether the host may still edit visit details.
     */
    public static boolean canEdit(String status) {
        String normalized = normalize(status);
        return PENDING.equals(normalized)
                || APPROVED.equals(normalized)
                || PENDING_ADHOC.equals(normalized);
    }

    /**
     * Returns {@code true} if the request can be cancelled by its creator.
     * A request can only be cancelled while editable and the visitor is not yet on campus.
     *
     * @param status   the current request status.
     * @param onCampus whether the visitor is currently on campus.
     * @return whether cancellation is allowed.
     */
    public static boolean canCancel(String status, boolean onCampus) {
        return !onCampus && canEdit(status);
    }

    /**
     * Returns {@code true} if the status should count toward duplicate-request detection.
     * Terminal statuses (rejected, denied, cancelled) are excluded so the user can re-submit.
     *
     * @param status the current request status.
     * @return whether the request blocks a duplicate submission.
     */
    public static boolean isDuplicateBlockingStatus(String status) {
        String normalized = normalize(status);
        return PENDING.equals(normalized)
                || APPROVED.equals(normalized)
                || PENDING_ADHOC.equals(normalized);
    }

    /**
     * Returns {@code true} if the request has reached a final state and cannot transition further.
     *
     * @param status the current request status.
     * @return whether the status is terminal.
     */
    public static boolean isTerminal(String status) {
        String normalized = normalize(status);
        return REJECTED.equals(normalized)
                || DENIED.equals(normalized)
                || CANCELLED.equals(normalized);
    }
}
