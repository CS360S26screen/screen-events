package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;

import java.util.Objects;

/**
 * Domain model for a blacklist entry in the Campus Gate Access System.
 *
 * <p><b>Design pattern:</b> Data Transfer Object (DTO) / Firestore document model.
 * The class stores structured data for Firebase serialization while keeping
 * business decisions in the rules and service layers.</p>
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

    /**
     * Creates an empty {@code BlacklistEntry} instance for Firestore deserialization.
     */
    public BlacklistEntry() {}

    /**
     * Creates a populated {@code BlacklistEntry} instance.
     * @param entityId the value to assign to {@code entityId}
     * @param entityType the value to assign to {@code entityType}
     * @param entityName the value to assign to {@code entityName}
     * @param reason the value to assign to {@code reason}
     * @param banType the value to assign to {@code banType}
     * @param expiryDate the value to assign to {@code expiryDate}
     * @param addedBy the value to assign to {@code addedBy}
     */
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

    /**
     * Determines whether this temporary ban has passed its configured expiry timestamp.
     *
     * @return {@code true} when the ban is temporary and the expiry time has elapsed.
     */
    public boolean isExpired() {
        if (BAN_PERMANENT.equals(banType) || expiryDate == null) return false;
        return Timestamp.now().compareTo(expiryDate) >= 0;
    }

    /**
     * Determines whether this entry should currently block access.
     *
     * @return {@code true} when the entry is marked active and has not expired.
     */
    public boolean isEffectivelyActive() {
        return isActive && !isExpired();
    }

    /**
     * Returns the entry id.
     * @return the current entry id
     */
    public String getEntryId()      { return entryId; }
    /**
     * Returns the entity id.
     * @return the current entity id
     */
    public String getEntityId()     { return entityId; }
    /**
     * Returns the entity type.
     * @return the current entity type
     */
    public String getEntityType()   { return entityType; }
    /**
     * Returns the entity name.
     * @return the current entity name
     */
    public String getEntityName()   { return entityName; }
    /**
     * Returns the reason.
     * @return the current reason
     */
    public String getReason()       { return reason; }
    /**
     * Returns the ban type.
     * @return the current ban type
     */
    public String getBanType()      { return banType; }
    /**
     * Returns the expiry date.
     * @return the current expiry date
     */
    public Timestamp getExpiryDate(){ return expiryDate; }
    /**
     * Returns the added by.
     * @return the current added by
     */
    public String getAddedBy()      { return addedBy; }
    /**
     * Returns the added at.
     * @return the current added at
     */
    public Timestamp getAddedAt()   { return addedAt; }

    /**
     * Returns whether Firestore marks this blacklist entry as active.
     *
     * @return {@code true} when the entry has not been soft-removed.
     */
    @PropertyName("active")
    public boolean isActive()       { return isActive; }

    /**
     * Sets the entry id.
     * @param entryId the value to assign to {@code entryId}
     */
    public void setEntryId(String entryId)          { this.entryId   = entryId; }
    /**
     * Sets the entity id.
     * @param entityId the value to assign to {@code entityId}
     */
    public void setEntityId(String entityId)         { this.entityId  = entityId; }
    /**
     * Sets the entity type.
     * @param entityType the value to assign to {@code entityType}
     */
    public void setEntityType(String entityType)     { this.entityType = entityType; }
    /**
     * Sets the entity name.
     * @param entityName the value to assign to {@code entityName}
     */
    public void setEntityName(String entityName)     { this.entityName = entityName; }
    /**
     * Sets the reason.
     * @param reason the value to assign to {@code reason}
     */
    public void setReason(String reason)             { this.reason    = reason; }
    /**
     * Sets the ban type.
     * @param banType the value to assign to {@code banType}
     */
    public void setBanType(String banType)           { this.banType   = banType; }
    /**
     * Sets the expiry date.
     * @param expiryDate the value to assign to {@code expiryDate}
     */
    public void setExpiryDate(Timestamp expiryDate)  { this.expiryDate = expiryDate; }
    /**
     * Sets the added by.
     * @param addedBy the value to assign to {@code addedBy}
     */
    public void setAddedBy(String addedBy)           { this.addedBy   = addedBy; }
    /**
     * Sets the added at.
     * @param addedAt the value to assign to {@code addedAt}
     */
    public void setAddedAt(Timestamp addedAt)        { this.addedAt   = addedAt; }

    /**
     * Sets the active.
     * @param active the value to assign to {@code active}
     */
    @PropertyName("active")
    public void setActive(boolean active)            { isActive       = active; }

    /**
     * Compares this model with another object for value equality.
     * @param o the object to compare with this instance
     * @return {@code true} when the supplied object represents the same model data
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlacklistEntry)) return false;
        BlacklistEntry that = (BlacklistEntry) o;
        return Objects.equals(entryId, that.entryId)
                && Objects.equals(entityId, that.entityId);
    }

    /**
     * Computes the hash code for the identifying fields of this model.
     * @return the hash code for this model
     */
    @Override
    public int hashCode() { return Objects.hash(entryId, entityId); }
}
