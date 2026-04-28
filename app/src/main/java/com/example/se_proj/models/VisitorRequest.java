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

    public String getRequestId() { return requestId; }
    public String getGuestName() { return guestName; }
    public String getGuestCNIC() { return guestCNIC; }
    public String getPurpose() { return purpose; }
    public String getVisitDate() { return visitDate; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public String getHostId() { return hostId; }
    public String getCreatorId() { return creatorId; }
    public String getHostType() { return hostType; }
    public String getStatus() { return status; }
    public String getEntryTime() { return entryTime; }
    public String getExitTime() { return exitTime; }
    public boolean isOnCampus() { return onCampus; }
    /** Alias for {@link #isOnCampus()} — used by existing rule utilities. */
    public boolean getOnCampus() { return onCampus; }

    // -------------------------------------------------------------------------
    // Setters (required for Firestore deserialization)
    // -------------------------------------------------------------------------

    public void setRequestId(String requestId) { this.requestId = requestId; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public void setGuestCNIC(String guestCNIC) { this.guestCNIC = guestCNIC; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public void setVisitDate(String visitDate) { this.visitDate = visitDate; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
    public void setHostType(String hostType) { this.hostType = hostType; }
    public void setStatus(String status) { this.status = status; }
    public void setEntryTime(String entryTime) { this.entryTime = entryTime; }
    public void setExitTime(String exitTime) { this.exitTime = exitTime; }
    public void setOnCampus(boolean onCampus) { this.onCampus = onCampus; }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

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

    @Override
    public int hashCode() {
        return Objects.hash(requestId, guestName, guestCNIC, purpose, visitDate,
                startTime, endTime, hostId, creatorId, hostType, status,
                entryTime, exitTime, onCampus);
    }

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
