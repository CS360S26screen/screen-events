package com.example.se_proj;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;

import com.example.se_proj.databinding.ActivityGuardDashboardBinding;
import com.example.se_proj.models.Alert;
import com.example.se_proj.models.AuditLog;
import com.example.se_proj.models.RegisteredVehicle;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.AlertManager;
import com.example.se_proj.rules.BlacklistService;
import com.example.se_proj.rules.DeliveryTrackingService;
import com.example.se_proj.rules.ParkingOccupancyUtils;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.RequestValidationUtils;
import com.example.se_proj.rules.VisitWindowEvaluator;
import com.example.se_proj.rules.ZoneAccessLogger;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gate-operations dashboard for guards.
 *
 * <p>Extends the base check-in/check-out workflow with:</p>
 * <ul>
 *   <li><b>US18</b> — every scan (success or failure) is logged via {@link ZoneAccessLogger}
 *       to {@code zone_access_logs} and mirrored to {@code access_logs}.</li>
 *   <li><b>US19/20</b> — CNIC search first checks {@link BlacklistService}; a positive
 *       result triggers a high-priority {@link AlertManager} alert and shows a popup
 *       before the visitor request lookup proceeds.</li>
 *   <li><b>US21</b> — delivery entry and exit buttons open an input dialog and delegate
 *       to {@link DeliveryTrackingService} for lifecycle management.</li>
 * </ul>
 */
public class GuardDashboardActivity extends AppCompatActivity {

    public static final String EXTRA_GATE_MODE = "gate_mode";
    public static final String MODE_IN_GATE    = "in_gate";
    public static final String MODE_OUT_GATE   = "out_gate";

    private static final String TAG = "GuardDashboard";
    private static final String CARS_COLLECTION = "cars_registered";

    private interface OnVehicleValidated {
        void onValidated(RegisteredVehicle vehicle);
    }

    private ActivityGuardDashboardBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private VisitorRequest currentRequest = null;
    private String currentZoneId         = "main_gate";

    private ZoneAccessLogger       zoneAccessLogger;
    private BlacklistService       blacklistService;
    private AlertManager           alertManager;
    private DeliveryTrackingService deliveryService;

    private ListenerRegistration alertListenerReg;
    private int selectedGuardHomeTab = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGuardDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String guardId = currentGuardId();
        zoneAccessLogger = new ZoneAccessLogger(db, guardId);
        blacklistService = new BlacklistService(db);
        alertManager     = new AlertManager(db, guardId);
        deliveryService  = new DeliveryTrackingService(db, guardId, alertManager);

