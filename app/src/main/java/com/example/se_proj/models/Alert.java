package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Domain model for a guard alert in the Campus Gate Access System (US20).
 *
 * <p>Maps to the Firestore {@code alerts} collection. Alerts are written whenever a
 * blacklisted entity is scanned, or a delivery rider exceeds the allowed window.
 * Guards subscribe to real-time snapshots of unacknowledged alerts so the UI updates
 * instantly without polling.</p>
 *
 * <h3>Firestore document structure</h3>
 * <pre>
 * alerts/{alertId}
 *   alertType       : "BLACKLISTED_PERSON" | "BLACKLISTED_VEHICLE"
 *                   | "DELIVERY_OVERSTAY"  | "OVERSTAY"
 *   priority        : "HIGH" | "MEDIUM"
 *   entityId        : "4201234567890"        // CNIC, plate, or delivery doc ID
 *   entityName      : "John Doe"
 *   message         : "HIGH PRIORITY: Blacklisted person detected at main_gate …"
 *   zoneId          : "main_gate"
 *   guardId         : "firebase-uid"
 *   timestamp       : Timestamp
 *   isAcknowledged  : false
 *   acknowledgedBy  : ""
 *   acknowledgedAt  : null | Timestamp
 * </pre>
 *
 * <h3>Composite index required</h3>
 * {@code isAcknowledged ASC, timestamp DESC} — used by AlertManager real-time listener.
 *
 * <p><b>Design pattern:</b> Data Transfer Object (DTO) / Firestore document model.
 * The class stores structured data for Firebase serialization while keeping
 * business decisions in the rules and service layers.</p>
 */
public class Alert {

    public static final String TYPE_BLACKLISTED_PERSON  = "BLACKLISTED_PERSON";
    public static final String TYPE_BLACKLISTED_VEHICLE = "BLACKLISTED_VEHICLE";
    public static final String TYPE_OVERSTAY            = "OVERSTAY";
    public static final String TYPE_DELIVERY_OVERSTAY   = "DELIVERY_OVERSTAY";
    public static final String PRIORITY_HIGH   = "HIGH";
    public static final String PRIORITY_MEDIUM = "MEDIUM";

    @DocumentId
    private String alertId      = "";
    private String alertType    = "";
    private String priority     = PRIORITY_HIGH;
    private String entityId     = "";
    private String entityName   = "";
    private String message      = "";
    private String zoneId       = "";
    private String guardId      = "";
    private Timestamp timestamp = Timestamp.now();
    private boolean isAcknowledged = false;
    private String acknowledgedBy  = "";
    private Timestamp acknowledgedAt = null;

    /** Required no-arg constructor for Firestore deserialization. */
    public Alert() {}

