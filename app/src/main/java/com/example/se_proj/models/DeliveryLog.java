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
 *
 * <p><b>Design pattern:</b> Data Transfer Object (DTO) / Firestore document model.
 * The class stores structured data for Firebase serialization while keeping
 * business decisions in the rules and service layers.</p>
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

    /**
     * Creates a populated {@code DeliveryLog} instance.
     * @param riderId the value to assign to {@code riderId}
     * @param riderName the value to assign to {@code riderName}
     * @param riderCNIC the value to assign to {@code riderCNIC}
     * @param vehicleNumber the value to assign to {@code vehicleNumber}
     * @param destinationBlock the value to assign to {@code destinationBlock}
     * @param entryTime the value to assign to {@code entryTime}
     * @param expectedExitTime the value to assign to {@code expectedExitTime}
     * @param guardId the value to assign to {@code guardId}
     */
    public DeliveryLog(String riderId, String riderName, String riderCNIC,
                       String vehicleNumber, String destinationBlock,
                       Timestamp entryTime, Timestamp expectedExitTime,
                       String guardId) {
        this(riderId, riderName, riderCNIC, "", vehicleNumber, destinationBlock,
                "", "", entryTime, expectedExitTime, guardId);
    }

    /**
     * Creates a populated {@code DeliveryLog} instance.
     * @param riderId the value to assign to {@code riderId}
     * @param riderName the value to assign to {@code riderName}
     * @param riderCNIC the value to assign to {@code riderCNIC}
     * @param companyName the value to assign to {@code companyName}
     * @param vehicleNumber the value to assign to {@code vehicleNumber}
     * @param destinationBlock the value to assign to {@code destinationBlock}
     * @param receivingHostId the value to assign to {@code receivingHostId}
     * @param receivingHostName the value to assign to {@code receivingHostName}
     * @param entryTime the value to assign to {@code entryTime}
     * @param expectedExitTime the value to assign to {@code expectedExitTime}
     * @param guardId the value to assign to {@code guardId}
     */
    public DeliveryLog(String riderId, String riderName, String riderCNIC,
                       String companyName, String vehicleNumber, String destinationBlock,
                       String receivingHostId, String receivingHostName,
                       Timestamp entryTime, Timestamp expectedExitTime,
                       String guardId) {
        this(riderId, riderName, riderCNIC, companyName, vehicleNumber, destinationBlock,
                receivingHostId, receivingHostName, "", entryTime, expectedExitTime, guardId);
    }

    /**
     * Creates a populated {@code DeliveryLog} instance.
     * @param riderId the value to assign to {@code riderId}
     * @param riderName the value to assign to {@code riderName}
     * @param riderCNIC the value to assign to {@code riderCNIC}
     * @param companyName the value to assign to {@code companyName}
     * @param vehicleNumber the value to assign to {@code vehicleNumber}
     * @param destinationBlock the value to assign to {@code destinationBlock}
     * @param receivingHostId the value to assign to {@code receivingHostId}
     * @param receivingHostName the value to assign to {@code receivingHostName}
     * @param entryZoneId the value to assign to {@code entryZoneId}
     * @param entryTime the value to assign to {@code entryTime}
     * @param expectedExitTime the value to assign to {@code expectedExitTime}
     * @param guardId the value to assign to {@code guardId}
     */
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

    /**
     * Returns the delivery id.
     * @return the current delivery id
     */
    public String getDeliveryId()        { return deliveryId; }
    /**
     * Returns the rider id.
     * @return the current rider id
     */
    public String getRiderId()           { return riderId; }
    /**
     * Returns the rider name.
     * @return the current rider name
     */
    public String getRiderName()         { return riderName; }
    /**
     * Returns the rider cnic.
     * @return the current rider cnic
     */
    public String getRiderCNIC()         { return riderCNIC; }
    /**
     * Returns the company name.
     * @return the current company name
     */
    public String getCompanyName()       { return companyName; }
    /**
     * Returns the vehicle number.
     * @return the current vehicle number
     */
    public String getVehicleNumber()     { return vehicleNumber; }
    /**
     * Returns the destination block.
     * @return the current destination block
     */
    public String getDestinationBlock()  { return destinationBlock; }
    /**
     * Returns the receiving host id.
     * @return the current receiving host id
     */
    public String getReceivingHostId()   { return receivingHostId; }
    /**
     * Returns the receiving host name.
     * @return the current receiving host name
     */
    public String getReceivingHostName() { return receivingHostName; }
    /**
     * Returns the entry zone id.
     * @return the current entry zone id
     */
    public String getEntryZoneId()       { return entryZoneId; }
    /**
     * Returns the exit zone id.
     * @return the current exit zone id
     */
    public String getExitZoneId()        { return exitZoneId; }
    /**
     * Returns the entry time.
     * @return the current entry time
     */
    public Timestamp getEntryTime()      { return entryTime; }
    /**
     * Returns the expected exit time.
     * @return the current expected exit time
     */
    public Timestamp getExpectedExitTime(){ return expectedExitTime; }
    /**
     * Returns the exit time.
     * @return the current exit time
     */
    public Timestamp getExitTime()       { return exitTime; }
    /**
     * Returns the duration minutes.
     * @return the current duration minutes
     */
    public long getDurationMinutes()     { return durationMinutes; }
    /**
     * Returns the status.
     * @return the current status
     */
    public String getStatus()            { return status; }
    /**
     * Returns the overstay flagged.
     * @return the current overstay flagged
     */
    public boolean isOverstayFlagged()   { return overstayFlagged; }
    /**
     * Returns the guard id.
     * @return the current guard id
     */
    public String getGuardId()           { return guardId; }

    // -------------------------------------------------------------------------
    // Setters (required for Firestore deserialization)
    // -------------------------------------------------------------------------

    /**
     * Sets the delivery id.
     * @param deliveryId the value to assign to {@code deliveryId}
     */
    public void setDeliveryId(String deliveryId)              { this.deliveryId        = deliveryId; }
    /**
     * Sets the rider id.
     * @param riderId the value to assign to {@code riderId}
     */
    public void setRiderId(String riderId)                    { this.riderId           = riderId; }
    /**
     * Sets the rider name.
     * @param riderName the value to assign to {@code riderName}
     */
    public void setRiderName(String riderName)                { this.riderName         = riderName; }
    /**
     * Sets the rider cnic.
     * @param riderCNIC the value to assign to {@code riderCNIC}
     */
    public void setRiderCNIC(String riderCNIC)                { this.riderCNIC         = riderCNIC; }
    /**
     * Sets the company name.
     * @param companyName the value to assign to {@code companyName}
     */
    public void setCompanyName(String companyName)            { this.companyName       = companyName; }
    /**
     * Sets the vehicle number.
     * @param vehicleNumber the value to assign to {@code vehicleNumber}
     */
    public void setVehicleNumber(String vehicleNumber)        { this.vehicleNumber     = vehicleNumber; }
    /**
     * Sets the destination block.
     * @param destinationBlock the value to assign to {@code destinationBlock}
     */
    public void setDestinationBlock(String destinationBlock)  { this.destinationBlock  = destinationBlock; }
    /**
     * Sets the receiving host id.
     * @param receivingHostId the value to assign to {@code receivingHostId}
     */
    public void setReceivingHostId(String receivingHostId)    { this.receivingHostId   = receivingHostId; }
    /**
     * Sets the receiving host name.
     * @param receivingHostName the value to assign to {@code receivingHostName}
     */
    public void setReceivingHostName(String receivingHostName){ this.receivingHostName = receivingHostName; }
    /**
     * Sets the entry zone id.
     * @param entryZoneId the value to assign to {@code entryZoneId}
     */
    public void setEntryZoneId(String entryZoneId)            { this.entryZoneId       = entryZoneId; }
    /**
     * Sets the exit zone id.
     * @param exitZoneId the value to assign to {@code exitZoneId}
     */
    public void setExitZoneId(String exitZoneId)              { this.exitZoneId        = exitZoneId; }
    /**
     * Sets the entry time.
     * @param entryTime the value to assign to {@code entryTime}
     */
    public void setEntryTime(Timestamp entryTime)             { this.entryTime         = entryTime; }
    /**
     * Sets the expected exit time.
     * @param expectedExitTime the value to assign to {@code expectedExitTime}
     */
    public void setExpectedExitTime(Timestamp expectedExitTime){ this.expectedExitTime = expectedExitTime; }
    /**
     * Sets the exit time.
     * @param exitTime the value to assign to {@code exitTime}
     */
    public void setExitTime(Timestamp exitTime)               { this.exitTime          = exitTime; }
    /**
     * Sets the duration minutes.
     * @param durationMinutes the value to assign to {@code durationMinutes}
     */
    public void setDurationMinutes(long durationMinutes)      { this.durationMinutes   = durationMinutes; }
    /**
     * Sets the status.
     * @param status the value to assign to {@code status}
     */
    public void setStatus(String status)                      { this.status            = status; }
    /**
     * Sets the overstay flagged.
     * @param overstayFlagged the value to assign to {@code overstayFlagged}
     */
    public void setOverstayFlagged(boolean overstayFlagged)   { this.overstayFlagged   = overstayFlagged; }
    /**
     * Sets the guard id.
     * @param guardId the value to assign to {@code guardId}
     */
    public void setGuardId(String guardId)                    { this.guardId           = guardId; }

    /**
     * Compares this model with another object for value equality.
     * @param o the object to compare with this instance
     * @return {@code true} when the supplied object represents the same model data
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeliveryLog)) return false;
        DeliveryLog that = (DeliveryLog) o;
        return Objects.equals(deliveryId, that.deliveryId);
    }

    /**
     * Computes the hash code for the identifying fields of this model.
     * @return the hash code for this model
     */
    @Override
    public int hashCode() { return Objects.hash(deliveryId); }

    /**
     * Returns a concise diagnostic string for this model.
     * @return a readable summary of this model
     */
    @Override
    public String toString() {
        return "DeliveryLog{riderId='" + riderId + "', status='" + status
                + "', duration=" + durationMinutes + "min}";
    }
}
