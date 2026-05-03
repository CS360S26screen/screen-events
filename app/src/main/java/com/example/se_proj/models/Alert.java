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

    public String getAlertId()          { return alertId; }
    public String getAlertType()        { return alertType; }
    public String getPriority()         { return priority; }
    public String getEntityId()         { return entityId; }
    public String getEntityName()       { return entityName; }
    public String getMessage()          { return message; }
    public String getZoneId()           { return zoneId; }
    public String getGuardId()          { return guardId; }
    public Timestamp getTimestamp()     { return timestamp; }
    public boolean isAcknowledged()     { return isAcknowledged; }
    public String getAcknowledgedBy()   { return acknowledgedBy; }
    public Timestamp getAcknowledgedAt(){ return acknowledgedAt; }

    // -------------------------------------------------------------------------
    // Setters (required for Firestore deserialization)
    // -------------------------------------------------------------------------

    public void setAlertId(String alertId)                 { this.alertId       = alertId; }
    public void setAlertType(String alertType)             { this.alertType     = alertType; }
    public void setPriority(String priority)               { this.priority      = priority; }
    public void setEntityId(String entityId)               { this.entityId      = entityId; }
    public void setEntityName(String entityName)           { this.entityName    = entityName; }
    public void setMessage(String message)                 { this.message       = message; }
    public void setZoneId(String zoneId)                   { this.zoneId        = zoneId; }
    public void setGuardId(String guardId)                 { this.guardId       = guardId; }
    public void setTimestamp(Timestamp timestamp)          { this.timestamp     = timestamp; }
    public void setAcknowledged(boolean acknowledged)      { isAcknowledged     = acknowledged; }
    public void setAcknowledgedBy(String acknowledgedBy)   { this.acknowledgedBy  = acknowledgedBy; }
    public void setAcknowledgedAt(Timestamp acknowledgedAt){ this.acknowledgedAt = acknowledgedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alert)) return false;
        Alert that = (Alert) o;
        return Objects.equals(alertId, that.alertId);
    }

    @Override
    public int hashCode() { return Objects.hash(alertId); }

    @Override
    public String toString() {
        return "Alert{alertType='" + alertType + "', priority='" + priority
                + "', entityId='" + entityId + "', acknowledged=" + isAcknowledged + "}";
    }
}
