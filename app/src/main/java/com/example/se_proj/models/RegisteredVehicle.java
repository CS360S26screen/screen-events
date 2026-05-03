package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Firestore model for a student-registered vehicle in the Campus Gate Access System.
 *
 * <p>Documents live in the {@code cars_registered} collection. One student can own at most
 * two vehicles. License plates must be unique across all documents.</p>
 */
public class RegisteredVehicle {

    @DocumentId
    private String vehicleId = "";

    /** Roll number of the student who owns this vehicle. */
    private String studentRollNo = "";

    /** Display name of the student. */
    private String studentName = "";

    /** Firebase Auth UID of the student. */
    private String studentUid = "";

    /** {@code "student"} or {@code "faculty"}. */
    private String ownerRole = "student";

    /** License plate (uppercase, stored without spaces). */
    private String licensePlate = "";

    /** Car make/model description (e.g. "Honda Civic 2019"). */
    private String carModel = "";

    /** {@code true} while the vehicle is physically on campus. */
    private boolean onCampus = false;

    /** Timestamp string when the car entered; null if not on campus. */
    private String entryTime = null;

    /** Timestamp string when the car last exited; null if still on campus. */
    private String exitTime = null;

    /** False when the vehicle credential has been revoked or disabled. */
    private boolean active = true;

    /** Optional credential expiry; null means no expiry is configured. */
    private Timestamp credentialExpiresAt = null;

    public RegisteredVehicle() {}

    public RegisteredVehicle(String vehicleId, String studentRollNo, String studentName,
                              String studentUid, String licensePlate, String carModel,
                              boolean onCampus, String entryTime, String exitTime) {
        this(vehicleId, studentRollNo, studentName, studentUid, "student", licensePlate,
                carModel, onCampus, entryTime, exitTime);
    }

    public RegisteredVehicle(String vehicleId, String studentRollNo, String studentName,
                              String studentUid, String ownerRole, String licensePlate,
                              String carModel, boolean onCampus, String entryTime,
                              String exitTime) {
        this.vehicleId = vehicleId;
        this.studentRollNo = studentRollNo;
        this.studentName = studentName;
        this.studentUid = studentUid;
        this.ownerRole = ownerRole == null || ownerRole.trim().isEmpty()
                ? "student" : ownerRole.trim();
        this.licensePlate = licensePlate;
        this.carModel = carModel;
        this.onCampus = onCampus;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
    }

    public String getVehicleId() { return vehicleId; }
    public String getStudentRollNo() { return studentRollNo; }
    public String getStudentName() { return studentName; }
    public String getStudentUid() { return studentUid; }
    public String getOwnerRole() { return ownerRole; }
    public String getLicensePlate() { return licensePlate; }
    public String getCarModel() { return carModel; }
    public boolean isOnCampus() { return onCampus; }
    public String getEntryTime() { return entryTime; }
    public String getExitTime() { return exitTime; }
    public boolean isActive() { return active; }
    public Timestamp getCredentialExpiresAt() { return credentialExpiresAt; }

    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    public void setStudentRollNo(String studentRollNo) { this.studentRollNo = studentRollNo; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public void setStudentUid(String studentUid) { this.studentUid = studentUid; }
    public void setOwnerRole(String ownerRole) { this.ownerRole = ownerRole; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    public void setCarModel(String carModel) { this.carModel = carModel; }
    public void setOnCampus(boolean onCampus) { this.onCampus = onCampus; }
    public void setEntryTime(String entryTime) { this.entryTime = entryTime; }
    public void setExitTime(String exitTime) { this.exitTime = exitTime; }
    public void setActive(boolean active) { this.active = active; }
    public void setCredentialExpiresAt(Timestamp credentialExpiresAt) {
        this.credentialExpiresAt = credentialExpiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegisteredVehicle)) return false;
        RegisteredVehicle that = (RegisteredVehicle) o;
        return onCampus == that.onCampus
                && Objects.equals(vehicleId, that.vehicleId)
                && Objects.equals(studentRollNo, that.studentRollNo)
                && Objects.equals(licensePlate, that.licensePlate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vehicleId, studentRollNo, licensePlate, onCampus);
    }

    @Override
    public String toString() {
        return "RegisteredVehicle{plate='" + licensePlate + "', model='" + carModel
                + "', student='" + studentRollNo + "', onCampus=" + onCampus + "}";
    }
}
