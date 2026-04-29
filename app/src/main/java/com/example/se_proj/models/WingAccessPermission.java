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

    public WingAccessPermission() {}

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

    public String getPermissionId() { return permissionId; }
    public String getStudentRollNo() { return studentRollNo; }
    public String getStudentName() { return studentName; }
    public String getWing() { return wing; }
    public String getGrantedBy() { return grantedBy; }
    public boolean isLifetime() { return lifetime; }
    public String getExpiryDate() { return expiryDate; }
    public Timestamp getGrantedAt() { return grantedAt; }
    public boolean isActive() { return active; }

    public void setPermissionId(String permissionId) { this.permissionId = permissionId; }
    public void setStudentRollNo(String studentRollNo) { this.studentRollNo = studentRollNo; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public void setWing(String wing) { this.wing = wing; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    public void setLifetime(boolean lifetime) { this.lifetime = lifetime; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
    public void setGrantedAt(Timestamp grantedAt) { this.grantedAt = grantedAt; }
    public void setActive(boolean active) { this.active = active; }

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

    @Override
    public int hashCode() {
        return Objects.hash(permissionId, studentRollNo, wing, lifetime, active);
    }

    @Override
    public String toString() {
        return "WingAccessPermission{id='" + permissionId + "', student='" + studentRollNo
                + "', wing='" + wing + "', lifetime=" + lifetime
                + ", expiry='" + expiryDate + "', active=" + active + "}";
    }
}
