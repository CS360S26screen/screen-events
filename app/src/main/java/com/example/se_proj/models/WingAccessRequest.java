package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Firestore model for a wing/lab access request in the Campus Gate Access System.
 *
 * <p>Documents live in the {@code wing_access_requests} collection. Students request access
 * for themselves; faculty may request on behalf of a student by providing student details.
 * Admin approves or rejects, which creates a {@link WingAccessPermission} on approval.</p>
 */
public class WingAccessRequest {

    @DocumentId
    private String requestId = "";

    /** Roll number of the student who needs wing access. */
    private String studentRollNo = "";

    /** Display name of the student. */
    private String studentName = "";

    /** One of the five campus wings (see {@link com.example.se_proj.rules.WingConstants}). */
    private String wing = "";

    /** Firebase Auth UID of whoever submitted the request (student or faculty). */
    private String requestedBy = "";

    /** {@code "student"} if self-request, {@code "faculty"} if on-behalf request. */
    private String requesterType = "student";

    /** Faculty ID when requesterType is {@code "faculty"}, otherwise empty string. */
    private String facultyId = "";

    /** Current status: {@code "pending"}, {@code "approved"}, or {@code "rejected"}. */
    private String status = "pending";

    /** Student's stated reason for needing wing access. */
    private String reason = "";

    private Timestamp createdAt = Timestamp.now();

    public WingAccessRequest() {}

    public WingAccessRequest(String requestId, String studentRollNo, String studentName,
                              String wing, String requestedBy, String requesterType,
                              String facultyId, String status, String reason,
                              Timestamp createdAt) {
        this.requestId = requestId;
        this.studentRollNo = studentRollNo;
        this.studentName = studentName;
        this.wing = wing;
        this.requestedBy = requestedBy;
        this.requesterType = requesterType;
        this.facultyId = facultyId;
        this.status = status;
        this.reason = reason != null ? reason : "";
        this.createdAt = createdAt;
    }

    public String getRequestId() { return requestId; }
    public String getStudentRollNo() { return studentRollNo; }
    public String getStudentName() { return studentName; }
    public String getWing() { return wing; }
    public String getRequestedBy() { return requestedBy; }
    public String getRequesterType() { return requesterType; }
    public String getFacultyId() { return facultyId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Timestamp getCreatedAt() { return createdAt; }

    public void setRequestId(String requestId) { this.requestId = requestId; }
    public void setStudentRollNo(String studentRollNo) { this.studentRollNo = studentRollNo; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public void setWing(String wing) { this.wing = wing; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public void setRequesterType(String requesterType) { this.requesterType = requesterType; }
    public void setFacultyId(String facultyId) { this.facultyId = facultyId; }
    public void setStatus(String status) { this.status = status; }
    public void setReason(String reason) { this.reason = reason; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WingAccessRequest)) return false;
        WingAccessRequest that = (WingAccessRequest) o;
        return Objects.equals(requestId, that.requestId)
                && Objects.equals(studentRollNo, that.studentRollNo)
                && Objects.equals(wing, that.wing)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, studentRollNo, wing, status);
    }

    @Override
    public String toString() {
        return "WingAccessRequest{id='" + requestId + "', student='" + studentRollNo
                + "', wing='" + wing + "', status='" + status + "'}";
    }
}
