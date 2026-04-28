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

    public String getId() { return id; }
    public String getVisitorName() { return visitorName; }
    public String getVisitorCNIC() { return visitorCNIC; }
    public String getHostId() { return hostId; }
    public String getAction() { return action; }
    public String getReason() { return reason; }
    public String getCreatorId() { return creatorId; }
    public Timestamp getTimestamp() { return timestamp; }

    // -------------------------------------------------------------------------
    // Setters (required for Firestore deserialization)
    // -------------------------------------------------------------------------

    public void setId(String id) { this.id = id; }
    public void setVisitorName(String visitorName) { this.visitorName = visitorName; }
    public void setVisitorCNIC(String visitorCNIC) { this.visitorCNIC = visitorCNIC; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public void setAction(String action) { this.action = action; }
    public void setReason(String reason) { this.reason = reason; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

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

    @Override
    public int hashCode() {
        return Objects.hash(id, visitorName, visitorCNIC, hostId, action, reason, creatorId, timestamp);
    }

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
