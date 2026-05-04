package com.example.se_proj.models;

import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Domain model representing a visitor access request in the Campus Gate Access System.
 *
 * <p>Each instance maps 1-to-1 with a document in the Firestore {@code visitor_requests}
 * collection. The class serves both pre-scheduled faculty/student requests and ad-hoc
 * walk-in requests created by guards at the gate.</p>
 *
 * <h3>Lifecycle</h3>
 * A request progresses through the statuses defined in
 * {@link com.example.se_proj.rules.RequestStatus}:
 * {@code pending} / {@code pending_adhoc} &rarr; {@code approved} | {@code rejected} |
 * {@code denied} | {@code cancelled}.
 * Once approved, the guard may check the visitor in ({@link #onCampus} = true) and later
 * check them out.
 *
 * <h3>Security Model</h3>
 * <ul>
 *   <li>{@link #creatorId} stores the Firebase Auth UID of the user who created the request
 *       and is used by Firestore security rules to enforce ownership-based access.</li>
 *   <li>{@link #hostId} stores the human-readable roll number or faculty ID used for
 *       display and business-logic queries (e.g. guest-limit checks).</li>
 * </ul>
 *
 * <h3>Outstanding Issues</h3>
 * The temporal fields ({@code visitDate}, {@code startTime}, {@code endTime},
 * {@code entryTime}, {@code exitTime}) are currently string-based and should migrate to
 * structured timestamp/date types for stronger validation and query safety.
 *
 * <p><b>Design pattern:</b> Data Transfer Object (DTO) / Firestore document model.
 * The class stores structured data for Firebase serialization while keeping
 * business decisions in the rules and service layers.</p>
 */
public class VisitorRequest {

    /** Firestore document ID, auto-populated by {@link DocumentId}. */
    @DocumentId
    private String requestId = "";

    /** Full name of the visitor. */
    private String guestName = "";

    /** 13-digit CNIC of the visitor (stored digits-only after normalization). */
    private String guestCNIC = "";

    /** Reason for the visit (e.g. "Meeting", "Student Guest Visit"). */
    private String purpose = "";

    /** Scheduled visit date in {@code dd/MM/yyyy} format. */
    private String visitDate = "";

    /** Approved entry window start in {@code HH:mm} format. */
    private String startTime = "";

    /** Approved entry window end in {@code HH:mm} format. */
    private String endTime = "";

    /** Roll number or faculty ID of the campus host. */
    private String hostId = "";

    /** Firebase Auth UID of the request creator (used by security rules). */
    private String creatorId = "";

    /** Role of the host: {@code "faculty"} or {@code "student"}. */
    private String hostType = "faculty";

    /** Current request status (see {@link com.example.se_proj.rules.RequestStatus}). */
    private String status = "pending";

    /** Actual entry timestamp set by the guard on check-in, or null. */
    private String entryTime = null;

    /** Actual exit timestamp set by the guard on check-out, or null. */
    private String exitTime = null;

    /** {@code true} while the visitor is physically on campus. */
    private boolean onCampus = false;

    /** Required no-arg constructor for Firestore deserialization. */
    public VisitorRequest() {}

    /**
     * Full constructor for programmatic creation of requests.
     *
     * @param requestId  Firestore document ID
     * @param guestName  Full name of the visitor
     * @param guestCNIC  13-digit CNIC
     * @param purpose    Reason for the visit
     * @param visitDate  Visit date in dd/MM/yyyy
     * @param startTime  Entry window start in HH:mm
     * @param endTime    Entry window end in HH:mm
     * @param hostId     Roll number or faculty ID of the host
     * @param creatorId  Firebase Auth UID of the creator
     * @param hostType   Host role: "faculty" or "student"
     * @param status     Current request status
     * @param entryTime  Actual entry timestamp, or null
     * @param exitTime   Actual exit timestamp, or null
     * @param onCampus   Whether the visitor is currently on campus
     */
    public VisitorRequest(String requestId, String guestName, String guestCNIC,
                          String purpose, String visitDate, String startTime, String endTime,
                          String hostId, String creatorId, String hostType, String status,
                          String entryTime, String exitTime, boolean onCampus) {
        this.requestId = requestId;
        this.guestName = guestName;
        this.guestCNIC = guestCNIC;
        this.purpose = purpose;
        this.visitDate = visitDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.hostId = hostId;
        this.creatorId = creatorId;
        this.hostType = hostType;
        this.status = status;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.onCampus = onCampus;
    }

    /**
     * Returns a new {@code VisitorRequest} with the given {@code requestId} and all other
     * fields copied from this instance. Replaces the Kotlin {@code data class copy()} usage.
     *
     * @param newRequestId the document ID to assign
     * @return a new instance with the updated ID
     */
    public VisitorRequest withRequestId(String newRequestId) {
        return new VisitorRequest(newRequestId, guestName, guestCNIC, purpose, visitDate,
                startTime, endTime, hostId, creatorId, hostType, status,
                entryTime, exitTime, onCampus);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    /**
     * Returns the request id.
     * @return the current request id
     */
    public String getRequestId() { return requestId; }
    /**
     * Returns the guest name.
     * @return the current guest name
     */
    public String getGuestName() { return guestName; }
    /**
     * Returns the visitor CNIC.
     *
     * @return the current visitor CNIC.
     */
    public String getGuestCNIC() { return guestCNIC; }
    /**
     * Returns the purpose.
     * @return the current purpose
     */
    public String getPurpose() { return purpose; }
    /**
     * Returns the visit date.
     * @return the current visit date
     */
    public String getVisitDate() { return visitDate; }
    /**
     * Returns the start time.
     * @return the current start time
     */
    public String getStartTime() { return startTime; }
    /**
     * Returns the end time.
     * @return the current end time
     */
    public String getEndTime() { return endTime; }
    /**
     * Returns the host id.
     * @return the current host id
     */
    public String getHostId() { return hostId; }
    /**
     * Returns the creator id.
     * @return the current creator id
     */
    public String getCreatorId() { return creatorId; }
    /**
     * Returns the host type.
     * @return the current host type
     */
    public String getHostType() { return hostType; }
    /**
     * Returns the status.
     * @return the current status
     */
    public String getStatus() { return status; }
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
     * Returns whether the visitor is currently on campus.
     *
     * @return {@code true} while the visitor is checked in and has not exited.
     */
    public boolean isOnCampus() { return onCampus; }

    // -------------------------------------------------------------------------
    // Setters (required for Firestore deserialization)
    // -------------------------------------------------------------------------

    /**
     * Sets the request id.
     * @param requestId the value to assign to {@code requestId}
     */
    public void setRequestId(String requestId) { this.requestId = requestId; }
    /**
     * Sets the guest name.
     * @param guestName the value to assign to {@code guestName}
     */
    public void setGuestName(String guestName) { this.guestName = guestName; }
    /**
     * Sets the guest cnic.
     * @param guestCNIC the value to assign to {@code guestCNIC}
     */
    public void setGuestCNIC(String guestCNIC) { this.guestCNIC = guestCNIC; }
    /**
     * Sets the purpose.
     * @param purpose the value to assign to {@code purpose}
     */
    public void setPurpose(String purpose) { this.purpose = purpose; }
    /**
     * Sets the visit date.
     * @param visitDate the value to assign to {@code visitDate}
     */
    public void setVisitDate(String visitDate) { this.visitDate = visitDate; }
    /**
     * Sets the start time.
     * @param startTime the value to assign to {@code startTime}
     */
    public void setStartTime(String startTime) { this.startTime = startTime; }
    /**
     * Sets the end time.
     * @param endTime the value to assign to {@code endTime}
     */
    public void setEndTime(String endTime) { this.endTime = endTime; }
    /**
     * Sets the host id.
     * @param hostId the value to assign to {@code hostId}
     */
    public void setHostId(String hostId) { this.hostId = hostId; }
    /**
     * Sets the creator id.
     * @param creatorId the value to assign to {@code creatorId}
     */
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    /**
     * Sets the host type.
     * @param hostType the value to assign to {@code hostType}
     */
    public void setHostType(String hostType) { this.hostType = hostType; }
    /**
     * Sets the status.
     * @param status the value to assign to {@code status}
     */
    public void setStatus(String status) { this.status = status; }
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
     * Sets the on campus.
     * @param onCampus the value to assign to {@code onCampus}
     */
    public void setOnCampus(boolean onCampus) { this.onCampus = onCampus; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    /**
     * Compares this model with another object for value equality.
     * @param o the object to compare with this instance
     * @return {@code true} when the supplied object represents the same model data
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VisitorRequest)) return false;
        VisitorRequest that = (VisitorRequest) o;
        return onCampus == that.onCampus
                && Objects.equals(requestId, that.requestId)
                && Objects.equals(guestName, that.guestName)
                && Objects.equals(guestCNIC, that.guestCNIC)
                && Objects.equals(purpose, that.purpose)
                && Objects.equals(visitDate, that.visitDate)
                && Objects.equals(startTime, that.startTime)
                && Objects.equals(endTime, that.endTime)
                && Objects.equals(hostId, that.hostId)
                && Objects.equals(creatorId, that.creatorId)
                && Objects.equals(hostType, that.hostType)
                && Objects.equals(status, that.status)
                && Objects.equals(entryTime, that.entryTime)
                && Objects.equals(exitTime, that.exitTime);
    }

    /**
     * Computes the hash code for the identifying fields of this model.
     * @return the hash code for this model
     */
    @Override
    public int hashCode() {
        return Objects.hash(requestId, guestName, guestCNIC, purpose, visitDate,
                startTime, endTime, hostId, creatorId, hostType, status,
                entryTime, exitTime, onCampus);
    }

    /**
     * Returns a concise diagnostic string for this model.
     * @return a readable summary of this model
     */
    @Override
    public String toString() {
        return "VisitorRequest{" +
                "requestId='" + requestId + '\'' +
                ", guestName='" + guestName + '\'' +
                ", guestCNIC='" + guestCNIC + '\'' +
                ", purpose='" + purpose + '\'' +
                ", visitDate='" + visitDate + '\'' +
                ", startTime='" + startTime + '\'' +
                ", endTime='" + endTime + '\'' +
                ", hostId='" + hostId + '\'' +
                ", creatorId='" + creatorId + '\'' +
                ", hostType='" + hostType + '\'' +
                ", status='" + status + '\'' +
                ", entryTime='" + entryTime + '\'' +
                ", exitTime='" + exitTime + '\'' +
                ", onCampus=" + onCampus +
                '}';
    }
}
