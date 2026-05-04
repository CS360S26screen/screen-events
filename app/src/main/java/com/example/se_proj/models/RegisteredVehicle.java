package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Firestore model for a student-registered vehicle in the Campus Gate Access System.
 *
 * <p>Documents live in the {@code cars_registered} collection. One student can own at most
 * two vehicles. License plates must be unique across all documents.</p>
 *
 * <p><b>Design pattern:</b> Data Transfer Object (DTO) / Firestore document model.
 * The class stores structured data for Firebase serialization while keeping
 * business decisions in the rules and service layers.</p>
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

    /**
     * Creates an empty {@code RegisteredVehicle} instance for Firestore deserialization.
     */
    public RegisteredVehicle() {}

    /**
     * Creates a populated {@code RegisteredVehicle} instance.
     * @param vehicleId the value to assign to {@code vehicleId}
     * @param studentRollNo the value to assign to {@code studentRollNo}
     * @param studentName the value to assign to {@code studentName}
     * @param studentUid the value to assign to {@code studentUid}
     * @param licensePlate the value to assign to {@code licensePlate}
     * @param carModel the value to assign to {@code carModel}
     * @param onCampus the value to assign to {@code onCampus}
     * @param entryTime the value to assign to {@code entryTime}
     * @param exitTime the value to assign to {@code exitTime}
     */
    public RegisteredVehicle(String vehicleId, String studentRollNo, String studentName,
                              String studentUid, String licensePlate, String carModel,
                              boolean onCampus, String entryTime, String exitTime) {
        this(vehicleId, studentRollNo, studentName, studentUid, "student", licensePlate,
                carModel, onCampus, entryTime, exitTime);
    }

    /**
     * Creates a populated {@code RegisteredVehicle} instance.
     * @param vehicleId the value to assign to {@code vehicleId}
     * @param studentRollNo the value to assign to {@code studentRollNo}
     * @param studentName the value to assign to {@code studentName}
     * @param studentUid the value to assign to {@code studentUid}
     * @param ownerRole the value to assign to {@code ownerRole}
     * @param licensePlate the value to assign to {@code licensePlate}
     * @param carModel the value to assign to {@code carModel}
     * @param onCampus the value to assign to {@code onCampus}
     * @param entryTime the value to assign to {@code entryTime}
     * @param exitTime the value to assign to {@code exitTime}
     */
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

    /**
     * Returns the vehicle id.
     * @return the current vehicle id
     */
    public String getVehicleId() { return vehicleId; }
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
     * Returns the student uid.
     * @return the current student uid
     */
    public String getStudentUid() { return studentUid; }
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
     * Returns whether this vehicle is currently on campus.
     *
     * @return {@code true} while the vehicle is checked in and has not exited.
     */
    public boolean isOnCampus() { return onCampus; }
    /**
     * Returns the entry time.
     * @return the current entry time
     */
    public String getEntryTime() { return entryTime; }
    /**
     * Returns the exit time.
     * @return the current exit time
     */
    public String getExitTime() { return exitTime; }
    /**
     * Returns whether this vehicle credential is active.
     *
     * @return {@code true} when the vehicle credential has not been disabled.
     */
    public boolean isActive() { return active; }
    /**
     * Returns the credential expires at.
     * @return the current credential expires at
     */
    public Timestamp getCredentialExpiresAt() { return credentialExpiresAt; }

    /**
     * Sets the vehicle id.
     * @param vehicleId the value to assign to {@code vehicleId}
     */
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
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
     * Sets the student uid.
     * @param studentUid the value to assign to {@code studentUid}
     */
    public void setStudentUid(String studentUid) { this.studentUid = studentUid; }
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
     * Sets the on campus.
     * @param onCampus the value to assign to {@code onCampus}
     */
    public void setOnCampus(boolean onCampus) { this.onCampus = onCampus; }
    /**
     * Sets the entry time.
     * @param entryTime the value to assign to {@code entryTime}
     */
    public void setEntryTime(String entryTime) { this.entryTime = entryTime; }
    /**
     * Sets the exit time.
     * @param exitTime the value to assign to {@code exitTime}
     */
    public void setExitTime(String exitTime) { this.exitTime = exitTime; }
    /**
     * Sets the active.
     * @param active the value to assign to {@code active}
     */
    public void setActive(boolean active) { this.active = active; }
    /**
     * Sets the credential expires at.
     * @param credentialExpiresAt the value to assign to {@code credentialExpiresAt}
     */
    public void setCredentialExpiresAt(Timestamp credentialExpiresAt) {
        this.credentialExpiresAt = credentialExpiresAt;
    }

    /**
     * Compares this model with another object for value equality.
     * @param o the object to compare with this instance
     * @return {@code true} when the supplied object represents the same model data
     */
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

    /**
     * Computes the hash code for the identifying fields of this model.
     * @return the hash code for this model
     */
    @Override
    public int hashCode() {
        return Objects.hash(vehicleId, studentRollNo, licensePlate, onCampus);
    }

    /**
     * Returns a concise diagnostic string for this model.
     * @return a readable summary of this model
     */
    @Override
    public String toString() {
        return "RegisteredVehicle{plate='" + licensePlate + "', model='" + carModel
                + "', student='" + studentRollNo + "', onCampus=" + onCampus + "}";
    }
}
