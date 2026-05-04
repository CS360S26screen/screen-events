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
 *
 * <p><b>Design pattern:</b> Data Transfer Object (DTO) / Firestore document model.
 * The class stores structured data for Firebase serialization while keeping
 * business decisions in the rules and service layers.</p>
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

    /**
     * Creates an empty {@code WingAccessRequest} instance for Firestore deserialization.
     */
    public WingAccessRequest() {}

    /**
     * Creates a populated {@code WingAccessRequest} instance.
     * @param requestId the value to assign to {@code requestId}
     * @param studentRollNo the value to assign to {@code studentRollNo}
     * @param studentName the value to assign to {@code studentName}
     * @param wing the value to assign to {@code wing}
     * @param requestedBy the value to assign to {@code requestedBy}
     * @param requesterType the value to assign to {@code requesterType}
     * @param facultyId the value to assign to {@code facultyId}
     * @param status the value to assign to {@code status}
     * @param reason the value to assign to {@code reason}
     * @param createdAt the value to assign to {@code createdAt}
     */
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

    /**
     * Returns the request id.
     * @return the current request id
     */
    public String getRequestId() { return requestId; }
    /**
     * Returns the student roll no.
     * @return the current student roll no
     */
    public String getStudentRollNo() { return studentRollNo; }
    /**
     * Returns the student name.
     * @return the current student name
     */
    public String getStudentName() { return studentName; }
    /**
     * Returns the wing.
     * @return the current wing
     */
    public String getWing() { return wing; }
    /**
     * Returns the requested by.
     * @return the current requested by
     */
    public String getRequestedBy() { return requestedBy; }
    /**
     * Returns the requester type.
     * @return the current requester type
     */
    public String getRequesterType() { return requesterType; }
    /**
     * Returns the faculty id.
     * @return the current faculty id
     */
    public String getFacultyId() { return facultyId; }
    /**
     * Returns the status.
     * @return the current status
     */
    public String getStatus() { return status; }
    /**
     * Returns the reason.
     * @return the current reason
     */
    public String getReason() { return reason; }
    /**
     * Returns the created at.
     * @return the current created at
     */
    public Timestamp getCreatedAt() { return createdAt; }

    /**
     * Sets the request id.
     * @param requestId the value to assign to {@code requestId}
     */
    public void setRequestId(String requestId) { this.requestId = requestId; }
    /**
     * Sets the student roll no.
     * @param studentRollNo the value to assign to {@code studentRollNo}
     */
    public void setStudentRollNo(String studentRollNo) { this.studentRollNo = studentRollNo; }
    /**
     * Sets the student name.
     * @param studentName the value to assign to {@code studentName}
     */
    public void setStudentName(String studentName) { this.studentName = studentName; }
    /**
     * Sets the wing.
     * @param wing the value to assign to {@code wing}
     */
    public void setWing(String wing) { this.wing = wing; }
    /**
     * Sets the requested by.
     * @param requestedBy the value to assign to {@code requestedBy}
     */
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    /**
     * Sets the requester type.
     * @param requesterType the value to assign to {@code requesterType}
     */
    public void setRequesterType(String requesterType) { this.requesterType = requesterType; }
    /**
     * Sets the faculty id.
     * @param facultyId the value to assign to {@code facultyId}
     */
    public void setFacultyId(String facultyId) { this.facultyId = facultyId; }
    /**
     * Sets the status.
     * @param status the value to assign to {@code status}
     */
    public void setStatus(String status) { this.status = status; }
    /**
     * Sets the reason.
     * @param reason the value to assign to {@code reason}
     */
    public void setReason(String reason) { this.reason = reason; }
    /**
     * Sets the created at.
     * @param createdAt the value to assign to {@code createdAt}
     */
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    /**
     * Compares this model with another object for value equality.
     * @param o the object to compare with this instance
     * @return {@code true} when the supplied object represents the same model data
     */
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

    /**
     * Computes the hash code for the identifying fields of this model.
     * @return the hash code for this model
     */
    @Override
    public int hashCode() {
        return Objects.hash(requestId, studentRollNo, wing, status);
    }

    /**
     * Returns a concise diagnostic string for this model.
     * @return a readable summary of this model
     */
    @Override
    public String toString() {
        return "WingAccessRequest{id='" + requestId + "', student='" + studentRollNo
                + "', wing='" + wing + "', status='" + status + "'}";
    }
}
