package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Domain model for a zone-access attempt log in the Campus Gate Access System (US18).
 *
 * <p>Maps to the Firestore {@code zone_access_logs} collection. Every scan at a gate —
 * whether the person was allowed in, denied, or exited — produces one document here.
 * Complements the existing {@link AuditLog} in {@code access_logs}: ZoneAccessLog stores
 * richer machine-readable fields (zone, outcome) while AuditLog preserves the human-facing
 * narrative needed by the existing admin audit view.</p>
 *
 * <h3>Firestore document structure</h3>
 * <pre>
 * zone_access_logs/{logId}
 *   entityId      : "4201234567890"          // CNIC or vehicle plate
 *   entityType    : "person" | "vehicle"
 *   entityName    : "Jane Smith"
 *   zoneId        : "main_gate" | "block_a" | "block_b" | "out_gate"
 *   action        : "ENTRY" | "EXIT" | "DENIED"
 *   outcome       : "SUCCESS" | "FAILURE"
 *   failureReason : ""  | "Blacklisted" | "Expired Window" | …
 *   guardId       : "firebase-uid"
 *   requestId     : "visitor_request doc ID" | ""
 *   timestamp     : Timestamp
 * </pre>
 *
 * <h3>Composite indexes required</h3>
 * <ul>
 *   <li>{@code entityId ASC, timestamp DESC} — per-person access history</li>
 *   <li>{@code zoneId ASC, timestamp DESC} — per-gate activity feed</li>
 *   <li>{@code outcome ASC, timestamp DESC} — failure analytics dashboard</li>
 * </ul>
 */
public class ZoneAccessLog {

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";
    public static final String ACTION_ENTRY  = "ENTRY";
    public static final String ACTION_EXIT   = "EXIT";
    public static final String ACTION_DENIED = "DENIED";

    @DocumentId
    private String logId        = "";
    private String entityId     = "";
    private String entityType   = "";
    private String entityName   = "";
    private String zoneId       = "";
    private String action       = "";
    private String outcome      = "";
    private String failureReason = "";
    private String guardId      = "";
    private String requestId    = "";
    private Timestamp timestamp = Timestamp.now();

    /** Required no-arg constructor for Firestore deserialization. */
    public ZoneAccessLog() {}

    public ZoneAccessLog(String entityId, String entityType, String entityName,
                         String zoneId, String action, String outcome,
                         String failureReason, String guardId, String requestId) {
        this.entityId      = entityId;
        this.entityType    = entityType;
        this.entityName    = entityName;
        this.zoneId        = zoneId;
        this.action        = action;
        this.outcome       = outcome;
        this.failureReason = failureReason;
        this.guardId       = guardId;
        this.requestId     = requestId;
        this.timestamp     = Timestamp.now();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getLogId()         { return logId; }
    public String getEntityId()      { return entityId; }
    public String getEntityType()    { return entityType; }
    public String getEntityName()    { return entityName; }
    public String getZoneId()        { return zoneId; }
    public String getAction()        { return action; }
    public String getOutcome()       { return outcome; }
    public String getFailureReason() { return failureReason; }
    public String getGuardId()       { return guardId; }
    public String getRequestId()     { return requestId; }
    public Timestamp getTimestamp()  { return timestamp; }

    // -------------------------------------------------------------------------
    // Setters (required for Firestore deserialization)
    // -------------------------------------------------------------------------

    public void setLogId(String logId)               { this.logId         = logId; }
    public void setEntityId(String entityId)          { this.entityId      = entityId; }
    public void setEntityType(String entityType)      { this.entityType    = entityType; }
    public void setEntityName(String entityName)      { this.entityName    = entityName; }
    public void setZoneId(String zoneId)              { this.zoneId        = zoneId; }
    public void setAction(String action)              { this.action        = action; }
    public void setOutcome(String outcome)            { this.outcome       = outcome; }
    public void setFailureReason(String failureReason){ this.failureReason = failureReason; }
    public void setGuardId(String guardId)            { this.guardId       = guardId; }
    public void setRequestId(String requestId)        { this.requestId     = requestId; }
    public void setTimestamp(Timestamp timestamp)     { this.timestamp     = timestamp; }
 * Firestore model for a single wing/lab access attempt log entry.
 *
 * <p>Documents live in the {@code zone_access_logs} collection. Every access attempt through
 * the self-service wing scanner — whether allowed or denied — is appended here automatically.
 * No guard involvement; this collection is append-only.</p>
 */
public class ZoneAccessLog {

    @DocumentId
    private String logId = "";

    /** Roll number of the student who attempted access. */
    private String studentRollNo = "";

    /** Wing where the access attempt occurred. */
    private String wing = "";

    /** {@code "ALLOWED"} or {@code "DENIED"}. */
    private String result = "";

    /** Human-readable reason (e.g. "Access granted", "No permission", "Expired"). */
    private String reason = "";

    private Timestamp timestamp = Timestamp.now();

    public ZoneAccessLog() {}

    public ZoneAccessLog(String logId, String studentRollNo, String wing,
                          String result, String reason, Timestamp timestamp) {
        this.logId = logId;
        this.studentRollNo = studentRollNo;
        this.wing = wing;
        this.result = result;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getLogId() { return logId; }
    public String getStudentRollNo() { return studentRollNo; }
    public String getWing() { return wing; }
    public String getResult() { return result; }
    public String getReason() { return reason; }
    public Timestamp getTimestamp() { return timestamp; }

    public void setLogId(String logId) { this.logId = logId; }
    public void setStudentRollNo(String studentRollNo) { this.studentRollNo = studentRollNo; }
    public void setWing(String wing) { this.wing = wing; }
    public void setResult(String result) { this.result = result; }
    public void setReason(String reason) { this.reason = reason; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZoneAccessLog)) return false;
        ZoneAccessLog that = (ZoneAccessLog) o;
        return Objects.equals(logId, that.logId);
    }

    @Override
    public int hashCode() { return Objects.hash(logId); }

    @Override
    public String toString() {
        return "ZoneAccessLog{entityId='" + entityId + "', zone='" + zoneId
                + "', action='" + action + "', outcome='" + outcome + "'}";
        return Objects.equals(logId, that.logId)
                && Objects.equals(studentRollNo, that.studentRollNo)
                && Objects.equals(wing, that.wing)
                && Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logId, studentRollNo, wing, result);
    }

    @Override
    public String toString() {
        return "ZoneAccessLog{id='" + logId + "', student='" + studentRollNo
                + "', wing='" + wing + "', result='" + result + "'}";
    }
}
