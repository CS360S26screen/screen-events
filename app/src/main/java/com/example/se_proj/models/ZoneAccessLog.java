package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Firestore model for zone and wing access attempts.
 *
 * <p>The guard dashboard writes structured access records using entity/zone/action/outcome
 * fields. The wing scanner uses studentRollNo/wing/result/reason fields. Both shapes map
 * to the same {@code zone_access_logs} collection, so this model keeps both sets of fields
 * in one class and mirrors constructor values for compatibility.</p>
 */
public class ZoneAccessLog {

    public static final String OUTCOME_SUCCESS = "SUCCESS";
    public static final String OUTCOME_FAILURE = "FAILURE";
    public static final String ACTION_ENTRY = "ENTRY";
    public static final String ACTION_EXIT = "EXIT";
    public static final String ACTION_DENIED = "DENIED";

    @DocumentId
    private String logId = "";

    private String entityId = "";
    private String entityType = "";
    private String entityName = "";
    private String zoneId = "";
    private String action = "";
    private String outcome = "";
    private String failureReason = "";
    private String guardId = "";
    private String requestId = "";

    private String studentRollNo = "";
    private String wing = "";
    private String result = "";
    private String reason = "";

    private Timestamp timestamp = Timestamp.now();

    /** Required no-arg constructor for Firestore deserialization. */
    public ZoneAccessLog() {}

    public ZoneAccessLog(String entityId, String entityType, String entityName,
                         String zoneId, String action, String outcome,
                         String failureReason, String guardId, String requestId) {
        this.entityId = valueOrEmpty(entityId);
        this.entityType = valueOrEmpty(entityType);
        this.entityName = valueOrEmpty(entityName);
        this.zoneId = valueOrEmpty(zoneId);
        this.action = valueOrEmpty(action);
        this.outcome = valueOrEmpty(outcome);
        this.failureReason = valueOrEmpty(failureReason);
        this.guardId = valueOrEmpty(guardId);
        this.requestId = valueOrEmpty(requestId);
        this.timestamp = Timestamp.now();

        this.studentRollNo = this.entityId;
        this.wing = this.zoneId;
        this.result = OUTCOME_SUCCESS.equals(this.outcome) ? "ALLOWED" : "DENIED";
        this.reason = this.failureReason;
    }

    public ZoneAccessLog(String logId, String studentRollNo, String wing,
                         String result, String reason, Timestamp timestamp) {
        this.logId = valueOrEmpty(logId);
        this.studentRollNo = valueOrEmpty(studentRollNo);
        this.wing = valueOrEmpty(wing);
        this.result = valueOrEmpty(result);
        this.reason = valueOrEmpty(reason);
        this.timestamp = timestamp != null ? timestamp : Timestamp.now();

        this.entityId = this.studentRollNo;
        this.entityType = "person";
        this.entityName = this.studentRollNo;
        this.zoneId = this.wing;
        this.action = "ALLOWED".equals(this.result) ? ACTION_ENTRY : ACTION_DENIED;
        this.outcome = "ALLOWED".equals(this.result) ? OUTCOME_SUCCESS : OUTCOME_FAILURE;
        this.failureReason = this.reason;
    }

    public String getLogId() { return logId; }
    public String getEntityId() { return entityId; }
    public String getEntityType() { return entityType; }
    public String getEntityName() { return entityName; }
    public String getZoneId() { return zoneId; }
    public String getAction() { return action; }
    public String getOutcome() { return outcome; }
    public String getFailureReason() { return failureReason; }
    public String getGuardId() { return guardId; }
    public String getRequestId() { return requestId; }
    public String getStudentRollNo() { return studentRollNo.isEmpty() ? entityId : studentRollNo; }
    public String getWing() { return wing.isEmpty() ? zoneId : wing; }
    public String getResult() {
        if (!result.isEmpty()) return result;
        return OUTCOME_SUCCESS.equals(outcome) ? "ALLOWED" : "DENIED";
    }
    public String getReason() { return reason.isEmpty() ? failureReason : reason; }
    public Timestamp getTimestamp() { return timestamp; }

    public void setLogId(String logId) { this.logId = valueOrEmpty(logId); }
    public void setEntityId(String entityId) { this.entityId = valueOrEmpty(entityId); }
    public void setEntityType(String entityType) { this.entityType = valueOrEmpty(entityType); }
    public void setEntityName(String entityName) { this.entityName = valueOrEmpty(entityName); }
    public void setZoneId(String zoneId) { this.zoneId = valueOrEmpty(zoneId); }
    public void setAction(String action) { this.action = valueOrEmpty(action); }
    public void setOutcome(String outcome) { this.outcome = valueOrEmpty(outcome); }
    public void setFailureReason(String failureReason) { this.failureReason = valueOrEmpty(failureReason); }
    public void setGuardId(String guardId) { this.guardId = valueOrEmpty(guardId); }
    public void setRequestId(String requestId) { this.requestId = valueOrEmpty(requestId); }
    public void setStudentRollNo(String studentRollNo) { this.studentRollNo = valueOrEmpty(studentRollNo); }
    public void setWing(String wing) { this.wing = valueOrEmpty(wing); }
    public void setResult(String result) { this.result = valueOrEmpty(result); }
    public void setReason(String reason) { this.reason = valueOrEmpty(reason); }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZoneAccessLog)) return false;
        ZoneAccessLog that = (ZoneAccessLog) o;
        return Objects.equals(logId, that.logId)
                && Objects.equals(entityId, that.entityId)
                && Objects.equals(zoneId, that.zoneId)
                && Objects.equals(action, that.action)
                && Objects.equals(outcome, that.outcome)
                && Objects.equals(studentRollNo, that.studentRollNo)
                && Objects.equals(wing, that.wing)
                && Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logId, entityId, zoneId, action, outcome, studentRollNo, wing, result);
    }

    @Override
    public String toString() {
        return "ZoneAccessLog{"
                + "logId='" + logId + '\''
                + ", entityId='" + getEntityId() + '\''
                + ", zoneId='" + getZoneId() + '\''
                + ", action='" + getAction() + '\''
                + ", outcome='" + getOutcome() + '\''
                + ", studentRollNo='" + getStudentRollNo() + '\''
                + ", wing='" + getWing() + '\''
                + ", result='" + getResult() + '\''
                + '}';
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
