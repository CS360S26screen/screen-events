package com.example.se_proj.models;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

import java.util.Objects;

/**
 * Domain model for a delivery-rider lifecycle record (US21).
 *
 * <p>Maps to the Firestore {@code delivery_logs} collection. One document is created
 * when a rider enters campus ({@link #STATUS_ACTIVE}), updated on exit
 * ({@link #STATUS_COMPLETED} or {@link #STATUS_OVERSTAY}), and the actual on-campus
 * duration is recorded in {@code durationMinutes}.</p>
 *
 * <h3>Firestore document structure</h3>
 * <pre>
 * delivery_logs/{deliveryId}
 *   riderId          : "4201234567890"
 *   riderName        : "Ahmed Courier"
 *   riderCNIC        : "4201234567890"
 *   companyName      : "FoodExpress"
 *   vehicleNumber    : "ABC-123"
 *   destinationBlock : "Block A - Admin Building"
 *   receivingHostId  : "FAC-102"
 *   receivingHostName: "Dr. Khan"
 *   entryZoneId      : "in_gate"
 *   exitZoneId       : "out_gate"
 *   entryTime        : Timestamp
 *   expectedExitTime : Timestamp               // entryTime + 30 min
 *   exitTime         : Timestamp | null        // set on exit
 *   durationMinutes  : 0                       // set on exit
 *   status           : "active" | "completed" | "overstay"
 *   overstayFlagged  : false
 *   guardId          : "firebase-uid"
 * </pre>
 *
 * <h3>Composite indexes required</h3>
 * <ul>
 *   <li>{@code riderCNIC ASC, status ASC} — duplicate-entry guard in DeliveryTrackingService</li>
 *   <li>{@code status ASC, expectedExitTime ASC} — overstay detection scan</li>
 * </ul>
 */
public class DeliveryLog {

    public static final String STATUS_ACTIVE    = "active";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_OVERSTAY  = "overstay";

    @DocumentId
    private String deliveryId       = "";
    private String riderId          = "";
    private String riderName        = "";
    private String riderCNIC        = "";
    private String companyName      = "";
    private String vehicleNumber    = "";
    private String destinationBlock = "";
    private String receivingHostId  = "";
    private String receivingHostName = "";
    private String entryZoneId      = "";
    private String exitZoneId       = "";
    private Timestamp entryTime        = null;
    private Timestamp expectedExitTime = null;
    private Timestamp exitTime         = null;
    private long durationMinutes       = 0;
    private String status              = STATUS_ACTIVE;
    private boolean overstayFlagged    = false;
    private String guardId             = "";

    /** Required no-arg constructor for Firestore deserialization. */
    public DeliveryLog() {}

    public DeliveryLog(String riderId, String riderName, String riderCNIC,
                       String vehicleNumber, String destinationBlock,
                       Timestamp entryTime, Timestamp expectedExitTime,
                       String guardId) {
        this(riderId, riderName, riderCNIC, "", vehicleNumber, destinationBlock,
                "", "", entryTime, expectedExitTime, guardId);
    }

    public DeliveryLog(String riderId, String riderName, String riderCNIC,
                       String companyName, String vehicleNumber, String destinationBlock,
                       String receivingHostId, String receivingHostName,
                       Timestamp entryTime, Timestamp expectedExitTime,
                       String guardId) {
        this(riderId, riderName, riderCNIC, companyName, vehicleNumber, destinationBlock,
                receivingHostId, receivingHostName, "", entryTime, expectedExitTime, guardId);
    }

    public DeliveryLog(String riderId, String riderName, String riderCNIC,
                       String companyName, String vehicleNumber, String destinationBlock,
                       String receivingHostId, String receivingHostName, String entryZoneId,
                       Timestamp entryTime, Timestamp expectedExitTime,
                       String guardId) {
        this.riderId          = riderId;
        this.riderName        = riderName;
        this.riderCNIC        = riderCNIC;
        this.companyName      = companyName;
        this.vehicleNumber    = vehicleNumber;
        this.destinationBlock = destinationBlock;
        this.receivingHostId  = receivingHostId;
        this.receivingHostName = receivingHostName;
        this.entryZoneId      = entryZoneId;
        this.exitZoneId       = "";
        this.entryTime        = entryTime;
        this.expectedExitTime = expectedExitTime;
        this.status           = STATUS_ACTIVE;
        this.overstayFlagged  = false;
        this.guardId          = guardId;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getDeliveryId()        { return deliveryId; }
    public String getRiderId()           { return riderId; }
    public String getRiderName()         { return riderName; }
    public String getRiderCNIC()         { return riderCNIC; }
    public String getCompanyName()       { return companyName; }
    public String getVehicleNumber()     { return vehicleNumber; }
    public String getDestinationBlock()  { return destinationBlock; }
    public String getReceivingHostId()   { return receivingHostId; }
    public String getReceivingHostName() { return receivingHostName; }
    public String getEntryZoneId()       { return entryZoneId; }
    public String getExitZoneId()        { return exitZoneId; }
    public Timestamp getEntryTime()      { return entryTime; }
    public Timestamp getExpectedExitTime(){ return expectedExitTime; }
    public Timestamp getExitTime()       { return exitTime; }
    public long getDurationMinutes()     { return durationMinutes; }
    public String getStatus()            { return status; }
    public boolean isOverstayFlagged()   { return overstayFlagged; }
    public String getGuardId()           { return guardId; }

    // -------------------------------------------------------------------------
    // Setters (required for Firestore deserialization)
    // -------------------------------------------------------------------------

    public void setDeliveryId(String deliveryId)              { this.deliveryId        = deliveryId; }
    public void setRiderId(String riderId)                    { this.riderId           = riderId; }
    public void setRiderName(String riderName)                { this.riderName         = riderName; }
    public void setRiderCNIC(String riderCNIC)                { this.riderCNIC         = riderCNIC; }
    public void setCompanyName(String companyName)            { this.companyName       = companyName; }
    public void setVehicleNumber(String vehicleNumber)        { this.vehicleNumber     = vehicleNumber; }
    public void setDestinationBlock(String destinationBlock)  { this.destinationBlock  = destinationBlock; }
    public void setReceivingHostId(String receivingHostId)    { this.receivingHostId   = receivingHostId; }
    public void setReceivingHostName(String receivingHostName){ this.receivingHostName = receivingHostName; }
    public void setEntryZoneId(String entryZoneId)            { this.entryZoneId       = entryZoneId; }
    public void setExitZoneId(String exitZoneId)              { this.exitZoneId        = exitZoneId; }
    public void setEntryTime(Timestamp entryTime)             { this.entryTime         = entryTime; }
    public void setExpectedExitTime(Timestamp expectedExitTime){ this.expectedExitTime = expectedExitTime; }
    public void setExitTime(Timestamp exitTime)               { this.exitTime          = exitTime; }
    public void setDurationMinutes(long durationMinutes)      { this.durationMinutes   = durationMinutes; }
    public void setStatus(String status)                      { this.status            = status; }
    public void setOverstayFlagged(boolean overstayFlagged)   { this.overstayFlagged   = overstayFlagged; }
    public void setGuardId(String guardId)                    { this.guardId           = guardId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeliveryLog)) return false;
        DeliveryLog that = (DeliveryLog) o;
        return Objects.equals(deliveryId, that.deliveryId);
    }

    @Override
    public int hashCode() { return Objects.hash(deliveryId); }

    @Override
    public String toString() {
        return "DeliveryLog{riderId='" + riderId + "', status='" + status
                + "', duration=" + durationMinutes + "min}";
    }
}
