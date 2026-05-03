package com.example.se_proj;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;

import com.example.se_proj.databinding.ActivityGuardDashboardBinding;
import com.example.se_proj.models.AuditLog;
import com.example.se_proj.models.RegisteredVehicle;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.ParkingOccupancyUtils;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.RequestValidationUtils;
import com.example.se_proj.rules.VisitWindowEvaluator;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

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
 * <p>Supports visitor check-in (with optional car entry selection) and separate car exit
 * and student exit flows. Parking occupancy is updated automatically on each entry/exit.</p>
 */
public class GuardDashboardActivity extends AppCompatActivity {

    public static final String EXTRA_GATE_MODE = "gate_mode";
    public static final String MODE_IN_GATE = "in_gate";
    public static final String MODE_OUT_GATE = "out_gate";

    private static final String TAG = "GuardDashboard";

    private ActivityGuardDashboardBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private VisitorRequest currentRequest = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGuardDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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

        binding.toolbar.setNavigationOnClickListener(v ->
                binding.drawerLayout.openDrawer(GravityCompat.START));

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

        binding.btnSearchCnic.setOnClickListener(v -> {
            String cnic = RequestValidationUtils.normalizeCnic(
                    binding.etSearchCnic.getText().toString());
            if (!cnic.isEmpty()) {
                searchVisitorByCnic(cnic);
            } else {
                Toast.makeText(this, "Please enter CNIC", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnSearchRoll.setOnClickListener(v -> {
            String roll = binding.etSearchRoll.getText().toString().trim();
            if (!roll.isEmpty()) {
                searchCurrentVisitorsByHost(roll);
            } else {
                Toast.makeText(this, "Please enter Host Roll Number", Toast.LENGTH_SHORT).show();
            }
        });

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

        if (decision.shouldLogDeniedAccess()) {
            logAudit(request, "Denied", decision.getDeniedReason());
        }
    }

    private void setChipStatus(String text, int bgColorRes, int textColorRes) {
        binding.chipStatus.setText(text);
        binding.chipStatus.setChipBackgroundColor(
                ColorStateList.valueOf(ContextCompat.getColor(this, bgColorRes)));
        binding.chipStatus.setTextColor(ContextCompat.getColor(this, textColorRes));
    }

    // -------------------------------------------------------------------------
    // Check-in (visitor + optional car)
    // -------------------------------------------------------------------------

    private void checkIn(VisitorRequest request) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        Map<String, Object> updates = new HashMap<>();
        updates.put("entryTime", currentTime);
        updates.put("onCampus", true);
        db.collection("visitor_requests").document(request.getRequestId())
                .update(updates)
                .addOnSuccessListener(unused -> {
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
        updates.put("onCampus", true);
        db.collection("visitor_requests").document(request.getRequestId())
                .update(updates)
                .addOnSuccessListener(unused -> {
                    logAudit(request, "Entry", "Supervisor Override");
                    promptCarSelection(request.getHostId());
                    Toast.makeText(this, "Override successful", Toast.LENGTH_SHORT).show();
                });
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
            long currentOccupancy = snapshot.exists() && snapshot.getLong("currentOccupancy") != null
                    ? snapshot.getLong("currentOccupancy") : 0L;
            long maxCapacity = snapshot.exists() && snapshot.getLong("maxCapacity") != null
                    ? snapshot.getLong("maxCapacity") : 200L;

            long updatedOccupancy = ParkingOccupancyUtils.clampOccupancy(
                    currentOccupancy, delta, maxCapacity);

            if (!snapshot.exists()) {
                Map<String, Object> data = new HashMap<>();
                data.put("currentOccupancy", updatedOccupancy);
                data.put("maxCapacity", maxCapacity);
                transaction.set(documentRef, data);
            } else {
                transaction.update(documentRef, "currentOccupancy", updatedOccupancy);
            }
            return null;
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
}
