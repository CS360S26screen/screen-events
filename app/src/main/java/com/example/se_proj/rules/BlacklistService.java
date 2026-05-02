package com.example.se_proj.rules;

import android.util.Log;

import com.example.se_proj.models.BlacklistEntry;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the real-time blacklist system (US19).
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Asynchronously check whether a CNIC or vehicle plate is currently blacklisted.</li>
 *   <li>Add new entries with permanent or temporary (timed-expiry) bans.</li>
 *   <li>Soft-delete entries (mark isActive=false) to preserve audit history.</li>
 *   <li>Lazily expire temporary bans discovered during a negative blacklist check.</li>
 * </ul>
 *
 * <p>All Firestore operations are non-blocking. Results are delivered on the calling
 * thread via callback interfaces, consistent with the existing activity patterns in
 * {@code GuardDashboardActivity} and {@code AdminDashboardActivity}.</p>
 *
 * <h3>Duplicate prevention</h3>
 * {@link #addToBlacklist} first queries for an existing active entry and returns an
 * {@link IllegalStateException} via the callback if one is found, preventing the same
 * entity from being blacklisted twice simultaneously.
 *
 * <h3>Firestore index required</h3>
 * {@code entityId ASC, isActive ASC} on the {@code blacklist} collection.
 */
public final class BlacklistService {

    private static final String TAG        = "BlacklistService";
    private static final String COLLECTION = "blacklist";

    private static final String FIELD_ENTITY_ID = "entityId";
    private static final String FIELD_IS_ACTIVE = "isActive";
    private static final String FIELD_BAN_TYPE  = "banType";
    private static final String FIELD_EXPIRY    = "expiryDate";

    // =========================================================================
    // Callback interfaces
    // =========================================================================

    /**
     * Callback for {@link #checkBlacklist}.
     */
    public interface BlacklistCheckCallback {
        /**
         * @param isBlacklisted {@code true} if the entity is currently and actively blacklisted
         * @param entry         the matching {@link BlacklistEntry}, or {@code null} if not listed
         */
        void onResult(boolean isBlacklisted, BlacklistEntry entry);

        /** Called when the Firestore query itself fails (e.g. offline, permission denied). */
        void onError(Exception e);
    }

    /**
     * Callback for write operations (add / remove / expire).
     */
    public interface BlacklistWriteCallback {
        void onSuccess();
        void onError(Exception e);
    }

    private final FirebaseFirestore db;

    public BlacklistService(FirebaseFirestore db) {
        this.db = db;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Checks whether an entity is currently blacklisted.
     *
     * <p>An entry is considered blacklisted only when:</p>
     * <ul>
     *   <li>{@code isActive == true}, AND</li>
     *   <li>{@code banType == "permanent"} OR ({@code banType == "temporary"}
     *       AND {@code expiryDate > now})</li>
     * </ul>
     *
     * <p>Any expired temporary-ban entries encountered during the query are lazily
     * deactivated as a side-effect (opportunistic cleanup — does not affect the result).</p>
     *
     * @param entityId CNIC (13 digits) or vehicle plate number; blank input resolves to null
     * @return task resolving to the effective active entry, or null when the entity is clear
     */
    public Task<BlacklistEntry> checkBlacklist(String entityId) {
        TaskCompletionSource<BlacklistEntry> source = new TaskCompletionSource<>();
        String normalizedEntityId = normalizeEntityId(entityId);
        if (normalizedEntityId.isEmpty()) {
            source.setResult(null);
            return source.getTask();
        }

        Timestamp now = Timestamp.now();
        db.collection(COLLECTION)
                .whereEqualTo(FIELD_ENTITY_ID, normalizedEntityId)
                .whereEqualTo(FIELD_IS_ACTIVE, true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    BlacklistEntry activeEntry = findEffectiveEntry(snapshots, now);
                    deactivateExpiredTemporaryEntries(snapshots, now);
                    source.setResult(activeEntry);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "checkBlacklist failed for entityId=" + normalizedEntityId, e);
                    source.setException(e);
                });

        return source.getTask();
    }

    /**
     * Callback wrapper around {@link #checkBlacklist(String)} for existing Android screens.
     */
    public void checkBlacklist(String entityId, BlacklistCheckCallback callback) {
        checkBlacklist(entityId)
                .addOnSuccessListener(entry -> callback.onResult(entry != null, entry))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Adds an entity to the blacklist.
     *
     * <p>If an active entry already exists for the same {@code entityId}, the callback
     * receives an {@link IllegalStateException} — the caller should inform the admin
     * and offer a remove-then-re-add workflow.</p>
     *
     * @param entityId    CNIC or vehicle plate number (must not be blank)
     * @param entityType  {@link BlacklistEntry#TYPE_PERSON} or {@link BlacklistEntry#TYPE_VEHICLE}
     * @param entityName  human-readable name for UI display
     * @param reason      reason for blacklisting
     * @param banType     {@link BlacklistEntry#BAN_PERMANENT} or {@link BlacklistEntry#BAN_TEMPORARY}
     * @param expiryDate  expiry timestamp for temporary bans; {@code null} for permanent bans
     * @param addedBy     Firebase Auth UID of the admin creating the entry
     * @param callback    result callback
     */
    public void addToBlacklist(String entityId, String entityType, String entityName,
                                String reason, String banType, Timestamp expiryDate,
                                String addedBy, BlacklistWriteCallback callback) {

        String normalizedEntityId = normalizeEntityId(entityId);
        IllegalArgumentException validationError =
                validateBlacklistWrite(normalizedEntityId, entityType, entityName,
                        reason, banType, expiryDate);
        if (validationError != null) {
            callback.onError(validationError);
            return;
        }

        // Check for existing active entry to prevent duplicates
        Timestamp now = Timestamp.now();
        db.collection(COLLECTION)
                .whereEqualTo(FIELD_ENTITY_ID, normalizedEntityId)
                .whereEqualTo(FIELD_IS_ACTIVE, true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    BlacklistEntry effectiveEntry = findEffectiveEntry(snapshots, now);
                    deactivateExpiredTemporaryEntries(snapshots, now);
                    if (effectiveEntry != null) {
                        callback.onError(new IllegalStateException(
                                "Entity " + normalizedEntityId + " is already actively blacklisted. "
                                        + "Remove the existing entry before re-adding."));
                        return;
                    }
                    writeNewEntry(normalizedEntityId, entityType, entityName.trim(),
                            reason, banType, expiryDate, addedBy, callback);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Duplicate check failed for entityId=" + normalizedEntityId, e);
                    callback.onError(e);
                });
    }

    /**
     * Soft-deletes a blacklist entry by setting {@code isActive = false}.
     *
     * <p>The document is retained in Firestore for audit trail purposes. Guards can
     * confirm in the admin view that the entity was previously blacklisted.</p>
     *
     * @param entryId  Firestore document ID of the blacklist entry (must not be blank)
     * @param callback result callback
     */
    public void removeFromBlacklist(String entryId, BlacklistWriteCallback callback) {
        if (entryId == null || entryId.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("entryId must not be empty"));
            return;
        }

        db.collection(COLLECTION).document(entryId)
                .update("isActive", false)
                .addOnSuccessListener(unused -> {
                    Log.d(TAG, "Blacklist entry deactivated: " + entryId);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to deactivate entry: " + entryId, e);
                    callback.onError(e);
                });
    }

    /**
     * Eagerly scans for all expired temporary-ban entries and deactivates them.
     *
     * <p>Call this on admin login or periodically from a WorkManager job. Not required
     * for correctness — checkBlacklist already handles lazy expiry — but reduces clutter
     * in the admin blacklist view.</p>
     */
    public void removeExpiredEntries() {
        db.collection(COLLECTION)
                .whereEqualTo(FIELD_BAN_TYPE, BlacklistEntry.BAN_TEMPORARY)
                .whereEqualTo(FIELD_IS_ACTIVE, true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null) return;
                    Timestamp now = Timestamp.now();
                    deactivateExpiredTemporaryEntries(snapshots, now);
                    Log.d(TAG, "Expiry sweep completed");
                })
                .addOnFailureListener(e -> Log.e(TAG, "Expiry sweep query failed", e));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Scans a snapshot for the first entry that is both active and not expired.
     * Returns {@code null} if no such entry is found.
     */
    private BlacklistEntry findEffectiveEntry(QuerySnapshot snapshots, Timestamp now) {
        if (snapshots == null) return null;
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            BlacklistEntry entry = doc.toObject(BlacklistEntry.class);
            if (entry == null) continue;
            if (entry.getEntryId() == null || entry.getEntryId().isEmpty()) {
                entry.setEntryId(doc.getId());
            }
            if (isEffective(entry, now)) {
                return entry;
            }
        }
        return null;
    }

    /** Deactivates any expired temporary-ban documents found in the snapshot. */
    private void deactivateExpiredTemporaryEntries(QuerySnapshot snapshots, Timestamp now) {
        if (snapshots == null) return;
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            String banType = doc.getString(FIELD_BAN_TYPE);
            if (!BlacklistEntry.BAN_TEMPORARY.equals(banType)) {
                continue;
            }
            Timestamp expiryDate = doc.getTimestamp(FIELD_EXPIRY);
            if (isExpiredTemporaryBan(expiryDate, now)) {
                Map<String, Object> updates = new HashMap<>();
                updates.put(FIELD_IS_ACTIVE, false);
                doc.getReference()
                        .update(updates)
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Expiry update failed for " + doc.getId(), e));
            }
        }
    }

    private boolean isEffective(BlacklistEntry entry, Timestamp now) {
        if (entry == null || !entry.isActive()) return false;
        String banType = entry.getBanType();
        if (BlacklistEntry.BAN_PERMANENT.equals(banType)) {
            return true;
        }
        if (BlacklistEntry.BAN_TEMPORARY.equals(banType)) {
            return !isExpiredTemporaryBan(entry.getExpiryDate(), now);
        }
        return false;
    }

    private boolean isExpiredTemporaryBan(Timestamp expiryDate, Timestamp now) {
        return expiryDate == null || now.compareTo(expiryDate) >= 0;
    }

    private void writeNewEntry(String entityId, String entityType, String entityName,
                                String reason, String banType, Timestamp expiryDate,
                                String addedBy, BlacklistWriteCallback callback) {
        Timestamp persistedExpiryDate = BlacklistEntry.BAN_TEMPORARY.equals(banType)
                ? expiryDate : null;
        BlacklistEntry entry = new BlacklistEntry(
                entityId,
                entityType,
                entityName,
                reason.trim(),
                banType,
                persistedExpiryDate,
                addedBy == null ? "" : addedBy.trim()
        );
        db.collection(COLLECTION)
                .add(entry)
                .addOnSuccessListener(ref -> {
                    Log.d(TAG, "Added to blacklist: " + entityId + " | docId=" + ref.getId());
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add to blacklist: " + entityId, e);
                    callback.onError(e);
                });
    }

    private String normalizeEntityId(String entityId) {
        return entityId == null ? "" : entityId.trim();
    }

    private IllegalArgumentException validateBlacklistWrite(String entityId, String entityType,
                                                            String entityName, String reason,
                                                            String banType, Timestamp expiryDate) {
        if (entityId.isEmpty()) {
            return new IllegalArgumentException("entityId must not be empty");
        }
        if (!BlacklistEntry.TYPE_PERSON.equals(entityType)
                && !BlacklistEntry.TYPE_VEHICLE.equals(entityType)) {
            return new IllegalArgumentException("entityType must be person or vehicle");
        }
        if (entityName == null || entityName.trim().isEmpty()) {
            return new IllegalArgumentException("entityName must not be empty");
        }
        if (reason == null || reason.trim().isEmpty()) {
            return new IllegalArgumentException("reason must not be empty");
        }
        if (!BlacklistEntry.BAN_PERMANENT.equals(banType)
                && !BlacklistEntry.BAN_TEMPORARY.equals(banType)) {
            return new IllegalArgumentException("banType must be permanent or temporary");
        }
        if (BlacklistEntry.BAN_TEMPORARY.equals(banType)) {
            if (expiryDate == null) {
                return new IllegalArgumentException("temporary bans require an expiryDate");
            }
            if (Timestamp.now().compareTo(expiryDate) >= 0) {
                return new IllegalArgumentException("temporary ban expiryDate must be in the future");
            }
        }
        return null;
    }
}
