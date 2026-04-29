package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
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
