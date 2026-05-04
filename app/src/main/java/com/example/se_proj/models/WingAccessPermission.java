package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Firestore model for an approved wing/lab access permission.
 *
 * <p>Documents live in the {@code wing_access_permissions} collection. Created by admin when
 * approving a {@link WingAccessRequest}. Admin can revoke by setting {@code active = false}.
 * The self-service scanner checks this collection for valid, active, non-expired permissions.</p>
 *
 * <p><b>Design pattern:</b> Data Transfer Object (DTO) / Firestore document model.
 * The class stores structured data for Firebase serialization while keeping
 * business decisions in the rules and service layers.</p>
 */
public class WingAccessPermission {

    @DocumentId
    private String permissionId = "";

    /** Roll number of the student granted access. */
    private String studentRollNo = "";

    /** Display name of the student. */
    private String studentName = "";

    /** Wing this permission covers (see {@link com.example.se_proj.rules.WingConstants}). */
    private String wing = "";

    /** Firebase Auth UID of the admin who granted access. */
    private String grantedBy = "";

    /** {@code true} if access never expires; {@code false} means check {@code expiryDate}. */
    private boolean lifetime = false;

    /** Expiry date in {@code dd/MM/yyyy} format; {@code null} when {@code lifetime == true}. */
    private String expiryDate = null;

    private Timestamp grantedAt = Timestamp.now();

    /** {@code false} if admin has revoked this permission. */
    private boolean active = true;

    /**
     * Creates an empty {@code WingAccessPermission} instance for Firestore deserialization.
     */
    public WingAccessPermission() {}

    /**
     * Creates a populated {@code WingAccessPermission} instance.
     * @param permissionId the value to assign to {@code permissionId}
     * @param studentRollNo the value to assign to {@code studentRollNo}
     * @param studentName the value to assign to {@code studentName}
     * @param wing the value to assign to {@code wing}
     * @param grantedBy the value to assign to {@code grantedBy}
     * @param lifetime the value to assign to {@code lifetime}
     * @param expiryDate the value to assign to {@code expiryDate}
     * @param grantedAt the value to assign to {@code grantedAt}
     * @param active the value to assign to {@code active}
     */
    public WingAccessPermission(String permissionId, String studentRollNo, String studentName,
                                 String wing, String grantedBy, boolean lifetime,
                                 String expiryDate, Timestamp grantedAt, boolean active) {
        this.permissionId = permissionId;
        this.studentRollNo = studentRollNo;
        this.studentName = studentName;
        this.wing = wing;
        this.grantedBy = grantedBy;
        this.lifetime = lifetime;
        this.expiryDate = expiryDate;
        this.grantedAt = grantedAt;
        this.active = active;
    }

    /**
     * Returns the permission id.
     * @return the current permission id
     */
    public String getPermissionId() { return permissionId; }
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
     * Returns the granted by.
     * @return the current granted by
     */
    public String getGrantedBy() { return grantedBy; }
    /**
     * Returns the lifetime.
     * @return the current lifetime
     */
    public boolean isLifetime() { return lifetime; }
    /**
     * Returns the expiry date.
     * @return the current expiry date
     */
    public String getExpiryDate() { return expiryDate; }
    /**
     * Returns the granted at.
     * @return the current granted at
     */
    public Timestamp getGrantedAt() { return grantedAt; }
    /**
     * Returns whether this permission is active.
     *
     * @return {@code true} when the permission has not been revoked.
     */
    public boolean isActive() { return active; }

    /**
     * Sets the permission id.
     * @param permissionId the value to assign to {@code permissionId}
     */
    public void setPermissionId(String permissionId) { this.permissionId = permissionId; }
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
     * Sets the granted by.
     * @param grantedBy the value to assign to {@code grantedBy}
     */
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    /**
     * Sets the lifetime.
     * @param lifetime the value to assign to {@code lifetime}
     */
    public void setLifetime(boolean lifetime) { this.lifetime = lifetime; }
    /**
     * Sets the expiry date.
     * @param expiryDate the value to assign to {@code expiryDate}
     */
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
    /**
     * Sets the granted at.
     * @param grantedAt the value to assign to {@code grantedAt}
     */
    public void setGrantedAt(Timestamp grantedAt) { this.grantedAt = grantedAt; }
    /**
     * Sets the active.
     * @param active the value to assign to {@code active}
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Compares this model with another object for value equality.
     * @param o the object to compare with this instance
     * @return {@code true} when the supplied object represents the same model data
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WingAccessPermission)) return false;
        WingAccessPermission that = (WingAccessPermission) o;
        return lifetime == that.lifetime && active == that.active
                && Objects.equals(permissionId, that.permissionId)
                && Objects.equals(studentRollNo, that.studentRollNo)
                && Objects.equals(wing, that.wing);
    }

    /**
     * Computes the hash code for the identifying fields of this model.
     * @return the hash code for this model
     */
    @Override
    public int hashCode() {
        return Objects.hash(permissionId, studentRollNo, wing, lifetime, active);
    }

    /**
     * Returns a concise diagnostic string for this model.
     * @return a readable summary of this model
     */
    @Override
    public String toString() {
        return "WingAccessPermission{id='" + permissionId + "', student='" + studentRollNo
                + "', wing='" + wing + "', lifetime=" + lifetime
                + ", expiry='" + expiryDate + "', active=" + active + "}";
    }
}