        setupToolbar();
        setupDrawer();
        setupBottomNav();
        setupSearchButtons();
        setupActionButtons();
        setupDeliveryButtons();
        startAlertListener();
        ensureParkingDocument();
        setupLiveSummary();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (alertListenerReg != null) alertListenerReg.remove();
    }

    private void startAlertListener() {
        alertListenerReg = alertManager.listenForHighPriorityAlerts(
                new AlertManager.AlertListener() {
                    @Override
                    public void onNewAlerts(List<Alert> alerts) {
                        for (Alert a : alerts) {
                            if (!a.isAcknowledged()) {
                                showAlertBannerDialog(a);
                                return;
                            }
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Alert listener error", e);
                    }
                });
    }

    private void showAlertBannerDialog(Alert alert) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("⚠ HIGH PRIORITY ALERT")
                .setMessage(alert.getMessage())
                .setCancelable(false)
                .setPositiveButton("Acknowledge", (dialog, which) ->
                        alertManager.acknowledgeAlert(alert.getAlertId(), null))
                .show();
    }

    private void handleCnicSearch() {
        String cnic = RequestValidationUtils.normalizeCnic(
                binding.etSearchCnic.getText().toString());
        if (cnic.isEmpty()) {
            Toast.makeText(this, "Please enter CNIC", Toast.LENGTH_SHORT).show();
            return;
        }

        blacklistService.checkBlacklist(cnic, new BlacklistService.BlacklistCheckCallback() {
            @Override
            public void onResult(boolean isBlacklisted,
                                 com.example.se_proj.models.BlacklistEntry entry) {
                if (isBlacklisted) {
                    alertManager.triggerHighPriorityAlert(entry, currentZoneId, null);
                    zoneAccessLogger.logAccess(
                            cnic, "person", entry.getEntityName(),
                            currentZoneId,
                            com.example.se_proj.models.ZoneAccessLog.ACTION_DENIED,
                            com.example.se_proj.models.ZoneAccessLog.OUTCOME_FAILURE,
                            "Blacklisted: " + entry.getReason(), "");
                    showBlacklistBlockDialog(entry);
                } else {
                    searchVisitorByCnic(cnic);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Blacklist check failed — proceeding with caution", e);
                Toast.makeText(GuardDashboardActivity.this,
                        "Blacklist check unavailable. Proceed with caution.",
                        Toast.LENGTH_LONG).show();
                searchVisitorByCnic(cnic);
            }
        });
    }

    private void showBlacklistBlockDialog(com.example.se_proj.models.BlacklistEntry entry) {
        String details = "Name: " + entry.getEntityName()
                + "\nID: " + entry.getEntityId()
                + "\nReason: " + entry.getReason()
                + "\nBan type: " + entry.getBanType();

        new AlertDialog.Builder(this)
                .setTitle("ACCESS DENIED — Blacklisted Entity")
                .setMessage(details
                        + "\n\nAccess has been denied. An alert has been sent to all guards.")
                .setCancelable(false)
                .setPositiveButton("OK", null)
                .show();

        binding.cvResult.setVisibility(View.GONE);
        binding.tvEmptyState.setVisibility(View.VISIBLE);
        binding.tvEmptyState.setText("ACCESS DENIED: Blacklisted entity.");
        currentRequest = null;
    }

    private void setupLiveSummary() {
        db.collection("visitor_requests")
                .whereEqualTo("onCampus", true)
                .addSnapshotListener((snapshots, e) -> {
                    int count = snapshots != null ? snapshots.size() : 0;
                    binding.tvGuardGuestCount.setText(String.valueOf(count));
                });

        db.collection("system_metadata").document("parking_status")
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        long occupancy = snapshot.getLong("currentOccupancy") != null
                                ? snapshot.getLong("currentOccupancy") : 0;
                        long capacity = snapshot.getLong("maxCapacity") != null
                                ? snapshot.getLong("maxCapacity") : 200;
                        binding.tvGuardParkingCount.setText(occupancy + "/" + capacity);
                    }
                });

        binding.btnViewParking.setOnClickListener(v ->
                startGuardPortalActivity(new Intent(this, MainParkingActivity.class), false));
    }

    private void searchVisitorByCnic(String cnic) {
        db.collection("visitor_requests")
                .whereEqualTo("guestCNIC", cnic)
                .whereEqualTo("status", RequestStatus.APPROVED)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) {
                        currentRequest = null;
                        binding.cvResult.setVisibility(View.GONE);
                        binding.tvEmptyState.setVisibility(View.VISIBLE);
                        binding.tvEmptyState.setText("No approved request found for this CNIC.");
                        zoneAccessLogger.logAccess(
                                cnic, "person", "Unknown",
                                currentZoneId,
                                com.example.se_proj.models.ZoneAccessLog.ACTION_DENIED,
                                com.example.se_proj.models.ZoneAccessLog.OUTCOME_FAILURE,
                                "No approved request found", "");
                    } else {
                        binding.tvEmptyState.setVisibility(View.GONE);
                        DocumentSnapshot doc = snapshots.getDocuments().get(0);
                        VisitorRequest request = doc.toObject(VisitorRequest.class);
                        if (request == null) return;
                        currentRequest = request.getRequestId().isEmpty()
                                ? request.withRequestId(doc.getId()) : request;
                        displayResult(currentRequest);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Unable to search visitor right now",
                                Toast.LENGTH_SHORT).show());
    }

    private void searchCurrentVisitorsByHost(String hostId) {
        db.collection("visitor_requests")
                .whereEqualTo("hostId", hostId)
                .whereEqualTo("onCampus", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) {
                        currentRequest = null;
                        binding.cvResult.setVisibility(View.GONE);
                        binding.tvEmptyState.setVisibility(View.VISIBLE);
                        binding.tvEmptyState.setText(
                                "No guests currently on campus for this Host ID.");
                    } else {
                        binding.tvEmptyState.setVisibility(View.GONE);
                        DocumentSnapshot doc = snapshots.getDocuments().get(0);
                        VisitorRequest request = doc.toObject(VisitorRequest.class);
                        if (request == null) return;
                        currentRequest = request.getRequestId().isEmpty()
                                ? request.withRequestId(doc.getId()) : request;
                        displayResult(currentRequest);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Unable to search host records right now",
                                Toast.LENGTH_SHORT).show());
    }

    private void displayResult(VisitorRequest request) {
        binding.cvResult.setVisibility(View.VISIBLE);
        binding.tvGuestName.setText(request.getGuestName());
        binding.tvHostInfo.setText("Host ID: " + request.getHostId()
                + " (" + request.getHostType() + ")");
        binding.tvTimeWindow.setText(request.getVisitDate() + " | "
                + request.getStartTime() + " - " + request.getEndTime());

        VisitWindowEvaluator.Decision decision =
                VisitWindowEvaluator.evaluate(request, LocalDate.now(), LocalTime.now());
        binding.tvStatus.setText(decision.getMessage());
        binding.btnAction.setText(decision.getActionText());
        binding.btnAction.setEnabled(decision.isActionEnabled());
        binding.btnOverride.setVisibility(decision.isOverrideVisible() ? View.VISIBLE : View.GONE);

        VisitWindowEvaluator.VisitWindowState state = decision.getState();
        if (state == VisitWindowEvaluator.VisitWindowState.INSIDE
                || state == VisitWindowEvaluator.VisitWindowState.AUTHORIZED) {
            setChipStatus(decision.getLabel(), R.color.status_approved_bg, R.color.status_approved_text);
        } else {
            setChipStatus(decision.getLabel(), R.color.status_denied_bg, R.color.status_denied_text);
        }

        if (decision.shouldLogDeniedAccess()) {
            zoneAccessLogger.logAccessForVisitor(
                    request, currentZoneId,
                    com.example.se_proj.models.ZoneAccessLog.ACTION_DENIED,
                    com.example.se_proj.models.ZoneAccessLog.OUTCOME_FAILURE,
                    decision.getDeniedReason());
        }
    }

    private void checkIn(VisitorRequest request) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        String scannedPlate = getScannedVehicleEntryPlate();
        if (!scannedPlate.isEmpty()) {
            validateVehicleForEntry(request, scannedPlate,
                    vehicle -> completeCheckIn(request, currentTime, "", vehicle));
            return;
        }
        completeCheckIn(request, currentTime, "", null);
    }

    private void completeCheckIn(VisitorRequest request, String currentTime, String reason,
                                 RegisteredVehicle vehicle) {
        Map<String, Object> visitorUpdates = new HashMap<>();
        visitorUpdates.put("entryTime", currentTime);
        visitorUpdates.put("onCampus", true);

        DocumentReference visitorRef =
                db.collection("visitor_requests").document(request.getRequestId());
        WriteBatch batch = db.batch();
        batch.update(visitorRef, visitorUpdates);

        if (vehicle != null) {
            Map<String, Object> vehicleUpdates = new HashMap<>();
            vehicleUpdates.put("onCampus", true);
            vehicleUpdates.put("entryTime", currentTime);
            vehicleUpdates.put("exitTime", null);
            batch.update(db.collection(CARS_COLLECTION).document(vehicle.getVehicleId()), vehicleUpdates);
        }

        batch.commit().addOnSuccessListener(unused -> {
            zoneAccessLogger.logAccessForVisitor(
                    request, currentZoneId,
                    com.example.se_proj.models.ZoneAccessLog.ACTION_ENTRY,
                    com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS, reason);
            logAudit(request, "Entry", reason);

            if (vehicle != null) {
                zoneAccessLogger.logAccess(
                        vehicle.getLicensePlate(),
                        ZoneAccessLogger.ENTITY_TYPE_VEHICLE,
                        vehicle.getStudentName(),
                        currentZoneId,
                        com.example.se_proj.models.ZoneAccessLog.ACTION_ENTRY,
                        com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS,
                        "Vehicle owner " + vehicle.getStudentRollNo()
                                + " matched scanned credential " + request.getHostId(),
                        request.getRequestId());
                logCarAudit(vehicle, "CAR_ENTRY_AUTHORIZED");
                updateParking(1);
                clearVehicleEntryPlate();
                Toast.makeText(this, "Vehicle and student credentials authorized",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Checked In", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Check-in failed: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
    }

    private void validateVehicleForEntry(VisitorRequest request, String plate,
                                         OnVehicleValidated callback) {
        String normalizedPlate = plate.toUpperCase(Locale.ROOT).trim();
        blacklistService.checkBlacklist(normalizedPlate, new BlacklistService.BlacklistCheckCallback() {
            @Override
            public void onResult(boolean isBlacklisted, com.example.se_proj.models.BlacklistEntry entry) {
                if (isBlacklisted) {
                    alertManager.triggerHighPriorityAlert(entry, currentZoneId, null);
                    blockVehicleEntry(request, normalizedPlate, "Blacklisted vehicle: " + entry.getReason(), null);
                    new AlertDialog.Builder(GuardDashboardActivity.this)
                            .setTitle("⚠ ACCESS DENIED — Blacklisted Vehicle")
                            .setMessage("Vehicle " + normalizedPlate + " is blacklisted.\nReason: " + entry.getReason()
                                    + "\n\nAn alert has been sent to all guards.")
                            .setCancelable(false)
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    proceedWithVehicleValidation(request, normalizedPlate, callback);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Blacklist check failed during validation", e);
                proceedWithVehicleValidation(request, normalizedPlate, callback);
            }
        });
    }

    private void proceedWithVehicleValidation(VisitorRequest request, String plate, OnVehicleValidated callback) {
        db.collection(CARS_COLLECTION)
                .whereEqualTo("licensePlate", plate)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) {
                        blockVehicleEntry(request, plate, "Vehicle credential not registered", null);
                        return;
                    }

                    DocumentSnapshot doc = snapshots.getDocuments().get(0);
                    RegisteredVehicle vehicle = doc.toObject(RegisteredVehicle.class);
                    if (vehicle == null) {
                        blockVehicleEntry(request, plate, "Vehicle credential unreadable", null);
                        return;
                    }
                    if (vehicle.getVehicleId().isEmpty()) {
                        vehicle.setVehicleId(doc.getId());
                    }

                    String scannedOwnerCredential = normalizeCredential(request.getHostId());
                    String registeredOwnerCredential =
                            normalizeCredential(vehicle.getStudentRollNo());
                    if (scannedOwnerCredential.isEmpty()
                            || registeredOwnerCredential.isEmpty()
                            || !registeredOwnerCredential.equals(scannedOwnerCredential)) {
                        blockVehicleEntry(request, plate,
                                "Vehicle owner does not match scanned student credential", vehicle);
                        return;
                    }

                    if (vehicle.isOnCampus()) {
                        blockVehicleEntry(request, plate, "Vehicle is already marked on campus",
                                vehicle);
                        return;
                    }

                    if (!vehicle.isActive()) {
                        blockVehicleEntry(request, plate, "Vehicle credential is inactive", vehicle);
                        return;
                    }

                    if (isVehicleCredentialExpired(vehicle)) {
                        blockVehicleEntry(request, plate, "Vehicle credential is expired", vehicle);
                        return;
                    }

                    callback.onValidated(vehicle);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to validate vehicle credential", e);
                    Toast.makeText(this, "Vehicle validation failed: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void blockVehicleEntry(VisitorRequest request, String plate, String reason,
                                   RegisteredVehicle vehicle) {
        String entityName = vehicle != null ? vehicle.getStudentName() : "Unknown vehicle";
        zoneAccessLogger.logAccess(
                plate,
                ZoneAccessLogger.ENTITY_TYPE_VEHICLE,
                entityName,
                currentZoneId,
                com.example.se_proj.models.ZoneAccessLog.ACTION_DENIED,
                com.example.se_proj.models.ZoneAccessLog.OUTCOME_FAILURE,
                reason,
                request.getRequestId());
        logAudit(request, "VEHICLE_ENTRY_DENIED", plate + " — " + reason);
        Toast.makeText(this, "Vehicle entry blocked: " + reason, Toast.LENGTH_LONG).show();
    }

    private String getScannedVehicleEntryPlate() {
        return binding.etVehicleEntryPlate.getText() != null
                ? binding.etVehicleEntryPlate.getText().toString().trim().toUpperCase(Locale.ROOT)
                : "";
    }

    private void clearVehicleEntryPlate() {
        if (binding.etVehicleEntryPlate.getText() != null) {
            binding.etVehicleEntryPlate.getText().clear();
        }
    }

    private String normalizeCredential(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private void findCarForEntry(String plate) {
        String normalizedPlate = plate.toUpperCase(Locale.ROOT).trim();
        blacklistService.checkBlacklist(normalizedPlate, new BlacklistService.BlacklistCheckCallback() {
            @Override
            public void onResult(boolean isBlacklisted, com.example.se_proj.models.BlacklistEntry entry) {
                if (isBlacklisted) {
                    alertManager.triggerHighPriorityAlert(entry, currentZoneId, null);
                    blockStandaloneVehicleEntry(normalizedPlate, "Blacklisted vehicle: " + entry.getReason(), null);
                    new AlertDialog.Builder(GuardDashboardActivity.this)
                            .setTitle("⚠ ACCESS DENIED — Blacklisted Vehicle")
                            .setMessage("Vehicle " + normalizedPlate + " is blacklisted.\nReason: " + entry.getReason()
                                    + "\n\nAn alert has been sent to all guards.")
                            .setCancelable(false)
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    proceedWithStandaloneCarEntryLookup(normalizedPlate);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Blacklist check failed for car entry", e);
                proceedWithStandaloneCarEntryLookup(normalizedPlate);
            }
        });
    }

    private void proceedWithStandaloneCarEntryLookup(String plate) {
        db.collection(CARS_COLLECTION)
                .whereEqualTo("licensePlate", plate)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) {
                        blockStandaloneVehicleEntry(
                                plate, "Vehicle credential not registered", null);
                        return;
                    }

                    DocumentSnapshot doc = snapshots.getDocuments().get(0);
                    RegisteredVehicle vehicle = doc.toObject(RegisteredVehicle.class);
                    if (vehicle == null) {
                        blockStandaloneVehicleEntry(
                                plate, "Vehicle credential unreadable", null);
                        return;
                    }
                    if (vehicle.getVehicleId().isEmpty()) {
                        vehicle.setVehicleId(doc.getId());
                    }

                    if (vehicle.isOnCampus()) {
                        blockStandaloneVehicleEntry(
                                plate, "Vehicle is already marked on campus", vehicle);
                        return;
                    }

                    if (!vehicle.isActive()) {
                        blockStandaloneVehicleEntry(
                                plate, "Vehicle credential is inactive", vehicle);
                        return;
                    }

                    if (isVehicleCredentialExpired(vehicle)) {
                        blockStandaloneVehicleEntry(
                                plate, "Vehicle credential is expired", vehicle);
                        return;
                    }

                    new AlertDialog.Builder(GuardDashboardActivity.this)
                            .setTitle("Car Found: " + vehicle.getLicensePlate())
                            .setMessage(buildVehicleOwnerMessage(vehicle))
                            .setPositiveButton("Check In Car",
                                    (d, w) -> performCarEntry(vehicle))
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Search failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void performCarEntry(RegisteredVehicle vehicle) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        Map<String, Object> updates = new HashMap<>();
        updates.put("onCampus", true);
        updates.put("entryTime", currentTime);
        updates.put("exitTime", null);

        db.collection(CARS_COLLECTION).document(vehicle.getVehicleId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    updateParking(1);
                    zoneAccessLogger.logAccess(
                            vehicle.getLicensePlate(),
                            ZoneAccessLogger.ENTITY_TYPE_VEHICLE,
                            vehicle.getStudentName(),
                            currentZoneId,
                            com.example.se_proj.models.ZoneAccessLog.ACTION_ENTRY,
                            com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS,
                            "CAR_ENTRY linked to owner " + vehicle.getStudentRollNo(),
                            "");
                    logCarAudit(vehicle, "CAR_ENTRY");
                    Toast.makeText(this, "Car checked in", Toast.LENGTH_SHORT).show();
                    clearVehicleEntryPlate();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update car: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private boolean isVehicleCredentialExpired(RegisteredVehicle vehicle) {
        return vehicle.getCredentialExpiresAt() != null
                && vehicle.getCredentialExpiresAt().compareTo(Timestamp.now()) < 0;
    }

    private void blockStandaloneVehicleEntry(String plate, String reason,
                                             RegisteredVehicle vehicle) {
        String entityName = vehicle != null ? vehicle.getStudentName() : "Unknown vehicle";
        zoneAccessLogger.logAccess(
                plate,
                ZoneAccessLogger.ENTITY_TYPE_VEHICLE,
                entityName,
                currentZoneId,
                com.example.se_proj.models.ZoneAccessLog.ACTION_DENIED,
                com.example.se_proj.models.ZoneAccessLog.OUTCOME_FAILURE,
                reason,
                "");
        Toast.makeText(this, "Vehicle entry blocked: " + reason, Toast.LENGTH_LONG).show();
    }

    private String buildVehicleOwnerMessage(RegisteredVehicle vehicle) {
        return vehicle.getCarModel()
                + "\nOwner: " + vehicle.getStudentName()
                + " (" + vehicle.getStudentRollNo() + ")";
    }

    private void findCarForExit(String plate) {
        db.collection(CARS_COLLECTION)
                .whereEqualTo("licensePlate", plate)
                .whereEqualTo("onCampus", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (snapshots == null || snapshots.isEmpty()) {
                        Toast.makeText(this,
                                "No on-campus vehicle found with plate: " + plate,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot doc = snapshots.getDocuments().get(0);
                    RegisteredVehicle vehicle = doc.toObject(RegisteredVehicle.class);
                    if (vehicle == null) return;
                    if (vehicle.getVehicleId().isEmpty()) {
                        vehicle.setVehicleId(doc.getId());
                    }

                    new AlertDialog.Builder(this)
                            .setTitle("Car Found: " + vehicle.getLicensePlate())
                            .setMessage(buildVehicleOwnerMessage(vehicle))
                            .setPositiveButton("Check Out Car",
                                    (d, w) -> performCarExit(vehicle, false))
                            .setNeutralButton("Driver Only Exit",
                                    (d, w) -> performCarExit(vehicle, true))
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Search failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void performCarExit(RegisteredVehicle vehicle, boolean driverOnly) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        Map<String, Object> updates = new HashMap<>();
        updates.put("onCampus", false);
        updates.put("exitTime", currentTime);
        db.collection(CARS_COLLECTION).document(vehicle.getVehicleId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    updateParking(-1);
                    String action = driverOnly ? "CAR_EXIT_DRIVER_ONLY" : "CAR_EXIT";
                    zoneAccessLogger.logAccess(
                            vehicle.getLicensePlate(),
                            ZoneAccessLogger.ENTITY_TYPE_VEHICLE,
                            vehicle.getStudentName(),
                            currentZoneId,
                            com.example.se_proj.models.ZoneAccessLog.ACTION_EXIT,
                            com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS,
                            action + " linked to owner " + vehicle.getStudentRollNo(),
                            "");
                    logCarAudit(vehicle, action);
                    String msg = driverOnly
                            ? "Car checked out (driver only — student remains on campus)"
                            : "Car checked out";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    if (binding.etCarExitPlate.getText() != null) {
                        binding.etCarExitPlate.getText().clear();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to update car: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void checkOut(VisitorRequest request) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        Map<String, Object> updates = new HashMap<>();
        updates.put("exitTime", currentTime);
        updates.put("onCampus", false);
        db.collection("visitor_requests").document(request.getRequestId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    zoneAccessLogger.logAccessForVisitor(
                            request, currentZoneId,
                            com.example.se_proj.models.ZoneAccessLog.ACTION_EXIT,
                            com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS, "");
                    Toast.makeText(this, "Checked Out", Toast.LENGTH_SHORT).show();
                    logAudit(request, "Exit", "");
                });
    }

    private void manualOverride(VisitorRequest request) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        String scannedPlate = getScannedVehicleEntryPlate();
        if (!scannedPlate.isEmpty()) {
            validateVehicleForEntry(request, scannedPlate,
                    vehicle -> completeCheckIn(request, currentTime, "Supervisor Override", vehicle));
            return;
        }
        completeCheckIn(request, currentTime, "Supervisor Override", null);
    }

    private void showDeliveryEntryDialog() {
        LinearLayout layout = buildDeliveryEntryLayout();
        EditText etName  = (EditText) layout.getChildAt(0);
        EditText etCnic  = (EditText) layout.getChildAt(1);
        EditText etCompany = (EditText) layout.getChildAt(2);
        EditText etPlate = (EditText) layout.getChildAt(3);
        EditText etDest  = (EditText) layout.getChildAt(4);
        EditText etHostId = (EditText) layout.getChildAt(5);
        EditText etHostName = (EditText) layout.getChildAt(6);

        new AlertDialog.Builder(this)
                .setTitle("Register Delivery Entry")
                .setView(layout)
                .setPositiveButton("Register", (dialog, which) -> {
                    String name  = etName.getText().toString().trim();
                    String cnic  = RequestValidationUtils.normalizeCnic(etCnic.getText().toString());
                    String company = etCompany.getText().toString().trim();
                    String plate = etPlate.getText().toString().trim();
                    String dest  = etDest.getText().toString().trim();
                    String hostId = etHostId.getText().toString().trim();
                    String hostName = etHostName.getText().toString().trim();

                    if (name.isEmpty() || cnic.isEmpty() || company.isEmpty()
                            || plate.isEmpty() || dest.isEmpty()
                            || hostId.isEmpty() || hostName.isEmpty()) {
                        Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    deliveryService.startDelivery(
                            cnic, name, cnic, company, plate, dest, hostId, hostName, currentZoneId,
                            new DeliveryTrackingService.DeliveryCallback() {
                                @Override
                                public void onSuccess(String deliveryId) {
                                    Toast.makeText(GuardDashboardActivity.this, "Delivery registered.", Toast.LENGTH_SHORT).show();
                                    zoneAccessLogger.logAccess(
                                            cnic, "person", name, currentZoneId,
                                            com.example.se_proj.models.ZoneAccessLog.ACTION_ENTRY,
                                            com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS,
                                            "", deliveryId);
                                }
                                @Override
                                public void onError(Exception e) {
                                    Toast.makeText(GuardDashboardActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeliveryExitDialog() {
        EditText etCnic = new EditText(this);
        etCnic.setHint("Rider CNIC (13 digits)");
        new AlertDialog.Builder(this)
                .setTitle("Register Delivery Exit")
                .setView(etCnic)
                .setPositiveButton("Exit", (dialog, which) -> {
                    String cnic = RequestValidationUtils.normalizeCnic(etCnic.getText().toString());
                    deliveryService.endDelivery(cnic, currentZoneId, new DeliveryTrackingService.DeliveryCallback() {
                        @Override
                        public void onSuccess(String deliveryId) {
                            Toast.makeText(GuardDashboardActivity.this, "Exit logged.", Toast.LENGTH_SHORT).show();
                            zoneAccessLogger.logAccess(
                                    cnic, "person", "Delivery Rider", currentZoneId,
                                    com.example.se_proj.models.ZoneAccessLog.ACTION_EXIT,
                                    com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS,
                                    "", deliveryId);
                        }
                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(GuardDashboardActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout buildDeliveryEntryLayout() {
        int padding = (int) (8 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding * 2, padding, padding * 2, padding);
        EditText etName  = new EditText(this); etName.setHint("Name");
        EditText etCnic  = new EditText(this); etCnic.setHint("CNIC");
        EditText etCompany = new EditText(this); etCompany.setHint("Company");
        EditText etPlate = new EditText(this); etPlate.setHint("Plate");
        EditText etDest  = new EditText(this); etDest.setHint("Destination");
        EditText etHostId = new EditText(this); etHostId.setHint("Host ID");
        EditText etHostName = new EditText(this); etHostName.setHint("Host Name");
        layout.addView(etName); layout.addView(etCnic); layout.addView(etCompany);
        layout.addView(etPlate); layout.addView(etDest); layout.addView(etHostId); layout.addView(etHostName);
        return layout;
    }

    private void setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private void setupDrawer() {
        binding.toolbar.setNavigationOnClickListener(v ->
                binding.drawerLayout.openDrawer(GravityCompat.START));
        binding.drawerNavigation.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.drawer_in_gate) {
                currentZoneId = "in_gate";
                binding.toolbar.setTitle("In-Gate - Live Dashboard");
            } else if (id == R.id.drawer_out_gate) {
                currentZoneId = "out_gate";
                binding.toolbar.setTitle("Out-Gate - Live Dashboard");
            } else if (id == R.id.drawer_main_parking) {
                startGuardPortalActivity(new Intent(this, MainParkingActivity.class), false);
            } else if (id == R.id.drawer_wing_scanner) {
                startGuardPortalActivity(new Intent(this, WingScannerActivity.class), false);
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_logs) {
                startGuardPortalActivity(new Intent(this, AdminAuditActivity.class), false);
                return true;
            } else if (id == R.id.nav_adhoc) {
                startGuardPortalActivity(new Intent(this, WalkInRegistrationActivity.class), false);
                return true;
            } else if (id == R.id.nav_settings) {
                Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
    }

    private void startGuardPortalActivity(Intent intent, boolean finishCurrent) {
        startActivity(intent);
        overridePendingTransition(0, 0);
        if (finishCurrent) {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    private void selectGuardHomeTab(int index, boolean animate) {
        selectedGuardHomeTab = index;
        View selectedTab = index == 0
                ? binding.tabGuests
                : index == 1 ? binding.tabDelivery : binding.tabCars;

        binding.guardHomeTabContainer.post(() -> {
            int targetWidth = selectedTab.getWidth();
            if (targetWidth <= 0) return;

            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) binding.guardHomeTabHighlight.getLayoutParams();
            if (params.width != targetWidth) {
                params.width = targetWidth;
                binding.guardHomeTabHighlight.setLayoutParams(params);
            }

            float targetX = binding.guardHomeTabRow.getX() + selectedTab.getX();
            if (animate) {
                binding.guardHomeTabHighlight.animate()
                        .x(targetX)
                        .setDuration(220)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            } else {
                binding.guardHomeTabHighlight.setX(targetX);
            }
        });

        updateGuardHomeTabText(binding.tabGuests, index == 0);
        updateGuardHomeTabButton(binding.tabDelivery, index == 1);
        updateGuardHomeTabButton(binding.tabCars, index == 2);
        binding.cardVerifyGuest.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        binding.cardFindCurrentGuest.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        binding.cardDeliveryTracking.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        binding.carTrackingSection.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        if (index != 0) {
            binding.cvResult.setVisibility(View.GONE);
            binding.tvEmptyState.setVisibility(View.GONE);
        }
    }

    private void updateGuardHomeTabText(TextView tab, boolean selected) {
        int color = selected ? Color.WHITE : Color.parseColor("#3D4A5C");
        tab.setTextColor(color);
        tab.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        tab.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void updateGuardHomeTabButton(MaterialButton tab, boolean selected) {
        int color = selected ? Color.WHITE : Color.parseColor("#3D4A5C");
        tab.setTextColor(color);
        tab.setIconTint(ColorStateList.valueOf(color));
        tab.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void setupSearchButtons() {
        binding.btnSearchCnic.setOnClickListener(v -> handleCnicSearch());
        binding.btnSearchRoll.setOnClickListener(v -> {
            String roll = binding.etSearchRoll.getText().toString().trim();
            if (!roll.isEmpty()) searchCurrentVisitorsByHost(roll);
        });
        binding.btnFindCar.setOnClickListener(v -> {
            String plate = binding.etCarExitPlate.getText().toString().trim().toUpperCase(Locale.ROOT);
            if (!plate.isEmpty()) findCarForExit(plate);
        });
        binding.btnEnterCar.setOnClickListener(v -> {
            String plate = getScannedVehicleEntryPlate();
            if (!plate.isEmpty()) findCarForEntry(plate);
        });
    }

    private void setupActionButtons() {
        binding.btnAction.setOnClickListener(v -> {
            if (currentRequest == null) return;
            if (currentRequest.isOnCampus()) checkOut(currentRequest); else checkIn(currentRequest);
        });
        binding.btnOverride.setOnClickListener(v -> {
            if (currentRequest != null) manualOverride(currentRequest);
        });
    }

    private void setupDeliveryButtons() {
        try {
            binding.guardHomeTabContainer.post(() ->
                    selectGuardHomeTab(selectedGuardHomeTab, false));
            binding.tabGuests.setOnClickListener(v -> selectGuardHomeTab(0, true));
            binding.tabDelivery.setOnClickListener(v -> selectGuardHomeTab(1, true));
            binding.tabCars.setOnClickListener(v -> selectGuardHomeTab(2, true));
            binding.btnDeliveryEntry.setOnClickListener(v -> showDeliveryEntryDialog());
            binding.btnDeliveryExit.setOnClickListener(v -> showDeliveryExitDialog());
        } catch (Exception e) {
            Log.w(TAG, "Delivery buttons not found in layout — delivery UI unavailable. "
                    + "Add btnDeliveryEntry and btnDeliveryExit to activity_guard_dashboard.xml");
        }
    }

    private void setChipStatus(String text, int bgColorRes, int textColorRes) {
        binding.chipStatus.setText(text);
        binding.chipStatus.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(this, bgColorRes)));
        binding.chipStatus.setTextColor(ContextCompat.getColor(this, textColorRes));
    }

    private void logAudit(VisitorRequest request, String action, String reason) {
        AuditLog log = new AuditLog("", request.getGuestName(), request.getGuestCNIC(), request.getHostId(), action, reason, currentGuardId(), Timestamp.now());
        db.collection("access_logs").add(log);
    }

    private void logCarAudit(RegisteredVehicle vehicle, String action) {
        AuditLog log = new AuditLog("", vehicle.getStudentName(), "", vehicle.getStudentRollNo(), action, vehicle.getLicensePlate() + " — " + vehicle.getCarModel(), currentGuardId(), Timestamp.now());
        db.collection("access_logs").add(log);
    }

    private void ensureParkingDocument() {
        DocumentReference docRef = db.collection("system_metadata").document("parking_status");
        docRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Map<String, Object> data = new HashMap<>(); data.put("currentOccupancy", 0L); data.put("maxCapacity", 200L);
                docRef.set(data);
            }
        });
    }

    private void updateParking(long delta) {
        DocumentReference documentRef = db.collection("system_metadata").document("parking_status");
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(documentRef);
            long current = snapshot.exists() ? snapshot.getLong("currentOccupancy") : 0L;
            long capacity = snapshot.exists() ? snapshot.getLong("maxCapacity") : 200L;
            long updated = ParkingOccupancyUtils.clampOccupancy(current, delta, capacity);
            transaction.update(documentRef, "currentOccupancy", updated);
            return null;
        }).addOnFailureListener(e -> Log.e(TAG, "Parking update failed", e));
    }

    private String currentGuardId() {
        return FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
    }
}
