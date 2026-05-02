package com.example.se_proj.rules;

import android.util.Log;

import com.example.se_proj.models.AuditLog;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.models.ZoneAccessLog;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

/**
 * Logs every zone-access attempt (US18) with timestamp, location, and user ID.
 *
 * <p>Each scan produces two Firestore writes:</p>
 * <ol>
 *   <li><b>zone_access_logs</b> — machine-readable log with zone, outcome, and
 *       failure reason; used by the Zone Logs admin tab and security analytics.</li>
 *   <li><b>access_logs</b> (mirror) — AuditLog entry reusing the existing model so
 *       the current AdminAuditActivity continues to show all gate events without change.</li>
 * </ol>
 *
 * <p>This class has no Android UI dependencies and can be instantiated by any component
 * that holds a {@link FirebaseFirestore} reference and a guard UID.</p>
 */
public final class ZoneAccessLogger {

    private static final String TAG              = "ZoneAccessLogger";
    private static final String ZONE_COLLECTION  = "zone_access_logs";
    private static final String AUDIT_COLLECTION = "access_logs";

    public static final String ENTITY_TYPE_PERSON  = "person";
    public static final String ENTITY_TYPE_VEHICLE = "vehicle";

    private final FirebaseFirestore db;
    private final String guardId;

    /**
     * @param db      the Firestore instance to write to
     * @param guardId Firebase Auth UID of the currently logged-in guard
     */
    public ZoneAccessLogger(FirebaseFirestore db, String guardId) {
        this.db      = db;
        this.guardId = guardId;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Records a zone-access attempt for any entity (person or vehicle).
     *
     * <p>Both writes are fire-and-forget; failures are logged but do not throw so
     * that a logging error never blocks the guard's gate-control workflow.</p>
     *
     * @param entityId      CNIC (13 digits) or vehicle plate number
     * @param entityType    {@link ZoneAccessLog#ACTION_ENTRY "person"} or {@code "vehicle"}
     * @param entityName    display name shown in audit views
     * @param zoneId        gate or zone identifier (e.g. {@code "main_gate"}, {@code "block_a"})
     * @param action        {@link ZoneAccessLog#ACTION_ENTRY}, {@link ZoneAccessLog#ACTION_EXIT},
     *                      or {@link ZoneAccessLog#ACTION_DENIED}
     * @param outcome       {@link ZoneAccessLog#OUTCOME_SUCCESS} or
     *                      {@link ZoneAccessLog#OUTCOME_FAILURE}
     * @param failureReason non-empty string describing why access was denied; empty for successes
     * @param requestId     Firestore document ID of the linked visitor_request, or {@code ""}
     */
    public void logAccess(String entityId, String entityType, String entityName,
                          String zoneId, String action, String outcome,
                          String failureReason, String requestId) {

        ZoneAccessLog zoneLog = buildZoneLog(entityId, entityType, entityName, zoneId,
                action, outcome, failureReason, requestId);
        writeZoneAndAuditLogs(zoneLog);
    }

    /**
     * Records a successful zone-access attempt.
     */
    public void logSuccess(String entityId, String entityType, String entityName,
                           String zoneId, String action, String requestId) {
        logAccess(entityId, entityType, entityName, zoneId, action,
                ZoneAccessLog.OUTCOME_SUCCESS, "", requestId);
    }

    /**
     * Records a failed zone-access attempt with a denial/failure reason.
     */
    public void logFailure(String entityId, String entityType, String entityName,
                           String zoneId, String action, String reason, String requestId) {
        logAccess(entityId, entityType, entityName, zoneId, action,
                ZoneAccessLog.OUTCOME_FAILURE, reason, requestId);
    }

    /**
     * Convenience overload that derives all fields from an approved {@link VisitorRequest}.
     *
     * @param request       the visitor request being acted on
     * @param zoneId        gate identifier
     * @param action        {@link ZoneAccessLog#ACTION_ENTRY}, EXIT, or DENIED
     * @param outcome       SUCCESS or FAILURE
     * @param failureReason empty for successes
     */
    public void logAccessForVisitor(VisitorRequest request, String zoneId,
                                    String action, String outcome, String failureReason) {
        logAccess(
                request != null ? request.getGuestCNIC() : "",
                ENTITY_TYPE_PERSON,
                request != null ? request.getGuestName() : "",
                zoneId,
                action,
                outcome,
                failureReason,
                request != null ? request.getRequestId() : ""
        );
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private ZoneAccessLog buildZoneLog(String entityId, String entityType, String entityName,
                                       String zoneId, String action, String outcome,
                                       String failureReason, String requestId) {
        return new ZoneAccessLog(
                normalize(entityId),
                normalizeEntityType(entityType),
                normalize(entityName),
                normalizeLocation(zoneId),
                normalizeAction(action, outcome),
                normalizeOutcome(outcome),
                normalize(failureReason),
                normalize(guardId),
                normalize(requestId)
        );
    }

    private void writeZoneAndAuditLogs(ZoneAccessLog zoneLog) {
        DocumentReference zoneRef = db.collection(ZONE_COLLECTION).document();
        zoneLog.setLogId(zoneRef.getId());

        AuditLog auditLog = AuditLogUtils.toZoneAccessAuditLog(zoneLog);
        DocumentReference auditRef = db.collection(AUDIT_COLLECTION).document();
        auditLog.setId(auditRef.getId());

        WriteBatch batch = db.batch();
        batch.set(zoneRef, zoneLog);
        batch.set(auditRef, auditLog);
        batch.commit()
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "Zone access logged: " + zoneRef.getId()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Zone/audit log write failed: " + e.getMessage()));
    }

    private String normalizeEntityType(String entityType) {
        String normalized = normalize(entityType).toLowerCase(java.util.Locale.ROOT);
        if (ENTITY_TYPE_VEHICLE.equals(normalized)) {
            return ENTITY_TYPE_VEHICLE;
        }
        return ENTITY_TYPE_PERSON;
    }

    private String normalizeLocation(String zoneId) {
        String normalized = normalize(zoneId);
        return normalized.isEmpty() ? "unknown_zone" : normalized;
    }

    private String normalizeAction(String action, String outcome) {
        String normalized = normalize(action).toUpperCase(java.util.Locale.ROOT);
        if (ZoneAccessLog.ACTION_ENTRY.equals(normalized)
                || ZoneAccessLog.ACTION_EXIT.equals(normalized)
                || ZoneAccessLog.ACTION_DENIED.equals(normalized)) {
            return normalized;
        }
        return ZoneAccessLog.OUTCOME_FAILURE.equals(normalizeOutcome(outcome))
                ? ZoneAccessLog.ACTION_DENIED
                : ZoneAccessLog.ACTION_ENTRY;
    }

    private String normalizeOutcome(String outcome) {
        String normalized = normalize(outcome).toUpperCase(java.util.Locale.ROOT);
        if (ZoneAccessLog.OUTCOME_SUCCESS.equals(normalized)) {
            return ZoneAccessLog.OUTCOME_SUCCESS;
        }
        return ZoneAccessLog.OUTCOME_FAILURE;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
