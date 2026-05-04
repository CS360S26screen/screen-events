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
 *
 * <p><b>Design pattern:</b> Data Transfer Object (DTO) / Firestore document model.
 * The class stores structured data for Firebase serialization while keeping
 * business decisions in the rules and service layers.</p>
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

    /**
     * Creates a populated {@code ZoneAccessLog} instance.
     * @param entityId the value to assign to {@code entityId}
     * @param entityType the value to assign to {@code entityType}
     * @param entityName the value to assign to {@code entityName}
     * @param zoneId the value to assign to {@code zoneId}
     * @param action the value to assign to {@code action}
     * @param outcome the value to assign to {@code outcome}
     * @param failureReason the value to assign to {@code failureReason}
     * @param guardId the value to assign to {@code guardId}
     * @param requestId the value to assign to {@code requestId}
     */
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

    /**
     * Creates a populated {@code ZoneAccessLog} instance.
     * @param logId the value to assign to {@code logId}
     * @param studentRollNo the value to assign to {@code studentRollNo}
     * @param wing the value to assign to {@code wing}
     * @param result the value to assign to {@code result}
     * @param reason the value to assign to {@code reason}
     * @param timestamp the value to assign to {@code timestamp}
     */
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

    /**
     * Returns the log id.
     * @return the current log id
     */
    public String getLogId() { return logId; }
    /**
     * Returns the entity id.
     * @return the current entity id
     */
    public String getEntityId() { return entityId; }
    /**
     * Returns the entity type.
     * @return the current entity type
     */
    public String getEntityType() { return entityType; }
    /**
     * Returns the entity name.
     * @return the current entity name
     */
    public String getEntityName() { return entityName; }
    /**
     * Returns the zone id.
     * @return the current zone id
     */
    public String getZoneId() { return zoneId; }
    /**
     * Returns the action.
     * @return the current action
     */
    public String getAction() { return action; }
    /**
     * Returns the outcome.
     * @return the current outcome
     */
    public String getOutcome() { return outcome; }
    /**
     * Returns the failure reason.
     * @return the current failure reason
     */
    public String getFailureReason() { return failureReason; }
    /**
     * Returns the guard id.
     * @return the current guard id
     */
    public String getGuardId() { return guardId; }
    /**
     * Returns the request id.
     * @return the current request id
     */
    public String getRequestId() { return requestId; }
    /**
     * Returns the student roll no.
     * @return the current student roll no
     */
    public String getStudentRollNo() { return studentRollNo.isEmpty() ? entityId : studentRollNo; }
    /**
     * Returns the wing.
     * @return the current wing
     */
    public String getWing() { return wing.isEmpty() ? zoneId : wing; }
    /**
     * Returns the result.
     * @return the current result
     */
    public String getResult() {
        if (!result.isEmpty()) return result;
        return OUTCOME_SUCCESS.equals(outcome) ? "ALLOWED" : "DENIED";
    }
    /**
     * Returns the reason.
     * @return the current reason
     */
    public String getReason() { return reason.isEmpty() ? failureReason : reason; }
    /**
     * Returns the timestamp.
     * @return the current timestamp
     */
    public Timestamp getTimestamp() { return timestamp; }

    /**
     * Sets the log id.
     * @param logId the value to assign to {@code logId}
     */
    public void setLogId(String logId) { this.logId = valueOrEmpty(logId); }
    /**
     * Sets the entity id.
     * @param entityId the value to assign to {@code entityId}
     */
    public void setEntityId(String entityId) { this.entityId = valueOrEmpty(entityId); }
    /**
     * Sets the entity type.
     * @param entityType the value to assign to {@code entityType}
     */
    public void setEntityType(String entityType) { this.entityType = valueOrEmpty(entityType); }
    /**
     * Sets the entity name.
     * @param entityName the value to assign to {@code entityName}
     */
    public void setEntityName(String entityName) { this.entityName = valueOrEmpty(entityName); }
    /**
     * Sets the zone id.
     * @param zoneId the value to assign to {@code zoneId}
     */
    public void setZoneId(String zoneId) { this.zoneId = valueOrEmpty(zoneId); }
    /**
     * Sets the action.
     * @param action the value to assign to {@code action}
     */
    public void setAction(String action) { this.action = valueOrEmpty(action); }
    /**
     * Sets the outcome.
     * @param outcome the value to assign to {@code outcome}
     */
    public void setOutcome(String outcome) { this.outcome = valueOrEmpty(outcome); }
    /**
     * Sets the failure reason.
     * @param failureReason the value to assign to {@code failureReason}
     */
    public void setFailureReason(String failureReason) { this.failureReason = valueOrEmpty(failureReason); }
    /**
     * Sets the guard id.
     * @param guardId the value to assign to {@code guardId}
     */
    public void setGuardId(String guardId) { this.guardId = valueOrEmpty(guardId); }
    /**
     * Sets the request id.
     * @param requestId the value to assign to {@code requestId}
     */
    public void setRequestId(String requestId) { this.requestId = valueOrEmpty(requestId); }
    /**
     * Sets the student roll no.
     * @param studentRollNo the value to assign to {@code studentRollNo}
     */
    public void setStudentRollNo(String studentRollNo) { this.studentRollNo = valueOrEmpty(studentRollNo); }
    /**
     * Sets the wing.
     * @param wing the value to assign to {@code wing}
     */
    public void setWing(String wing) { this.wing = valueOrEmpty(wing); }
    /**
     * Sets the result.
     * @param result the value to assign to {@code result}
     */
    public void setResult(String result) { this.result = valueOrEmpty(result); }
    /**
     * Sets the reason.
     * @param reason the value to assign to {@code reason}
     */
    public void setReason(String reason) { this.reason = valueOrEmpty(reason); }
    /**
     * Sets the timestamp.
     * @param timestamp the value to assign to {@code timestamp}
     */
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    /**
     * Compares this model with another object for value equality.
     * @param o the object to compare with this instance
     * @return {@code true} when the supplied object represents the same model data
     */
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

    /**
     * Computes the hash code for the identifying fields of this model.
     * @return the hash code for this model
     */
    @Override
    public int hashCode() {
        return Objects.hash(logId, entityId, zoneId, action, outcome, studentRollNo, wing, result);
    }

    /**
     * Returns a concise diagnostic string for this model.
     * @return a readable summary of this model
     */
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
