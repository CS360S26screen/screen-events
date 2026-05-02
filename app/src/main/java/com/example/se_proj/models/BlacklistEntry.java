package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Domain model for a blacklist entry in the Campus Gate Access System.
 *
 * <p>Maps to the Firestore {@code blacklist} collection. Supports both permanent bans
 * (no expiry) and temporary bans with an explicit expiry timestamp. Entries are soft-deleted
 * (isActive = false) rather than physically removed to preserve audit history.</p>
 *
 * <h3>Firestore document structure</h3>
 * <pre>
 * blacklist/{entryId}
 *   entityId      : "4201234567890"          // CNIC or vehicle plate
 *   entityType    : "person" | "vehicle"
 *   entityName    : "John Doe"
 *   reason        : "Theft attempt on 2026-04-01"
 *   banType       : "permanent" | "temporary"
 *   expiryDate    : Timestamp | null         // null for permanent bans
 *   addedBy       : "firebase-uid"
 *   addedAt       : Timestamp
 *   isActive      : true | false
 * </pre>
 *
 * <h3>Composite index required</h3>
 * {@code entityId ASC, isActive ASC} — used by BlacklistService.checkBlacklist().
 */
public class BlacklistEntry {

    public static final String TYPE_PERSON  = "person";
    public static final String TYPE_VEHICLE = "vehicle";
    public static final String BAN_PERMANENT = "permanent";
    public static final String BAN_TEMPORARY = "temporary";

    @DocumentId
    private String entryId    = "";
    private String entityId   = "";
    private String entityType = "";
    private String entityName = "";
    private String reason     = "";
    private String banType    = "";
    private Timestamp expiryDate = null;
    private String addedBy    = "";
    private Timestamp addedAt = Timestamp.now();
    private boolean isActive  = true;

    /** Required no-arg constructor for Firestore deserialization. */
    public BlacklistEntry() {}

    public BlacklistEntry(String entityId, String entityType, String entityName,
                          String reason, String banType, Timestamp expiryDate,
                          String addedBy) {
        this.entityId   = entityId;
        this.entityType = entityType;
        this.entityName = entityName;
        this.reason     = reason;
        this.banType    = banType;
        this.expiryDate = expiryDate;
        this.addedBy    = addedBy;
        this.addedAt    = Timestamp.now();
        this.isActive   = true;
    }

    // -------------------------------------------------------------------------
    // Derived predicates (pure Java — testable without Firestore)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if this is a temporary ban whose expiry has passed.
     * Always {@code false} for permanent bans or entries with no expiry set.
     */
    public boolean isExpired() {
        if (BAN_PERMANENT.equals(banType) || expiryDate == null) return false;
        return Timestamp.now().compareTo(expiryDate) >= 0;
    }

    /**
     * Returns {@code true} if this entry should currently block access:
     * the entry is marked active AND has not expired.
     */
    public boolean isEffectivelyActive() {
        return isActive && !isExpired();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getEntryId()      { return entryId; }
    public String getEntityId()     { return entityId; }
    public String getEntityType()   { return entityType; }
    public String getEntityName()   { return entityName; }
    public String getReason()       { return reason; }
    public String getBanType()      { return banType; }
    public Timestamp getExpiryDate(){ return expiryDate; }
    public String getAddedBy()      { return addedBy; }
    public Timestamp getAddedAt()   { return addedAt; }
    public boolean isActive()       { return isActive; }

    // -------------------------------------------------------------------------
    // Setters (required for Firestore deserialization)
    // -------------------------------------------------------------------------

    public void setEntryId(String entryId)          { this.entryId   = entryId; }
    public void setEntityId(String entityId)         { this.entityId  = entityId; }
    public void setEntityType(String entityType)     { this.entityType = entityType; }
    public void setEntityName(String entityName)     { this.entityName = entityName; }
    public void setReason(String reason)             { this.reason    = reason; }
    public void setBanType(String banType)           { this.banType   = banType; }
    public void setExpiryDate(Timestamp expiryDate)  { this.expiryDate = expiryDate; }
    public void setAddedBy(String addedBy)           { this.addedBy   = addedBy; }
    public void setAddedAt(Timestamp addedAt)        { this.addedAt   = addedAt; }
    public void setActive(boolean active)            { isActive       = active; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlacklistEntry)) return false;
        BlacklistEntry that = (BlacklistEntry) o;
        return Objects.equals(entryId, that.entryId)
                && Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() { return Objects.hash(entryId, entityId); }

    @Override
    public String toString() {
        return "BlacklistEntry{entityId='" + entityId + "', type='" + entityType
                + "', banType='" + banType + "', active=" + isActive + "}";
    }
}