    /**
     * Creates a populated {@code Alert} instance.
     * @param alertType the value to assign to {@code alertType}
     * @param priority the value to assign to {@code priority}
     * @param entityId the value to assign to {@code entityId}
     * @param entityName the value to assign to {@code entityName}
     * @param message the value to assign to {@code message}
     * @param zoneId the value to assign to {@code zoneId}
     * @param guardId the value to assign to {@code guardId}
     */
    public Alert(String alertType, String priority, String entityId, String entityName,
                 String message, String zoneId, String guardId) {
        this.alertType  = alertType;
        this.priority   = priority;
        this.entityId   = entityId;
        this.entityName = entityName;
        this.message    = message;
        this.zoneId     = zoneId;
        this.guardId    = guardId;
        this.timestamp  = Timestamp.now();
        this.isAcknowledged = false;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Returns the alert id.
     * @return the current alert id
     */
    public String getAlertId()          { return alertId; }
    /**
     * Returns the alert type.
     * @return the current alert type
     */
    public String getAlertType()        { return alertType; }
    /**
     * Returns the priority.
     * @return the current priority
     */
    public String getPriority()         { return priority; }
    /**
     * Returns the entity id.
     * @return the current entity id
     */
    public String getEntityId()         { return entityId; }
    /**
     * Returns the entity name.
     * @return the current entity name
     */
    public String getEntityName()       { return entityName; }
    /**
     * Returns the message.
     * @return the current message
     */
    public String getMessage()          { return message; }
    /**
     * Returns the zone id.
     * @return the current zone id
     */
    public String getZoneId()           { return zoneId; }
    /**
     * Returns the guard id.
     * @return the current guard id
     */
    public String getGuardId()          { return guardId; }
    /**
     * Returns the timestamp.
     * @return the current timestamp
     */
    public Timestamp getTimestamp()     { return timestamp; }
    /**
     * Returns the acknowledged.
     * @return the current acknowledged
     */
    public boolean isAcknowledged()     { return isAcknowledged; }
    /**
     * Returns the acknowledged by.
     * @return the current acknowledged by
     */
    public String getAcknowledgedBy()   { return acknowledgedBy; }
    /**
     * Returns the acknowledged at.
     * @return the current acknowledged at
     */
    public Timestamp getAcknowledgedAt(){ return acknowledgedAt; }

    // -------------------------------------------------------------------------
    // Setters (required for Firestore deserialization)
    // -------------------------------------------------------------------------

    /**
     * Sets the alert id.
     * @param alertId the value to assign to {@code alertId}
     */
    public void setAlertId(String alertId)                 { this.alertId       = alertId; }
    /**
     * Sets the alert type.
     * @param alertType the value to assign to {@code alertType}
     */
    public void setAlertType(String alertType)             { this.alertType     = alertType; }
    /**
     * Sets the priority.
     * @param priority the value to assign to {@code priority}
     */
    public void setPriority(String priority)               { this.priority      = priority; }
    /**
     * Sets the entity id.
     * @param entityId the value to assign to {@code entityId}
     */
    public void setEntityId(String entityId)               { this.entityId      = entityId; }
    /**
     * Sets the entity name.
     * @param entityName the value to assign to {@code entityName}
     */
    public void setEntityName(String entityName)           { this.entityName    = entityName; }
    /**
     * Sets the message.
     * @param message the value to assign to {@code message}
     */
    public void setMessage(String message)                 { this.message       = message; }
    /**
     * Sets the zone id.
     * @param zoneId the value to assign to {@code zoneId}
     */
    public void setZoneId(String zoneId)                   { this.zoneId        = zoneId; }
    /**
     * Sets the guard id.
     * @param guardId the value to assign to {@code guardId}
     */
    public void setGuardId(String guardId)                 { this.guardId       = guardId; }
    /**
     * Sets the timestamp.
     * @param timestamp the value to assign to {@code timestamp}
     */
    public void setTimestamp(Timestamp timestamp)          { this.timestamp     = timestamp; }
    /**
     * Sets the acknowledged.
     * @param acknowledged the value to assign to {@code acknowledged}
     */
    public void setAcknowledged(boolean acknowledged)      { isAcknowledged     = acknowledged; }
    /**
     * Sets the acknowledged by.
     * @param acknowledgedBy the value to assign to {@code acknowledgedBy}
     */
    public void setAcknowledgedBy(String acknowledgedBy)   { this.acknowledgedBy  = acknowledgedBy; }
    /**
     * Sets the acknowledged at.
     * @param acknowledgedAt the value to assign to {@code acknowledgedAt}
     */
    public void setAcknowledgedAt(Timestamp acknowledgedAt){ this.acknowledgedAt = acknowledgedAt; }

    /**
     * Compares this model with another object for value equality.
     * @param o the object to compare with this instance
     * @return {@code true} when the supplied object represents the same model data
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alert)) return false;
        Alert that = (Alert) o;
        return Objects.equals(alertId, that.alertId);
    }

    /**
     * Computes the hash code for the identifying fields of this model.
     * @return the hash code for this model
     */
    @Override
    public int hashCode() { return Objects.hash(alertId); }

    /**
     * Returns a concise diagnostic string for this model.
     * @return a readable summary of this model
     */
    @Override
    public String toString() {
        return "Alert{alertType='" + alertType + "', priority='" + priority
                + "', entityId='" + entityId + "', acknowledged=" + isAcknowledged + "}";
    }
}
