package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Firestore model for a pending car registration request in the Campus Gate Access System.
 *
 * <p>Documents live in the {@code car_registration_requests} collection. Students and faculty
 * submit these; admin approves them (which creates a {@link RegisteredVehicle} document) or
 * rejects them. A rejected or cancelled request leaves no vehicle record.</p>
 *
 * <p>Max 2 approved + pending requests per user (enforced in the submission logic).</p>
 */
public class CarRegistrationRequest {

    @DocumentId
    private String requestId = "";

    /** Firebase Auth UID of the requester. */
    private String studentUid = "";

    /** Display name of the vehicle owner. */
    private String ownerName = "";

    /** Roll number (student) or faculty ID (faculty) of the owner. */
    private String ownerRollNo = "";

    /** {@code "student"} or {@code "faculty"}. */
    private String ownerRole = "student";

    /** License plate (uppercase, no spaces). */
    private String licensePlate = "";

    /** Car make/model (e.g. "Honda Civic 2019"). */
    private String carModel = "";

    /** {@code "pending"}, {@code "approved"}, or {@code "rejected"}. */
    private String status = "pending";

    private Timestamp createdAt = Timestamp.now();

    public CarRegistrationRequest() {}

    public CarRegistrationRequest(String requestId, String studentUid, String ownerName,
                                  String ownerRollNo, String ownerRole,
                                  String licensePlate, String carModel,
                                  String status, Timestamp createdAt) {
        this.requestId = requestId;
        this.studentUid = studentUid;
        this.ownerName = ownerName;
        this.ownerRollNo = ownerRollNo;
        this.ownerRole = ownerRole;
        this.licensePlate = licensePlate;
        this.carModel = carModel;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getRequestId() { return requestId; }
    public String getStudentUid() { return studentUid; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerRollNo() { return ownerRollNo; }
    public String getOwnerRole() { return ownerRole; }
    public String getLicensePlate() { return licensePlate; }
    public String getCarModel() { return carModel; }
    public String getStatus() { return status; }
    public Timestamp getCreatedAt() { return createdAt; }

    public void setRequestId(String requestId) { this.requestId = requestId; }
    public void setStudentUid(String studentUid) { this.studentUid = studentUid; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public void setOwnerRollNo(String ownerRollNo) { this.ownerRollNo = ownerRollNo; }
    public void setOwnerRole(String ownerRole) { this.ownerRole = ownerRole; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public void setCarModel(String carModel) { this.carModel = carModel; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CarRegistrationRequest)) return false;
        CarRegistrationRequest that = (CarRegistrationRequest) o;
        return Objects.equals(requestId, that.requestId)
                && Objects.equals(licensePlate, that.licensePlate)
                && Objects.equals(status, that.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, licensePlate, status);
    }

    @Override
    public String toString() {
        return "CarRegistrationRequest{plate='" + licensePlate + "', owner='" + ownerRollNo
                + "', status='" + status + "'}";
    }
}
