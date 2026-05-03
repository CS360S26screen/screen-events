package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;

import java.util.Objects;

/**
 * Domain model for a blacklist entry in the Campus Gate Access System.
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
    
    // Explicitly map to 'active' field in Firestore
    private boolean isActive  = true;

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

    public boolean isExpired() {
        if (BAN_PERMANENT.equals(banType) || expiryDate == null) return false;
        return Timestamp.now().compareTo(expiryDate) >= 0;
    }

    public boolean isEffectivelyActive() {
        return isActive && !isExpired();
    }

    public String getEntryId()      { return entryId; }
    public String getEntityId()     { return entityId; }
    public String getEntityType()   { return entityType; }
    public String getEntityName()   { return entityName; }
    public String getReason()       { return reason; }
    public String getBanType()      { return banType; }
    public Timestamp getExpiryDate(){ return expiryDate; }
    public String getAddedBy()      { return addedBy; }
    public Timestamp getAddedAt()   { return addedAt; }

    @PropertyName("active")
    public boolean isActive()       { return isActive; }

    public void setEntryId(String entryId)          { this.entryId   = entryId; }
    public void setEntityId(String entityId)         { this.entityId  = entityId; }
    public void setEntityType(String entityType)     { this.entityType = entityType; }
    public void setEntityName(String entityName)     { this.entityName = entityName; }
    public void setReason(String reason)             { this.reason    = reason; }
    public void setBanType(String banType)           { this.banType   = banType; }
    public void setExpiryDate(Timestamp expiryDate)  { this.expiryDate = expiryDate; }
    public void setAddedBy(String addedBy)           { this.addedBy   = addedBy; }
    public void setAddedAt(Timestamp addedAt)        { this.addedAt   = addedAt; }

    @PropertyName("active")
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
}
