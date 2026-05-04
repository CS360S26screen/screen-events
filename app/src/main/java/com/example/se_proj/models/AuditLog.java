package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Domain model representing an immutable audit log entry in the Campus Gate Access System.
 *
 * <p>Each instance maps to a document in the Firestore {@code access_logs} collection.
 * Audit logs are created by guards (entry/exit/denied events) and admins (approve/reject
 * actions) to maintain a tamper-evident record of all gate activity.</p>
 *
 * <p>Logs are append-only; Firestore security rules prohibit updates and deletes on the
 * {@code access_logs} collection to guarantee immutability.</p>
 *
 * <h3>Outstanding Issues</h3>
 * The {@code action} field is still free-form text; replacing it with a constrained enum or
 * sealed hierarchy would reduce typo-driven filtering/reporting bugs.
 *
 * <p><b>Design pattern:</b> Data Transfer Object (DTO) / Firestore document model.
 * The class stores structured data for Firebase serialization while keeping
 * business decisions in the rules and service layers.</p>
 */
public class AuditLog {

    /** Firestore document ID, auto-populated by {@link DocumentId}. */
    @DocumentId
    private String id = "";

    /** Display name of the visitor involved in the event. */
    private String visitorName = "";

    /** 13-digit CNIC of the visitor (used for search/filtering). */
    private String visitorCNIC = "";

    /** Roll number or faculty ID of the campus host linked to the visit. */
    private String hostId = "";

    /**
     * Event type: {@code "Entry"}, {@code "Exit"}, {@code "Denied"},
     * {@code "ADMIN_APPROVED"}, {@code "ADMIN_REJECTED"}, or {@code "OVERSTAYING"}.
     */
    private String action = "";

    /**
     * Additional context (e.g. denial reason, "Supervisor Override").
     * Empty string when not applicable.
     */
    private String reason = "";

    /** Firebase Auth UID of the guard or admin who recorded the event. */
    private String creatorId = "";

    /** Server-side timestamp of when the event was logged. */
    private Timestamp timestamp = Timestamp.now();

    /** Required no-arg constructor for Firestore deserialization. */
    public AuditLog() {}

    /**
     * Full constructor for programmatic creation of audit log entries.
     *
     * @param id          Firestore document ID
     * @param visitorName Display name of the visitor
     * @param visitorCNIC 13-digit CNIC of the visitor
     * @param hostId      Roll number or faculty ID of the host
     * @param action      Event type string
     * @param reason      Additional context, or empty string
     * @param creatorId   Firebase Auth UID of the recording user
     * @param timestamp   Server-side event timestamp
     */
    public AuditLog(String id, String visitorName, String visitorCNIC, String hostId,
                    String action, String reason, String creatorId, Timestamp timestamp) {
        this.id = id;
        this.visitorName = visitorName;
        this.visitorCNIC = visitorCNIC;
        this.hostId = hostId;
        this.action = action;
        this.reason = reason;
        this.creatorId = creatorId;
        this.timestamp = timestamp;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Returns the Firestore document ID for this audit log.
     *
     * @return the current audit log document ID.
     */
    public String getId() { return id; }
    /**
     * Returns the visitor name.
     * @return the current visitor name
     */
    public String getVisitorName() { return visitorName; }
    /**
     * Returns the visitor cnic.
     * @return the current visitor cnic
     */
    public String getVisitorCNIC() { return visitorCNIC; }
    /**
     * Returns the host id.
     * @return the current host id
     */
    public String getHostId() { return hostId; }
    /**
     * Returns the action.
     * @return the current action
     */
    public String getAction() { return action; }
    /**
     * Returns the reason.
     * @return the current reason
     */
    public String getReason() { return reason; }
    /**
     * Returns the creator id.
     * @return the current creator id
     */
    public String getCreatorId() { return creatorId; }
    /**
     * Returns the timestamp.
     * @return the current timestamp
     */
    public Timestamp getTimestamp() { return timestamp; }

    // -------------------------------------------------------------------------
    // Setters (required for Firestore deserialization)
    // -------------------------------------------------------------------------

    /**
     * Sets the id.
     * @param id the value to assign to {@code id}
     */
    public void setId(String id) { this.id = id; }
    /**
     * Sets the visitor name.
     * @param visitorName the value to assign to {@code visitorName}
     */
    public void setVisitorName(String visitorName) { this.visitorName = visitorName; }
    /**
     * Sets the visitor cnic.
     * @param visitorCNIC the value to assign to {@code visitorCNIC}
     */
    public void setVisitorCNIC(String visitorCNIC) { this.visitorCNIC = visitorCNIC; }
    /**
     * Sets the host id.
     * @param hostId the value to assign to {@code hostId}
     */
    public void setHostId(String hostId) { this.hostId = hostId; }
    /**
     * Sets the action.
     * @param action the value to assign to {@code action}
     */
    public void setAction(String action) { this.action = action; }
    /**
     * Sets the reason.
     * @param reason the value to assign to {@code reason}
     */
    public void setReason(String reason) { this.reason = reason; }
    /**
     * Sets the creator id.
     * @param creatorId the value to assign to {@code creatorId}
     */
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    /**
     * Sets the timestamp.
     * @param timestamp the value to assign to {@code timestamp}
     */
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    /**
     * Compares this model with another object for value equality.
     * @param o the object to compare with this instance
     * @return {@code true} when the supplied object represents the same model data
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLog)) return false;
        AuditLog that = (AuditLog) o;
        return Objects.equals(id, that.id)
                && Objects.equals(visitorName, that.visitorName)
                && Objects.equals(visitorCNIC, that.visitorCNIC)
                && Objects.equals(hostId, that.hostId)
                && Objects.equals(action, that.action)
                && Objects.equals(reason, that.reason)
                && Objects.equals(creatorId, that.creatorId)
                && Objects.equals(timestamp, that.timestamp);
    }

    /**
     * Computes the hash code for the identifying fields of this model.
     * @return the hash code for this model
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, visitorName, visitorCNIC, hostId, action, reason, creatorId, timestamp);
    }

    /**
     * Returns a concise diagnostic string for this model.
     * @return a readable summary of this model
     */
    @Override
    public String toString() {
        return "AuditLog{" +
                "id='" + id + '\'' +
                ", visitorName='" + visitorName + '\'' +
                ", visitorCNIC='" + visitorCNIC + '\'' +
                ", hostId='" + hostId + '\'' +
                ", action='" + action + '\'' +
                ", reason='" + reason + '\'' +
                ", creatorId='" + creatorId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
