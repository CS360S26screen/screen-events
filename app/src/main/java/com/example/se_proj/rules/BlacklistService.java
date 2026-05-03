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
 */
public final class BlacklistService {

    private static final String TAG        = "BlacklistService";
    private static final String COLLECTION = "blacklist";

    private static final String FIELD_ENTITY_ID = "entityId";
    // Firestore serializes 'boolean isActive' as 'active'
    private static final String FIELD_IS_ACTIVE = "active";
    private static final String FIELD_BAN_TYPE  = "banType";
    private static final String FIELD_EXPIRY    = "expiryDate";

    public interface BlacklistCheckCallback {
        void onResult(boolean isBlacklisted, BlacklistEntry entry);
        void onError(Exception e);
    }

    public interface BlacklistWriteCallback {
        void onSuccess();
        void onError(Exception e);
    }

    private final FirebaseFirestore db;

    public BlacklistService(FirebaseFirestore db) {
        this.db = db;
    }

    /**
     * Checks whether an entity is blacklisted. 
     * Uses whereIn to handle variations like "FT454", "FT-454", and "FT - 454".
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

    public void checkBlacklist(String entityId, BlacklistCheckCallback callback) {
        checkBlacklist(entityId)
                .addOnSuccessListener(entry -> callback.onResult(entry != null, entry))
                .addOnFailureListener(callback::onError);
    }

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
