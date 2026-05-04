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
 *
 * <p><b>Design pattern:</b> Data Transfer Object (DTO) / Firestore document model.
 * The class stores structured data for Firebase serialization while keeping
 * business decisions in the rules and service layers.</p>
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

    /**
     * Creates an empty {@code CarRegistrationRequest} instance for Firestore deserialization.
     */
    public CarRegistrationRequest() {}

    /**
     * Creates a populated {@code CarRegistrationRequest} instance.
     * @param requestId the value to assign to {@code requestId}
     * @param studentUid the value to assign to {@code studentUid}
     * @param ownerName the value to assign to {@code ownerName}
     * @param ownerRollNo the value to assign to {@code ownerRollNo}
     * @param ownerRole the value to assign to {@code ownerRole}
     * @param licensePlate the value to assign to {@code licensePlate}
     * @param carModel the value to assign to {@code carModel}
     * @param status the value to assign to {@code status}
     * @param createdAt the value to assign to {@code createdAt}
     */
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

    /**
     * Returns the request id.
     * @return the current request id
     */
    public String getRequestId() { return requestId; }
    /**
     * Returns the student uid.
     * @return the current student uid
     */
    public String getStudentUid() { return studentUid; }
    /**
     * Returns the owner name.
     * @return the current owner name
     */
    public String getOwnerName() { return ownerName; }
    /**
     * Returns the owner roll no.
     * @return the current owner roll no
     */
    public String getOwnerRollNo() { return ownerRollNo; }
    /**
     * Returns the owner role.
     * @return the current owner role
     */
    public String getOwnerRole() { return ownerRole; }
    /**
     * Returns the license plate.
     * @return the current license plate
     */
    public String getLicensePlate() { return licensePlate; }
    /**
     * Returns the car model.
     * @return the current car model
     */
    public String getCarModel() { return carModel; }
    /**
     * Returns the status.
     * @return the current status
     */
    public String getStatus() { return status; }
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
     * Sets the student uid.
     * @param studentUid the value to assign to {@code studentUid}
     */
    public void setStudentUid(String studentUid) { this.studentUid = studentUid; }
    /**
     * Sets the owner name.
     * @param ownerName the value to assign to {@code ownerName}
     */
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    /**
     * Sets the owner roll no.
     * @param ownerRollNo the value to assign to {@code ownerRollNo}
     */
    public void setOwnerRollNo(String ownerRollNo) { this.ownerRollNo = ownerRollNo; }
    /**
     * Sets the owner role.
     * @param ownerRole the value to assign to {@code ownerRole}
     */
    public void setOwnerRole(String ownerRole) { this.ownerRole = ownerRole; }
    /**
     * Sets the license plate.
     * @param licensePlate the value to assign to {@code licensePlate}
     */
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    /**
     * Sets the car model.
     * @param carModel the value to assign to {@code carModel}
     */
    public void setCarModel(String carModel) { this.carModel = carModel; }
    /**
     * Sets the status.
     * @param status the value to assign to {@code status}
     */
    public void setStatus(String status) { this.status = status; }
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
        if (!(o instanceof CarRegistrationRequest)) return false;
        CarRegistrationRequest that = (CarRegistrationRequest) o;
        return Objects.equals(requestId, that.requestId)
                && Objects.equals(licensePlate, that.licensePlate)
                && Objects.equals(status, that.status);
    }

    /**
     * Computes the hash code for the identifying fields of this model.
     * @return the hash code for this model
     */
    @Override
    public int hashCode() {
        return Objects.hash(requestId, licensePlate, status);
    }

    /**
     * Returns a concise diagnostic string for this model.
     * @return a readable summary of this model
     */
    @Override
    public String toString() {
        return "CarRegistrationRequest{plate='" + licensePlate + "', owner='" + ownerRollNo
                + "', status='" + status + "'}";
    }
}
