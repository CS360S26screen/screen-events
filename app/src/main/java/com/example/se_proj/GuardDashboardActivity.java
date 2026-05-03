package com.example.se_proj;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
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
 *
 * <h3>Scan flow</h3>
 * <pre>
 * Guard types CNIC → checkBlacklist()
 *   ├─ BLACKLISTED → triggerHighPriorityAlert() → logAccess(DENIED) → show alert popup
 *   └─ CLEAR       → searchVisitorByCnic()
 *                       ├─ NOT FOUND → logAccess(DENIED, "No approved request")
 *                       └─ FOUND     → VisitWindowEvaluator → logAccess(SUCCESS/FAILURE) → UI
 * </pre>
 *
 * <p>The current zone ID defaults to {@link #currentZoneId} and is updated when the guard
 * selects In-Gate or Out-Gate from the navigation drawer.</p>
 * <p>Supports visitor check-in (with optional car entry selection) and separate car exit
 * and student exit flows. Parking occupancy is updated automatically on each entry/exit.</p>
 */
public class GuardDashboardActivity extends AppCompatActivity {

    public static final String EXTRA_GATE_MODE = "gate_mode";
    public static final String MODE_IN_GATE    = "in_gate";
    public static final String MODE_OUT_GATE   = "out_gate";

    private static final String TAG = "GuardDashboard";

    private static final String TAG = "GuardDashboard";

    private ActivityGuardDashboardBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private VisitorRequest currentRequest = null;
    private String currentZoneId         = "main_gate";

    // US18-21 service instances — initialised in onCreate after UID is available
    private ZoneAccessLogger       zoneAccessLogger;
    private BlacklistService       blacklistService;
    private AlertManager           alertManager;
    private DeliveryTrackingService deliveryService;

    /** Real-time alert listener — must be removed in onDestroy. */
    private ListenerRegistration alertListenerReg;

    // =========================================================================
    // Lifecycle
    // =========================================================================

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
    }
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return true;
            }
            return false;
        });

        ensureParkingDocument();
        setupLiveSummary();

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (alertListenerReg != null) alertListenerReg.remove();
    }

    // =========================================================================
    // US20 — real-time alert listener
    // =========================================================================

    /**
     * Attaches a real-time Firestore listener so this guard immediately sees any
     * high-priority alert written by themselves or by another guard.
     *
     * When alerts arrive, a non-dismissible banner dialog is shown if there is at
     * least one unacknowledged HIGH priority alert.
     */
    private void startAlertListener() {
        alertListenerReg = alertManager.listenForHighPriorityAlerts(
                new AlertManager.AlertListener() {
                    @Override
                    public void onNewAlerts(List<Alert> alerts) {
                        for (Alert a : alerts) {
                            if (!a.isAcknowledged()) {
                                showAlertBannerDialog(a);
                                return; // show one at a time
                            }
                        }
                    }
        binding.drawerNavigation.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.drawer_in_gate) {
                binding.toolbar.setTitle("In-Gate - Live Dashboard");
            } else if (id == R.id.drawer_out_gate) {
                binding.toolbar.setTitle("Out-Gate - Live Dashboard");
            } else if (id == R.id.drawer_main_parking) {
                startActivity(new Intent(this, MainParkingActivity.class));
            } else if (id == R.id.drawer_wing_scanner) {
                startActivity(new Intent(this, WingScannerActivity.class));
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Alert listener error", e);
                    }
                });
    }

    /**
     * Shows a full-screen modal alert dialog for a high-priority event.
     * The guard must tap "Acknowledge" to dismiss, which marks the alert in Firestore.
     */
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

    // =========================================================================
    // US19/20 — blacklist-aware CNIC search
    // =========================================================================

    /**
     * Entry point for the Search CNIC button.
     * Runs the blacklist check first; only proceeds to visitor-request lookup if clear.
     */
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
                    // US20: trigger alert BEFORE blocking gate
                    alertManager.triggerHighPriorityAlert(entry, currentZoneId, null);
                    // US18: log the denied zone-access attempt
                    zoneAccessLogger.logAccess(
                            cnic, "person", entry.getEntityName(),
                            currentZoneId,
                            com.example.se_proj.models.ZoneAccessLog.ACTION_DENIED,
                            com.example.se_proj.models.ZoneAccessLog.OUTCOME_FAILURE,
                            "Blacklisted: " + entry.getReason(), "");
                    showBlacklistBlockDialog(entry);
                } else {
                    // Entity is clear — proceed to visitor request lookup
                    searchVisitorByCnic(cnic);
                }
            }

            @Override
            public void onError(Exception e) {
                // Fail-open: if we cannot reach Firestore, proceed to request lookup
                // and log the check failure so the admin can investigate
                Log.e(TAG, "Blacklist check failed — proceeding with caution", e);
                Toast.makeText(GuardDashboardActivity.this,
                        "Blacklist check unavailable. Proceed with caution.",
                        Toast.LENGTH_LONG).show();
                searchVisitorByCnic(cnic);
            }
        binding.btnAction.setOnClickListener(v -> {
            if (currentRequest == null) return;
            VisitorRequest request = currentRequest;
            String action = request.isOnCampus() ? "Check-Out" : "Check-In";
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Action")
                    .setMessage("Are you sure you want to " + action
                            + " " + request.getGuestName() + "?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        if (request.isOnCampus()) checkOut(request);
                        else checkIn(request);
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    /** Shows a blocking dialog when a blacklisted CNIC is scanned. */
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

        // Hide the result card — no visitor request to show
        binding.cvResult.setVisibility(View.GONE);
        binding.tvEmptyState.setVisibility(View.VISIBLE);
        binding.tvEmptyState.setText("ACCESS DENIED: Blacklisted entity.");
        currentRequest = null;
    }

    // =========================================================================
    // Visitor request lookup (existing logic, now with US18 logging)
    // =========================================================================
        binding.btnOverride.setOnClickListener(v -> {
            if (currentRequest == null) return;
            VisitorRequest request = currentRequest;
            new AlertDialog.Builder(this)
                    .setTitle("Supervisor Override")
                    .setMessage("Manually override entry restrictions for "
                            + request.getGuestName() + "?")
                    .setPositiveButton("Confirm Override",
                            (dialog, which) -> manualOverride(request))
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.btnFindCar.setOnClickListener(v -> {
            String plate = binding.etCarExitPlate.getText() != null
                    ? binding.etCarExitPlate.getText().toString().trim().toUpperCase() : "";
            if (plate.isEmpty()) {
                Toast.makeText(this, "Please enter a license plate", Toast.LENGTH_SHORT).show();
            } else {
                findCarForExit(plate);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Live summary (guests on campus + parking)
    // -------------------------------------------------------------------------

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
                startActivity(new Intent(this, MainParkingActivity.class)));
    }

    // -------------------------------------------------------------------------
    // Visitor search
    // -------------------------------------------------------------------------

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
                        // US18: log the failed lookup
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

    // =========================================================================
    // US18 — enhanced displayResult with zone-access logging
    // =========================================================================
    // -------------------------------------------------------------------------
    // Result display
    // -------------------------------------------------------------------------

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

        // US18: log denied access (guard against duplicate logs on repeated searches
        // by only logging when the window evaluator produces a new denial reason)
        if (decision.shouldLogDeniedAccess()) {
            zoneAccessLogger.logAccessForVisitor(
                    request, currentZoneId,
                    com.example.se_proj.models.ZoneAccessLog.ACTION_DENIED,
                    com.example.se_proj.models.ZoneAccessLog.OUTCOME_FAILURE,
                    decision.getDeniedReason());
        }
    }

    // =========================================================================
    // Check-in / check-out / override — now with US18 zone logging
    // =========================================================================

    // -------------------------------------------------------------------------
    // Check-in (visitor + optional car)
    // -------------------------------------------------------------------------

    private void checkIn(VisitorRequest request) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        Map<String, Object> updates = new HashMap<>();
        updates.put("entryTime", currentTime);
        updates.put("onCampus",  true);
        db.collection("visitor_requests").document(request.getRequestId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    zoneAccessLogger.logAccessForVisitor(
                            request, currentZoneId,
                            com.example.se_proj.models.ZoneAccessLog.ACTION_ENTRY,
                            com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS, "");
                    updateParking(1);
                    logAudit(request, "Entry", "");
                    Toast.makeText(this, "Checked In", Toast.LENGTH_SHORT).show();
                    promptCarSelection(request.getHostId());
                });
    }

    /** After a successful visitor check-in, ask the guard which registered car entered. */
    private void promptCarSelection(String studentRollNo) {
        db.collection("registered_vehicles")
                .whereEqualTo("studentRollNo", studentRollNo)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<RegisteredVehicle> vehicles = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        RegisteredVehicle v = doc.toObject(RegisteredVehicle.class);
                        if (v != null) vehicles.add(v);
                    }

                    if (vehicles.isEmpty()) {
                        // No registered cars — update parking directly
                        updateParking(1);
                        return;
                    }

                    String[] options = new String[vehicles.size() + 1];
                    for (int i = 0; i < vehicles.size(); i++) {
                        options[i] = vehicles.get(i).getLicensePlate()
                                + " — " + vehicles.get(i).getCarModel();
                    }
                    options[vehicles.size()] = "No Car";

                    new AlertDialog.Builder(this)
                            .setTitle("Did a car enter?")
                            .setItems(options, (dialog, which) -> {
                                if (which < vehicles.size()) {
                                    markCarOnCampus(vehicles.get(which));
                                } else {
                                    updateParking(1);
                                }
                            })
                            .setCancelable(false)
                            .show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch registered vehicles", e);
                    updateParking(1);
                });
    }

    private void markCarOnCampus(RegisteredVehicle vehicle) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        Map<String, Object> updates = new HashMap<>();
        updates.put("onCampus", true);
        updates.put("entryTime", currentTime);
        db.collection("registered_vehicles").document(vehicle.getVehicleId())
                .update(updates)
                .addOnSuccessListener(unused -> updateParking(1))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to mark car on campus", e);
                    updateParking(1);
                    Toast.makeText(this, "Warning: could not mark car as on campus",
                            Toast.LENGTH_SHORT).show();
                });
    }

    // -------------------------------------------------------------------------
    // Car exit flow
    // -------------------------------------------------------------------------

    private void findCarForExit(String plate) {
        db.collection("registered_vehicles")
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

                    new AlertDialog.Builder(this)
                            .setTitle("Car Found: " + vehicle.getLicensePlate())
                            .setMessage(vehicle.getCarModel() + "\nStudent: "
                                    + vehicle.getStudentName()
                                    + " (" + vehicle.getStudentRollNo() + ")")
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
        db.collection("registered_vehicles").document(vehicle.getVehicleId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    updateParking(-1);
                    String action = driverOnly ? "CAR_EXIT_DRIVER_ONLY" : "CAR_EXIT";
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

    // -------------------------------------------------------------------------
    // Check-out (visitor)
    // -------------------------------------------------------------------------

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
                    updateParking(-1);
                    Toast.makeText(this, "Checked Out", Toast.LENGTH_SHORT).show();
                    logAudit(request, "Exit", "");
                    // Parking decrement for the person (car exit is handled separately via plate)
                    Toast.makeText(this, "Visitor Checked Out", Toast.LENGTH_SHORT).show();
                });
    }

    private void manualOverride(VisitorRequest request) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        Map<String, Object> updates = new HashMap<>();
        updates.put("entryTime", currentTime);
        updates.put("onCampus",  true);
        db.collection("visitor_requests").document(request.getRequestId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    zoneAccessLogger.logAccessForVisitor(
                            request, currentZoneId,
                            com.example.se_proj.models.ZoneAccessLog.ACTION_ENTRY,
                            com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS,
                            "Supervisor Override");
                    updateParking(1);
                    logAudit(request, "Entry", "Supervisor Override");
                    promptCarSelection(request.getHostId());
                    Toast.makeText(this, "Override successful", Toast.LENGTH_SHORT).show();
                });
    }

    // =========================================================================
    // US21 — delivery entry / exit dialogs
    // =========================================================================

    /** Shows an input dialog for registering a new delivery rider on campus. */
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
                    if (cnic.length() != 13) {
                        Toast.makeText(this, "CNIC must be 13 digits", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    deliveryService.startDelivery(
                            cnic, name, cnic, company, plate, dest, hostId, hostName, currentZoneId,
                            new DeliveryTrackingService.DeliveryCallback() {
                                @Override
                                public void onSuccess(String deliveryId) {
                                    Toast.makeText(GuardDashboardActivity.this,
                                            "Delivery registered. Window: "
                                                    + DeliveryTrackingService.DELIVERY_WINDOW_MINUTES
                                                    + " min. ID: " + deliveryId,
                                            Toast.LENGTH_LONG).show();
                                    // US18: log delivery entry
                                    zoneAccessLogger.logAccess(
                                            cnic, "person", name, currentZoneId,
                                            com.example.se_proj.models.ZoneAccessLog.ACTION_ENTRY,
                                            com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS,
                                            "", deliveryId);
                                }

                                @Override
                                public void onError(Exception e) {
                                    Toast.makeText(GuardDashboardActivity.this,
                                            "Delivery entry failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Shows an input dialog to close out a delivery rider. */
    private void showDeliveryExitDialog() {
        EditText etCnic = new EditText(this);
        etCnic.setHint("Rider CNIC (13 digits)");
        etCnic.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        etCnic.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("Register Delivery Exit")
                .setView(etCnic)
                .setPositiveButton("Exit", (dialog, which) -> {
                    String cnic = RequestValidationUtils.normalizeCnic(etCnic.getText().toString());
                    if (cnic.length() != 13) {
                        Toast.makeText(this, "CNIC must be 13 digits", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    deliveryService.endDelivery(
                            cnic, currentZoneId,
                            new DeliveryTrackingService.DeliveryCallback() {
                                @Override
                                public void onSuccess(String deliveryId) {
                                    Toast.makeText(GuardDashboardActivity.this,
                                            "Delivery exit logged. ID: " + deliveryId,
                                            Toast.LENGTH_SHORT).show();
                                    // US18: log delivery exit
                                    zoneAccessLogger.logAccess(
                                            cnic, "person", "Delivery Rider", currentZoneId,
                                            com.example.se_proj.models.ZoneAccessLog.ACTION_EXIT,
                                            com.example.se_proj.models.ZoneAccessLog.OUTCOME_SUCCESS,
                                            "", deliveryId);
                                }

                                @Override
                                public void onError(Exception e) {
                                    Toast.makeText(GuardDashboardActivity.this,
                                            "Delivery exit failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Builds the multi-field input layout for delivery entry registration. */
    private LinearLayout buildDeliveryEntryLayout() {
        int padding = (int) (8 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding * 2, padding, padding * 2, padding);

        EditText etName  = new EditText(this); etName.setHint("Rider Full Name");
        EditText etCnic  = new EditText(this); etCnic.setHint("Rider CNIC (13 digits)");
        etCnic.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText etCompany = new EditText(this); etCompany.setHint("Company");
        EditText etPlate = new EditText(this); etPlate.setHint("Vehicle Plate (e.g. ABC-123)");
        EditText etDest  = new EditText(this); etDest.setHint("Destination Block");
        EditText etHostId = new EditText(this); etHostId.setHint("Receiving Host/Faculty ID");
        EditText etHostName = new EditText(this); etHostName.setHint("Receiving Host/Faculty Name");

        layout.addView(etName);
        layout.addView(etCnic);
        layout.addView(etCompany);
        layout.addView(etPlate);
        layout.addView(etDest);
        layout.addView(etHostId);
        layout.addView(etHostName);
        return layout;
    }

    // =========================================================================
    // UI setup helpers
    // =========================================================================

    private void setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
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
                startActivity(new Intent(this, MainParkingActivity.class));
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
                startActivity(new Intent(this, AdminAuditActivity.class));
                return true;
            } else if (id == R.id.nav_adhoc) {
                startActivity(new Intent(this, WalkInRegistrationActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
    }

    private void setupSearchButtons() {
        // US19/20: search now goes through blacklist first
        binding.btnSearchCnic.setOnClickListener(v -> handleCnicSearch());

        binding.btnSearchRoll.setOnClickListener(v -> {
            String roll = binding.etSearchRoll.getText().toString().trim();
            if (!roll.isEmpty()) {
                searchCurrentVisitorsByHost(roll);
            } else {
                Toast.makeText(this, "Please enter Host Roll Number", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupActionButtons() {
        binding.btnAction.setOnClickListener(v -> {
            if (currentRequest == null) return;
            VisitorRequest request = currentRequest;
            String action = request.isOnCampus() ? "Check-Out" : "Check-In";
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Action")
                    .setMessage("Are you sure you want to " + action
                            + " " + request.getGuestName() + "?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        if (request.isOnCampus()) checkOut(request); else checkIn(request);
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        binding.btnOverride.setOnClickListener(v -> {
            if (currentRequest == null) return;
            VisitorRequest request = currentRequest;
            new AlertDialog.Builder(this)
                    .setTitle("Supervisor Override")
                    .setMessage("Manually override entry restrictions for "
                            + request.getGuestName() + "?")
                    .setPositiveButton("Confirm Override",
                            (dialog, which) -> manualOverride(request))
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    /**
     * US21 — wires up delivery entry/exit buttons.
     *
     * Requires two buttons with IDs {@code btnDeliveryEntry} and {@code btnDeliveryExit}
     * in {@code activity_guard_dashboard.xml}. If the IDs are absent the catch block
     * logs a warning and the feature degrades gracefully without crashing.
     */
    private void setupDeliveryButtons() {
        try {
            binding.getRoot().findViewById(R.id.btnDeliveryEntry)
                    .setOnClickListener(v -> showDeliveryEntryDialog());
            binding.getRoot().findViewById(R.id.btnDeliveryExit)
                    .setOnClickListener(v -> showDeliveryExitDialog());
        } catch (Exception e) {
            Log.w(TAG, "Delivery buttons not found in layout — delivery UI unavailable. "
                    + "Add btnDeliveryEntry and btnDeliveryExit to activity_guard_dashboard.xml");
        }
    }

    // =========================================================================
    // Shared helpers (unchanged from original)
    // =========================================================================

    private void setChipStatus(String text, int bgColorRes, int textColorRes) {
        binding.chipStatus.setText(text);
        binding.chipStatus.setChipBackgroundColor(
                ColorStateList.valueOf(ContextCompat.getColor(this, bgColorRes)));
        binding.chipStatus.setTextColor(ContextCompat.getColor(this, textColorRes));
    }
    // -------------------------------------------------------------------------
    // Audit logging
    // -------------------------------------------------------------------------

    private void logAudit(VisitorRequest request, String action, String reason) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        AuditLog log = new AuditLog(
                "", request.getGuestName(), request.getGuestCNIC(), request.getHostId(),
                action, reason, uid, Timestamp.now()
        );
        db.collection("access_logs").add(log)
                .addOnFailureListener(e ->
                        Log.e(TAG, "Audit logging failed: " + e.getMessage()));
    }

    private void logCarAudit(RegisteredVehicle vehicle, String action) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        AuditLog log = new AuditLog(
                "", vehicle.getStudentName(), "", vehicle.getStudentRollNo(),
                action,
                vehicle.getLicensePlate() + " — " + vehicle.getCarModel(),
                uid, Timestamp.now()
        );
        db.collection("access_logs").add(log)
                .addOnFailureListener(e ->
                        Log.e(TAG, "Car audit logging failed: " + e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Parking occupancy
    // -------------------------------------------------------------------------

    private void ensureParkingDocument() {
        com.google.firebase.firestore.DocumentReference docRef =
                db.collection("system_metadata").document("parking_status");
        docRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Map<String, Object> data = new HashMap<>();
                data.put("currentOccupancy", 0L);
                data.put("maxCapacity", 200L);
                docRef.set(data).addOnFailureListener(e ->
                        Log.e(TAG, "Failed to initialize parking document", e));
            }
        });
    }

    private void updateParking(long delta) {
        com.google.firebase.firestore.DocumentReference documentRef =
                db.collection("system_metadata").document("parking_status");

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(documentRef);
            long currentOccupancy = snapshot.exists()
                    && snapshot.getLong("currentOccupancy") != null
                    ? snapshot.getLong("currentOccupancy") : 0L;
            long maxCapacity = snapshot.exists()
                    && snapshot.getLong("maxCapacity") != null
                    ? snapshot.getLong("maxCapacity") : 200L;

            long updated = ParkingOccupancyUtils.clampOccupancy(
                    currentOccupancy, delta, maxCapacity);

            if (!snapshot.exists()) {
                Map<String, Object> data = new HashMap<>();
                data.put("currentOccupancy", updated);
                data.put("maxCapacity", maxCapacity);
                transaction.set(documentRef, data);
            } else {
                transaction.update(documentRef, "currentOccupancy", updated);
            }
            return null;
        }).addOnSuccessListener(unused ->
                Log.d(TAG, "Parking updated successfully")
        ).addOnFailureListener(e -> {
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Parking update failed", e);
            String errorMsg;
            if (e instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) e).getCode()
                    == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                errorMsg = "Permission Denied: Only Admins can update parking settings.";
            } else {
                errorMsg = "Unable to update parking: " + e.getMessage();
            }
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
        });
    }

    private String currentGuardId() {
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
    }
}
