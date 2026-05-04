package com.example.se_proj.rules;

import android.util.Log;

import com.example.se_proj.models.BlacklistEntry;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Manages the real-time blacklist system (US19).
 *
 * <p>Role in app: encapsulates Firestore reads and writes for active blacklist entries,
 * duplicate blacklist checks, soft removal, and temporary-ban cleanup.</p>
 *
 * <p><b>Design pattern:</b> Service layer. Activities delegate blacklist persistence and
 * normalization rules to this class instead of embedding Firestore query details in UI code.</p>
 */
public final class BlacklistService {

    private static final String TAG        = "BlacklistService";
    private static final String COLLECTION = "blacklist";

    private static final String FIELD_ENTITY_ID = "entityId";
    // Firestore serializes 'boolean isActive' as 'active'
    private static final String FIELD_IS_ACTIVE = "active";
    private static final String FIELD_BAN_TYPE  = "banType";
    private static final String FIELD_EXPIRY    = "expiryDate";

    /**
     * Callback contract for asynchronous blacklist lookup results.
     */
    public interface BlacklistCheckCallback {
        /**
         * Receives the lookup result.
         *
         * @param isBlacklisted {@code true} when an active, non-expired entry was found.
         * @param entry the matching blacklist entry, or {@code null} when none exists.
         */
        void onResult(boolean isBlacklisted, BlacklistEntry entry);

        /**
         * Receives lookup failures from Firestore.
         *
         * @param e the failure raised by the lookup operation.
         */
        void onError(Exception e);
    }

    /**
     * Callback contract for asynchronous blacklist write operations.
     */
    public interface BlacklistWriteCallback {
        /**
         * Called when the write operation completes successfully.
         */
        void onSuccess();

        /**
         * Called when the write operation fails.
         *
         * @param e the failure raised by the write operation.
         */
        void onError(Exception e);
    }

    private final FirebaseFirestore db;

    /**
     * Creates a blacklist service backed by the supplied Firestore instance.
     *
     * @param db Firestore database used for blacklist queries and updates.
     */
    public BlacklistService(FirebaseFirestore db) {
        this.db = db;
    }

    /**
     * Checks whether an entity is blacklisted. 
     * Uses whereIn to handle variations like "FT454", "FT-454", and "FT - 454".
     *
     * @param entityId CNIC, vehicle plate, or other entity identifier to check.
     * @return a task that resolves to the active matching entry, or {@code null} if not blacklisted.
     */
    public Task<BlacklistEntry> checkBlacklist(String entityId) {
        TaskCompletionSource<BlacklistEntry> source = new TaskCompletionSource<>();
        if (entityId == null || entityId.trim().isEmpty()) {
            source.setResult(null);
            return source.getTask();
        }

        String normalized = normalizeEntityId(entityId);
        String raw = entityId.trim();
        
        // Build a set of possible variations to search for (Firestore exact match)
        Set<String> candidates = new HashSet<>();
        candidates.add(normalized);
        candidates.add(raw);
        candidates.add(raw.toUpperCase(Locale.ROOT));
        
        // Add variations with spaces if it looks like a plate (e.g. "FT - 454")
        if (normalized.length() > 3) {
             // Try putting spaces around any existing dash or between letters and numbers
             candidates.add(raw.replace("-", " - "));
             candidates.add(normalized.replaceAll("^([A-Z]+)(\\d+)$", "$1 - $2"));
        }

        List<String> searchList = new ArrayList<>(candidates);
        Log.d(TAG, "Checking blacklist for candidates: " + searchList);

        db.collection(COLLECTION)
                .whereIn(FIELD_ENTITY_ID, searchList)
                .get()
                .addOnSuccessListener(snapshots -> {
                    BlacklistEntry activeEntry = findEffectiveEntry(snapshots, Timestamp.now());
                    source.setResult(activeEntry);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "checkBlacklist query failed", e);
                    source.setException(e);
                });

        return source.getTask();
    }

    /**
     * Checks whether an entity is blacklisted and reports the result through a callback.
     *
     * @param entityId CNIC, vehicle plate, or other entity identifier to check.
     * @param callback callback that receives the lookup result or failure.
     */
    public void checkBlacklist(String entityId, BlacklistCheckCallback callback) {
        checkBlacklist(entityId)
                .addOnSuccessListener(entry -> callback.onResult(entry != null, entry))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Adds an entity to the blacklist after confirming that no active entry already exists.
     *
     * @param entityId CNIC, vehicle plate, or other identifier to blacklist.
     * @param entityType entity category, such as {@link BlacklistEntry#TYPE_PERSON} or
     *                   {@link BlacklistEntry#TYPE_VEHICLE}.
     * @param entityName display name or description for the entity.
     * @param reason reason the entity is being blacklisted.
     * @param banType ban duration type, such as {@link BlacklistEntry#BAN_PERMANENT} or
     *                {@link BlacklistEntry#BAN_TEMPORARY}.
     * @param expiryDate expiry timestamp for temporary bans, or {@code null} for permanent bans.
     * @param addedBy Firebase Auth UID of the admin adding the entry.
     * @param callback callback that receives success or validation/write failure.
     */
    public void addToBlacklist(String entityId, String entityType, String entityName,
                                String reason, String banType, Timestamp expiryDate,
                                String addedBy, BlacklistWriteCallback callback) {

        String normalizedEntityId = normalizeEntityId(entityId);
        checkBlacklist(normalizedEntityId).addOnSuccessListener(existing -> {
            if (existing != null) {
                callback.onError(new IllegalStateException("Entity is already blacklisted."));
                return;
            }
            
            BlacklistEntry entry = new BlacklistEntry(
                    normalizedEntityId, entityType, entityName, reason.trim(), banType, expiryDate, addedBy
            );
            db.collection(COLLECTION).add(entry)
                    .addOnSuccessListener(ref -> callback.onSuccess())
                    .addOnFailureListener(callback::onError);
        });
    }

    /**
     * Soft-removes a blacklist entry by setting its Firestore {@code active} field to false.
     *
     * @param entryId Firestore document ID of the blacklist entry.
     * @param callback callback that receives success or update failure.
     */
    public void removeFromBlacklist(String entryId, BlacklistWriteCallback callback) {
        db.collection(COLLECTION).document(entryId)
                .update(FIELD_IS_ACTIVE, false)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(callback::onError);
    }

    /**
     * Maintenance task to deactivate entries whose expiry date has passed.
     */
    public void removeExpiredEntries() {
        Timestamp now = Timestamp.now();
        db.collection(COLLECTION)
                .whereEqualTo(FIELD_IS_ACTIVE, true)
                .whereEqualTo(FIELD_BAN_TYPE, BlacklistEntry.BAN_TEMPORARY)
                .whereLessThan(FIELD_EXPIRY, now)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) return;
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        batch.update(doc.getReference(), FIELD_IS_ACTIVE, false);
                    }
                    batch.commit().addOnFailureListener(e -> Log.e(TAG, "Failed to commit expired entries batch", e));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching expired entries", e));
    }

    private BlacklistEntry findEffectiveEntry(QuerySnapshot snapshots, Timestamp now) {
        if (snapshots == null) return null;
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            BlacklistEntry entry = doc.toObject(BlacklistEntry.class);
            if (entry != null && entry.isActive() && !entry.isExpired()) {
                if (entry.getEntryId().isEmpty()) entry.setEntryId(doc.getId());
                return entry;
            }
        }
        return null;
    }

    private String normalizeEntityId(String entityId) {
        if (entityId == null) return "";
        return entityId.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
