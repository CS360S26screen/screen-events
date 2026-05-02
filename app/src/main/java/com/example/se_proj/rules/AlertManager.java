package com.example.se_proj.rules;

import android.util.Log;

import com.example.se_proj.models.Alert;
import com.example.se_proj.models.BlacklistEntry;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages high-priority guard alerts (US20).
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Write an {@link Alert} document to Firestore whenever a blacklisted entity is scanned
 *       or a delivery rider overstays — so ALL guards on duty see the alert in real time.</li>
 *   <li>Provide a real-time Firestore listener so the guard's UI updates without polling.</li>
 *   <li>Mark alerts as acknowledged once a guard has actioned them.</li>
 * </ul>
 *
 * <h3>Real-time delivery to all guards</h3>
 * Because every guard's {@code GuardDashboardActivity} attaches a Firestore snapshot listener
 * via {@link #listenForUnacknowledgedAlerts}, a single write by one guard immediately triggers
 * {@link AlertListener#onNewAlerts} in every other guard's UI without server-side push needed.
 *
 * <h3>Firestore index required</h3>
 * {@code isAcknowledged ASC, timestamp DESC} on the {@code alerts} collection.
 */
public final class AlertManager {

    private static final String TAG        = "AlertManager";
    private static final String COLLECTION = "alerts";

    private static final String FIELD_ACKNOWLEDGED = "isAcknowledged";
    private static final String FIELD_TIMESTAMP    = "timestamp";
    private static final String FIELD_PRIORITY     = "priority";

    // =========================================================================
    // Callback interfaces
    // =========================================================================

    /**
     * Real-time callback delivered on every Firestore snapshot update.
     */
    public interface AlertListener {
        /** Called with the current list of unacknowledged alerts (may be empty). */
        void onNewAlerts(List<Alert> unacknowledgedAlerts);
        /** Called when the Firestore listener itself encounters an error. */
        void onError(Exception e);
    }

    /**
     * One-shot callback for alert write / acknowledge operations.
     */
    public interface AlertWriteCallback {
        /** @param alertId the Firestore document ID of the written/updated alert */
        void onSuccess(String alertId);
        void onError(Exception e);
    }

    private final FirebaseFirestore db;
    private final String guardId;

    /**
     * @param db      Firestore instance to write/listen on
     * @param guardId Firebase Auth UID of the guard operating this dashboard session
     */
    public AlertManager(FirebaseFirestore db, String guardId) {
        this.db      = db;
        this.guardId = guardId;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Triggers a HIGH priority alert when a blacklisted entity is scanned (US20).
     *
     * <p>The alert document is written to {@code alerts} and will immediately appear
     * in every guard's real-time listener. The method is called by
     * {@code GuardDashboardActivity} immediately after a positive blacklist check,
     * before any other action is taken.</p>
     *
     * @param entry    the matching {@link BlacklistEntry} from BlacklistService
     * @param zoneId   gate identifier where the scan occurred (e.g. {@code "main_gate"})
     * @param callback result callback; {@code null} is accepted (fire-and-forget)
     */
    public void triggerHighPriorityAlert(BlacklistEntry entry, String zoneId,
                                          AlertWriteCallback callback) {
        if (entry == null) {
            notifyAlertError(callback, new IllegalArgumentException("blacklist entry must not be null"));
            return;
        }

        String alertType = BlacklistEntry.TYPE_VEHICLE.equals(entry.getEntityType())
                ? Alert.TYPE_BLACKLISTED_VEHICLE
                : Alert.TYPE_BLACKLISTED_PERSON;

        String message = "HIGH PRIORITY: Blacklisted " + normalize(entry.getEntityType())
                + " detected at " + normalizeZone(zoneId)
                + " | Name: " + normalize(entry.getEntityName())
                + " | ID: " + normalize(entry.getEntityId())
                + " | Reason: " + normalize(entry.getReason());

        writeAlert(alertType, Alert.PRIORITY_HIGH,
                entry.getEntityId(), entry.getEntityName(),
                message, zoneId, callback);
    }

    /**
     * Convenience overload for callers that only have scanned entity fields.
     */
    public void triggerHighPriorityAlert(String entityId, String entityType,
                                         String entityName, String reason,
                                         String zoneId, AlertWriteCallback callback) {
        String normalizedType = BlacklistEntry.TYPE_VEHICLE.equals(
                normalize(entityType).toLowerCase(java.util.Locale.ROOT))
                ? BlacklistEntry.TYPE_VEHICLE : BlacklistEntry.TYPE_PERSON;
        String alertType = BlacklistEntry.TYPE_VEHICLE.equals(normalizedType)
                ? Alert.TYPE_BLACKLISTED_VEHICLE
                : Alert.TYPE_BLACKLISTED_PERSON;
        String message = "HIGH PRIORITY: Blacklisted " + normalizedType
                + " detected at " + normalizeZone(zoneId)
                + " | Name: " + normalize(entityName)
                + " | ID: " + normalize(entityId)
                + " | Reason: " + normalize(reason);

        writeAlert(alertType, Alert.PRIORITY_HIGH,
                entityId, entityName, message, zoneId, callback);
    }

    /**
     * Triggers a MEDIUM priority alert when a delivery rider exceeds the allowed window (US21).
     *
     * <p>Called from {@link DeliveryTrackingService#detectOverstay} after the rider's
     * expected exit time has passed and the overstay has not yet been flagged.</p>
     *
     * @param deliveryId Firestore document ID from {@code delivery_logs}
     * @param riderName  rider display name
     * @param zoneId     last known zone
     * @param callback   result callback; {@code null} is accepted (fire-and-forget)
     */
    public void triggerDeliveryOverstayAlert(String deliveryId, String riderName,
                                              String zoneId, AlertWriteCallback callback) {
        String message = "DELIVERY OVERSTAY: Rider " + riderName
                + " has exceeded the 30-minute allowed delivery window at " + zoneId
                + " | Delivery ID: " + deliveryId;

        writeAlert(Alert.TYPE_DELIVERY_OVERSTAY, Alert.PRIORITY_MEDIUM,
                deliveryId, riderName, message, zoneId, callback);
    }

    /**
     * Attaches a real-time Firestore listener for all unacknowledged alerts.
     *
     * <p>The returned {@link ListenerRegistration} <strong>must</strong> be stored by
     * the caller and removed in {@code onDestroy()} to prevent memory leaks.</p>
     *
     * <pre>{@code
     * // In GuardDashboardActivity.onCreate:
     * alertListener = alertManager.listenForUnacknowledgedAlerts(new AlertManager.AlertListener() {
     *     public void onNewAlerts(List<Alert> alerts) { showAlertBanner(alerts); }
     *     public void onError(Exception e)            { Log.e(TAG, "Alert listener error", e); }
     * });
     *
     * // In GuardDashboardActivity.onDestroy:
     * if (alertListener != null) alertListener.remove();
     * }</pre>
     *
     * @param listener callback that receives live alert updates
     * @return registration handle — caller owns the lifecycle
     */
    public ListenerRegistration listenForUnacknowledgedAlerts(AlertListener listener) {
        return db.collection(COLLECTION)
                .whereEqualTo(FIELD_ACKNOWLEDGED, false)
                .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Alert listener error", e);
                        if (listener != null) listener.onError(e);
                        return;
                    }
                    List<Alert> alerts = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Alert alert = doc.toObject(Alert.class);
                            if (alert == null) continue;
                            if (alert.getAlertId() == null || alert.getAlertId().isEmpty()) {
                                alert.setAlertId(doc.getId());
                            }
                            alerts.add(alert);
                        }
                    }
                    if (listener != null) listener.onNewAlerts(alerts);
                });
    }

    /**
     * Attaches a real-time listener for only unacknowledged HIGH-priority alerts.
     */
    public ListenerRegistration listenForHighPriorityAlerts(AlertListener listener) {
        return db.collection(COLLECTION)
                .whereEqualTo(FIELD_ACKNOWLEDGED, false)
                .whereEqualTo(FIELD_PRIORITY, Alert.PRIORITY_HIGH)
                .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "High-priority alert listener error", e);
                        if (listener != null) listener.onError(e);
                        return;
                    }
                    List<Alert> alerts = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Alert alert = doc.toObject(Alert.class);
                            if (alert == null) continue;
                            if (alert.getAlertId() == null || alert.getAlertId().isEmpty()) {
                                alert.setAlertId(doc.getId());
                            }
                            alerts.add(alert);
                        }
                    }
                    if (listener != null) listener.onNewAlerts(alerts);
                });
    }

    /**
     * Marks an alert as acknowledged by the current guard.
     *
     * <p>Once acknowledged, the alert disappears from the real-time feed returned by
     * {@link #listenForUnacknowledgedAlerts} on all guard dashboards.</p>
     *
     * @param alertId  Firestore document ID of the alert to acknowledge
     * @param callback result callback
     */
    public void acknowledgeAlert(String alertId, AlertWriteCallback callback) {
        if (alertId == null || alertId.trim().isEmpty()) {
            if (callback != null) callback.onError(
                    new IllegalArgumentException("alertId must not be empty"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_ACKNOWLEDGED, true);
        updates.put("acknowledgedBy",  guardId);
        updates.put("acknowledgedAt",  Timestamp.now());

        db.collection(COLLECTION).document(alertId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Alert acknowledged: " + alertId + " by guard=" + guardId);
                    if (callback != null) callback.onSuccess(alertId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to acknowledge alert: " + alertId, e);
                    if (callback != null) callback.onError(e);
                });
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void writeAlert(String alertType, String priority,
                             String entityId, String entityName,
                             String message, String zoneId,
                             AlertWriteCallback callback) {
        IllegalArgumentException validationError =
                validateAlert(alertType, priority, entityId, message, zoneId);
        if (validationError != null) {
            notifyAlertError(callback, validationError);
            return;
        }

        DocumentReference ref = db.collection(COLLECTION).document();
        Alert alert = new Alert(
                normalizeAlertType(alertType),
                normalizePriority(priority),
                normalize(entityId),
                normalize(entityName),
                normalize(message),
                normalizeZone(zoneId),
                normalize(guardId)
        );
        alert.setAlertId(ref.getId());

        Task<Void> writeTask = ref.set(alert);
        writeTask
                .addOnSuccessListener(unused -> {
                    Log.w(TAG, "Alert created [" + alert.getPriority() + "]: " + ref.getId()
                            + " | " + alert.getAlertType() + " at " + alert.getZoneId());
                    if (callback != null) callback.onSuccess(ref.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Alert write failed: " + e.getMessage());
                    if (callback != null) callback.onError(e);
                });
    }

    private IllegalArgumentException validateAlert(String alertType, String priority,
                                                   String entityId, String message,
                                                   String zoneId) {
        if (normalize(entityId).isEmpty()) {
            return new IllegalArgumentException("entityId must not be empty");
        }
        if (normalize(message).isEmpty()) {
            return new IllegalArgumentException("message must not be empty");
        }
        if (normalize(zoneId).isEmpty()) {
            return new IllegalArgumentException("zoneId must not be empty");
        }
        String normalizedType = normalizeAlertType(alertType);
        if (!Alert.TYPE_BLACKLISTED_PERSON.equals(normalizedType)
                && !Alert.TYPE_BLACKLISTED_VEHICLE.equals(normalizedType)
                && !Alert.TYPE_DELIVERY_OVERSTAY.equals(normalizedType)
                && !Alert.TYPE_OVERSTAY.equals(normalizedType)) {
            return new IllegalArgumentException("unsupported alertType: " + alertType);
        }
        String normalizedPriority = normalizePriority(priority);
        if (!Alert.PRIORITY_HIGH.equals(normalizedPriority)
                && !Alert.PRIORITY_MEDIUM.equals(normalizedPriority)) {
            return new IllegalArgumentException("unsupported priority: " + priority);
        }
        return null;
    }

    private void notifyAlertError(AlertWriteCallback callback, Exception error) {
        Log.e(TAG, "Alert operation rejected: " + error.getMessage());
        if (callback != null) callback.onError(error);
    }

    private String normalizeAlertType(String alertType) {
        return normalize(alertType).toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizePriority(String priority) {
        return normalize(priority).toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeZone(String zoneId) {
        String normalized = normalize(zoneId);
        return normalized.isEmpty() ? "unknown_zone" : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
