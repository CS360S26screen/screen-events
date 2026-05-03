package com.example.se_proj.rules;

import android.util.Log;

import com.example.se_proj.models.DeliveryLog;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manages the delivery rider lifecycle (US21): entry → destination → exit → duration → overstay.
 *
 * <p>Each delivery creates one document in the {@code delivery_logs} Firestore collection.
 * The document is created on entry ({@link #startDelivery}) and closed on exit
 * ({@link #endDelivery}). The allowed window is {@link #DELIVERY_WINDOW_MINUTES} minutes;
 * riders who remain on campus past that window are flagged and trigger an
 * {@link AlertManager} delivery-overstay alert.</p>
 *
 * <h3>Edge cases handled</h3>
 * <ul>
 *   <li><b>Duplicate entry</b>: {@link #startDelivery} queries for an existing active delivery
 *       for the same CNIC and returns {@link IllegalStateException} if one is found.</li>
 *   <li><b>Exit without entry</b>: {@link #endDelivery} returns {@link IllegalStateException}
 *       when no active delivery exists for the CNIC.</li>
 *   <li><b>Multiple active entries</b> (data inconsistency): {@link #endDelivery} always
 *       closes the chronologically oldest active record first.</li>
 * </ul>
 *
 * <h3>Firestore indexes required</h3>
 * <ul>
 *   <li>{@code riderCNIC ASC, status ASC} — duplicate guard on startDelivery</li>
 *   <li>{@code status ASC, expectedExitTime ASC} — overstay detection scan</li>
 * </ul>
 */
public final class DeliveryTrackingService {

    private static final String TAG        = "DeliveryTrackingService";
    private static final String COLLECTION = "delivery_logs";

    private static final String FIELD_RIDER_CNIC       = "riderCNIC";
    private static final String FIELD_STATUS           = "status";
    private static final String FIELD_ENTRY_TIME       = "entryTime";
    private static final String FIELD_EXPECTED_EXIT    = "expectedExitTime";
    private static final String FIELD_EXIT_TIME        = "exitTime";
    private static final String FIELD_EXIT_ZONE_ID     = "exitZoneId";
    private static final String FIELD_DURATION_MINUTES = "durationMinutes";
    private static final String FIELD_OVERSTAY_FLAGGED = "overstayFlagged";

    /** Maximum allowed on-campus time for a delivery rider, in minutes. */
    public static final long DELIVERY_WINDOW_MINUTES = 30;

    // =========================================================================
    // Callback interfaces
    // =========================================================================

    /**
     * One-shot callback for delivery write operations.
     */
    public interface DeliveryCallback {
        /** @param deliveryId Firestore document ID of the created or updated delivery log */
        void onSuccess(String deliveryId);
        void onError(Exception e);
    }

    /**
     * Callback for overstay detection results.
     */
    public interface OverstayCallback {
        /** @param overstayingDeliveries list of active delivery logs that have exceeded the window */
        void onResult(List<DeliveryLog> overstayingDeliveries);
        void onError(Exception e);
    }

    private final FirebaseFirestore db;
    private final String guardId;
    private final AlertManager alertManager;

    /**
     * @param db           Firestore instance
     * @param guardId      Firebase Auth UID of the guard on duty
     * @param alertManager used to fire delivery-overstay alerts; may be {@code null} in tests
     */
    public DeliveryTrackingService(FirebaseFirestore db, String guardId,
                                   AlertManager alertManager) {
        this.db           = db;
        this.guardId      = guardId;
        this.alertManager = alertManager;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Registers a delivery rider's entry on campus.
     *
     * <p>Creates a new {@link DeliveryLog} document with {@code status = "active"} and
     * sets {@code expectedExitTime = now + DELIVERY_WINDOW_MINUTES}.
     * Before creating, queries for any existing active delivery for the same CNIC to
     * prevent duplicate entries caused by rapid double-scans at the gate.</p>
     *
     * @param riderId           rider's primary ID (CNIC or badge number)
     * @param riderName         display name
     * @param riderCNIC         13-digit CNIC (used as duplicate guard key)
     * @param vehicleNumber     vehicle registration plate
     * @param destinationBlock  on-campus delivery destination
     * @param callback          result callback
     */
    public void startDelivery(String riderId, String riderName, String riderCNIC,
                              String vehicleNumber, String destinationBlock,
                              DeliveryCallback callback) {
        startDelivery(riderId, riderName, riderCNIC, "Unknown", vehicleNumber, destinationBlock,
                "Unknown", "Unknown", callback);
    }

    /**
     * Registers a delivery rider's entry with company and receiving host/faculty details.
     */
    public void startDelivery(String riderId, String riderName, String riderCNIC,
                              String companyName, String vehicleNumber, String destinationBlock,
                              String receivingHostId, String receivingHostName,
                              DeliveryCallback callback) {
        startDelivery(riderId, riderName, riderCNIC, companyName, vehicleNumber,
                destinationBlock, receivingHostId, receivingHostName, "", callback);
    }

    /**
     * Registers a delivery rider's entry with full lifecycle metadata, including gate/zone.
     */
    public void startDelivery(String riderId, String riderName, String riderCNIC,
                              String companyName, String vehicleNumber, String destinationBlock,
                              String receivingHostId, String receivingHostName, String entryZoneId,
                              DeliveryCallback callback) {
        String normalizedCnic = normalize(riderCNIC);
        IllegalArgumentException validationError =
                validateStartInput(riderName, normalizedCnic, companyName, vehicleNumber,
                        destinationBlock, receivingHostId, receivingHostName);
        if (validationError != null) {
            notifyError(callback, validationError);
            return;
        }

        // Guard against duplicate active delivery for the same rider
        db.collection(COLLECTION)
                .whereEqualTo(FIELD_RIDER_CNIC, normalizedCnic)
                .whereEqualTo(FIELD_STATUS, DeliveryLog.STATUS_ACTIVE)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots != null && !snapshots.isEmpty()) {
                        notifyError(callback, new IllegalStateException(
                                "Active delivery already exists for CNIC " + normalizedCnic
                                        + ". Exit the current delivery before registering a new one."));
                        return;
                    }
                    createDeliveryDocument(
                            normalizeOrFallback(riderId, normalizedCnic),
                            normalize(riderName),
                            normalizedCnic,
                            normalize(companyName),
                            normalize(vehicleNumber).toUpperCase(Locale.ROOT),
                            normalize(destinationBlock),
                            normalize(receivingHostId),
                            normalize(receivingHostName),
                            normalizeZone(entryZoneId),
                            callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Duplicate delivery check failed for CNIC=" + normalizedCnic, e);
                    notifyError(callback, e);
                });
    }

    /**
     * Closes a delivery log on rider exit, calculates duration, and flags overstay.
     *
     * <p>Sets {@code exitTime}, {@code durationMinutes}, and {@code status}:
     * {@code "completed"} if within the window, {@code "overstay"} if the window was exceeded.
     * When no active entry is found for the CNIC the callback receives
     * {@link IllegalStateException} (exit without prior entry).</p>
     *
     * @param riderCNIC 13-digit CNIC of the rider checking out
     * @param zoneId    exit gate identifier (used in overstay alert if triggered)
     * @param callback  result callback
     */
    public void endDelivery(String riderCNIC, String zoneId, DeliveryCallback callback) {
        String normalizedCnic = normalize(riderCNIC);
        if (normalizedCnic.isEmpty()) {
            notifyError(callback, new IllegalArgumentException("riderCNIC must not be empty"));
            return;
        }

        db.collection(COLLECTION)
                .whereEqualTo(FIELD_RIDER_CNIC, normalizedCnic)
                .whereEqualTo(FIELD_STATUS, DeliveryLog.STATUS_ACTIVE)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) {
                        Log.w(TAG, "Exit attempt with no active delivery for CNIC=" + normalizedCnic);
                        notifyError(callback, new IllegalStateException(
                                "No active delivery found for CNIC: " + normalizedCnic
                                        + ". Cannot log exit without prior entry."));
                        return;
                    }
                    // If multiple exist (data inconsistency), close the oldest first
                    DocumentSnapshot doc = findOldestEntry(snapshots);
                    closeDelivery(doc, zoneId, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "endDelivery query failed for CNIC=" + normalizedCnic, e);
                    notifyError(callback, e);
                });
    }

    /**
     * Calculates the on-campus duration in whole minutes between entry and exit.
     *
     * <p>This is a pure computation — no Firestore calls — and is directly unit-testable.</p>
     *
     * @param entryTime entry timestamp
     * @param exitTime  exit timestamp
     * @return duration in whole minutes (floor), or {@code 0} if either input is {@code null}
     */
    public long calculateDuration(Timestamp entryTime, Timestamp exitTime) {
        if (entryTime == null || exitTime == null) return 0;
        long diffMillis = exitTime.toDate().getTime() - entryTime.toDate().getTime();
        return Math.max(0, TimeUnit.MILLISECONDS.toMinutes(diffMillis));
    }

    /**
     * Returns {@code true} if the delivery is still active and has exceeded the allowed window.
     *
     * <p>Pure computation — no Firestore calls.</p>
     *
     * @param log the delivery log to evaluate
     * @param now current server-equivalent timestamp
     * @return whether the delivery is currently overstaying
     */
    public boolean isOverstaying(DeliveryLog log, Timestamp now) {
        if (log == null || !DeliveryLog.STATUS_ACTIVE.equals(log.getStatus())) return false;
        if (now == null) return false;
        Timestamp expected = log.getExpectedExitTime();
        return expected != null && now.compareTo(expected) >= 0;
    }

    /**
     * Scans all active deliveries and triggers an overstay alert for any that have
     * exceeded the allowed window and have not yet been flagged.
     *
     * <p>Marks {@code overstayFlagged = true} on each affected document so subsequent
     * calls do not re-fire the same alert. Call this from a periodic background task
     * (e.g. a 5-minute Firestore schedule) or on every guard-dashboard resume.</p>
     *
     * @param zoneId   gate identifier to include in overstay alert messages
     * @param callback receives the list of newly-detected overstaying deliveries
     */
    public void detectOverstay(String zoneId, OverstayCallback callback) {
        Timestamp now = Timestamp.now();
        db.collection(COLLECTION)
                .whereEqualTo(FIELD_STATUS, DeliveryLog.STATUS_ACTIVE)
                .whereLessThanOrEqualTo(FIELD_EXPECTED_EXIT, now)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null) {
                        notifyOverstayResult(callback, new ArrayList<>());
                        return;
                    }
                    List<DeliveryLog> overstaying = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        DeliveryLog log = doc.toObject(DeliveryLog.class);
                        if (log == null) continue;
                        if (log.getDeliveryId() == null || log.getDeliveryId().isEmpty()) {
                            log.setDeliveryId(doc.getId());
                        }
                        if (isOverstaying(log, now) && !log.isOverstayFlagged()) {
                            overstaying.add(log);
                            flagOverstayAndAlert(doc, log, zoneId);
                        }
                    }
                    notifyOverstayResult(callback, overstaying);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "detectOverstay query failed", e);
                    if (callback != null) callback.onError(e);
                });
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void createDeliveryDocument(String riderId, String riderName, String riderCNIC,
                                         String companyName, String vehicleNumber,
                                         String destinationBlock, String receivingHostId,
                                         String receivingHostName, String entryZoneId,
                                         DeliveryCallback callback) {
        Timestamp now          = Timestamp.now();
        Timestamp expectedExit = addMinutes(now, DELIVERY_WINDOW_MINUTES);

        DocumentReference ref = db.collection(COLLECTION).document();
        DeliveryLog log = new DeliveryLog(riderId, riderName, riderCNIC, companyName,
                vehicleNumber, destinationBlock, receivingHostId, receivingHostName,
                entryZoneId, now, expectedExit, guardId);
        log.setDeliveryId(ref.getId());

        ref.set(log)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Delivery started: " + ref.getId()
                            + " | CNIC=" + riderCNIC + " | dest=" + destinationBlock);
                    notifySuccess(callback, ref.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create delivery log for CNIC=" + riderCNIC, e);
                    notifyError(callback, e);
                });
    }

    private void closeDelivery(DocumentSnapshot doc, String zoneId, DeliveryCallback callback) {
        DeliveryLog entry = doc.toObject(DeliveryLog.class);
        if (entry == null) {
            notifyError(callback, new IllegalStateException("Malformed delivery log: " + doc.getId()));
            return;
        }

        Timestamp exitTime       = Timestamp.now();
        long durationMinutes     = calculateDuration(entry.getEntryTime(), exitTime);
        boolean isOverstay       = isDeliveryWindowExceeded(entry, exitTime);
        String finalStatus       = isOverstay
                ? DeliveryLog.STATUS_OVERSTAY : DeliveryLog.STATUS_COMPLETED;

        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_EXIT_TIME,        exitTime);
        updates.put(FIELD_EXIT_ZONE_ID,     normalizeZone(zoneId));
        updates.put(FIELD_DURATION_MINUTES, durationMinutes);
        updates.put(FIELD_STATUS,           finalStatus);
        updates.put(FIELD_OVERSTAY_FLAGGED, isOverstay || entry.isOverstayFlagged());

        doc.getReference()
                .update(updates)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Delivery closed: " + doc.getId()
                            + " | duration=" + durationMinutes + "min | status=" + finalStatus);
                    if (isOverstay && alertManager != null) {
                        alertManager.triggerDeliveryOverstayAlert(
                                doc.getId(), entry.getRiderName(), normalizeZone(zoneId), null);
                    }
                    notifySuccess(callback, doc.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to close delivery: " + doc.getId(), e);
                    notifyError(callback, e);
                });
    }

    private void flagOverstayAndAlert(DocumentSnapshot doc, DeliveryLog log, String zoneId) {
        doc.getReference()
                .update(FIELD_OVERSTAY_FLAGGED, true)
                .addOnSuccessListener(unused -> {
                    if (alertManager != null) {
                        alertManager.triggerDeliveryOverstayAlert(
                                doc.getId(), log.getRiderName(), normalizeZone(zoneId), null);
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to flag overstay on doc=" + doc.getId(), e));
    }

    /** Returns a {@link Timestamp} equal to {@code base} plus {@code minutes}. */
    private Timestamp addMinutes(Timestamp base, long minutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(base.toDate());
        cal.add(Calendar.MINUTE, (int) minutes);
        return new Timestamp(cal.getTime());
    }

    /**
     * When multiple active entries exist for the same CNIC (data inconsistency),
     * returns the document with the earliest entryTime to close the oldest first.
     */
    private DocumentSnapshot findOldestEntry(QuerySnapshot snapshots) {
        DocumentSnapshot oldest      = null;
        Timestamp        earliestTime = null;
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Timestamp entryTime = doc.getTimestamp(FIELD_ENTRY_TIME);
            if (entryTime == null) continue;
            if (earliestTime == null || entryTime.compareTo(earliestTime) < 0) {
                earliestTime = entryTime;
                oldest       = doc;
            }
        }
        // Fallback: return the first document if no entryTime was found
        return oldest != null ? oldest : snapshots.getDocuments().get(0);
    }

    private boolean isDeliveryWindowExceeded(DeliveryLog entry, Timestamp now) {
        Timestamp expectedExitTime = entry.getExpectedExitTime();
        return expectedExitTime != null && now.compareTo(expectedExitTime) > 0;
    }

    private IllegalArgumentException validateStartInput(String riderName, String riderCNIC,
                                                        String companyName, String vehicleNumber,
                                                        String destinationBlock,
                                                        String receivingHostId,
                                                        String receivingHostName) {
        if (normalize(riderCNIC).isEmpty()) {
            return new IllegalArgumentException("riderCNIC must not be empty");
        }
        if (normalize(riderName).isEmpty()) {
            return new IllegalArgumentException("riderName must not be empty");
        }
        if (normalize(companyName).isEmpty()) {
            return new IllegalArgumentException("companyName must not be empty");
        }
        if (normalize(vehicleNumber).isEmpty()) {
            return new IllegalArgumentException("vehicleNumber must not be empty");
        }
        if (normalize(destinationBlock).isEmpty()) {
            return new IllegalArgumentException("destinationBlock must not be empty");
        }
        if (normalize(receivingHostId).isEmpty()) {
            return new IllegalArgumentException("receivingHostId must not be empty");
        }
        if (normalize(receivingHostName).isEmpty()) {
            return new IllegalArgumentException("receivingHostName must not be empty");
        }
        return null;
    }

    private void notifySuccess(DeliveryCallback callback, String deliveryId) {
        if (callback != null) callback.onSuccess(deliveryId);
    }

    private void notifyError(DeliveryCallback callback, Exception e) {
        if (callback != null) callback.onError(e);
    }

    private void notifyOverstayResult(OverstayCallback callback, List<DeliveryLog> logs) {
        if (callback != null) callback.onResult(logs);
    }

    private String normalizeZone(String zoneId) {
        String normalized = normalize(zoneId);
        return normalized.isEmpty() ? "unknown_zone" : normalized;
    }

    private String normalizeOrFallback(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
