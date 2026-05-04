package com.example.se_proj;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.se_proj.adapters.CarRegistrationRequestAdapter;
import com.example.se_proj.adapters.VisitorRequestAdapter;
import com.example.se_proj.databinding.ActivityAdminDashboardBinding;
import com.example.se_proj.models.AuditLog;
import com.example.se_proj.models.BlacklistEntry;
import com.example.se_proj.models.CarRegistrationRequest;
import com.example.se_proj.models.RegisteredVehicle;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.BlacklistService;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.VehicleRegistrationUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin control panel for approving/rejecting visitor requests, managing car registration
 * requests, and monitoring live summary stats.
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private static final String TAG = "AdminDashboard";

    private ActivityAdminDashboardBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private VisitorRequestAdapter adapter;
    private BlacklistService blacklistService;
    private CarRegistrationRequestAdapter carAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        blacklistService = new BlacklistService(db);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        LogoutUtils.attachLogoutConfirmation(this, binding.adminLogoutButton);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                LogoutUtils.showLogoutConfirmation(this);
                return true;
            }
            return false;
        });

        setupVisitorRecyclerView();
        setupCarRequestRecyclerView();
        setupSummaryStats();
        ensureParkingDocument();
        fetchPendingRequests();
        setupBlacklistButton();
        fetchPendingCarRequests();

        binding.btnViewAudit.setOnClickListener(v ->
                startActivity(new Intent(this, AdminAuditActivity.class)));

        blacklistService.removeExpiredEntries();
        binding.btnWingAccess.setOnClickListener(v ->
                startActivity(new Intent(this, AdminWingAccessActivity.class)));
    }

    private void setupVisitorRecyclerView() {
        adapter = new VisitorRequestAdapter(
                new ArrayList<>(),
                this::handleApprove,
                this::handleReject
        );
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRequests.setAdapter(adapter);
    }

    private void setupCarRequestRecyclerView() {
        carAdapter = new CarRegistrationRequestAdapter(
                new ArrayList<>(),
                this::approveCarRequest,
                this::rejectCarRequest
        );
        binding.rvCarRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCarRequests.setAdapter(carAdapter);
    }

    private void setupSummaryStats() {
        db.collection("visitor_requests")
                .whereEqualTo("onCampus", true)
                .addSnapshotListener((snapshots, e) -> {
                    int count = snapshots != null ? snapshots.size() : 0;
                    binding.tvActiveGuests.setText(String.valueOf(count));
                });

        db.collection("system_metadata").document("parking_status")
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        long occupancy = snapshot.getLong("currentOccupancy") != null
                                ? snapshot.getLong("currentOccupancy") : 0;
                        long capacity  = snapshot.getLong("maxCapacity") != null
                                ? snapshot.getLong("maxCapacity") : 200;
                        binding.tvParkingStatus.setText(occupancy + "/" + capacity);
                    }
                });
    }

    private void fetchPendingRequests() {
        db.collection("visitor_requests")
                .whereIn("status", Arrays.asList(RequestStatus.PENDING, RequestStatus.PENDING_ADHOC))
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }
                    List<VisitorRequest> pendingList = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            VisitorRequest request = doc.toObject(VisitorRequest.class);
                            if (request != null) {
                                pendingList.add(request.withRequestId(doc.getId()));
                            }
                        }
                    }
                    adapter.updateData(pendingList);
                });
    }

    private void handleApprove(VisitorRequest request) {
        updateRequestStatus(request, RequestStatus.APPROVED);
    }

    private void handleReject(VisitorRequest request) {
        updateRequestStatus(request, RequestStatus.REJECTED);
    }

    private void updateRequestStatus(VisitorRequest request, String newStatus) {
        String requestId = request.getRequestId();
        if (requestId.isEmpty()) {
            Toast.makeText(this, "Error: Request ID is missing", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("visitor_requests").document(requestId)
                .update("status", newStatus)
                .addOnSuccessListener(unused -> {
                    logAdminAction(request, newStatus.toUpperCase());
                    Toast.makeText(this, "Request " + newStatus, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Failed to update ID: " + requestId, error);
                    Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void logAdminAction(VisitorRequest request, String action) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        AuditLog log = new AuditLog(
                "", request.getGuestName(), request.getGuestCNIC(),
                request.getHostId(), "ADMIN_" + action,
                "Action taken by Security Admin", uid, Timestamp.now()
        );
        db.collection("access_logs").add(log);
    }

    private void setupBlacklistButton() {
        View btnManage = binding.getRoot().findViewById(R.id.btnManageBlacklist);
        if (btnManage != null) {
            btnManage.setOnClickListener(v -> showBlacklistMenuDialog());
        }
    }

    private void showBlacklistMenuDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Blacklist Management")
                .setItems(new String[]{"Add Entity to Blacklist", "View / Remove Active Entries"},
                        (dialog, which) -> {
                            if (which == 0) showAddBlacklistDialog();
                            else startActivity(new Intent(this, AdminBlacklistActivity.class));
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddBlacklistDialog() {
        int dp8 = (int) (8 * getResources().getDisplayMetrics().density);

        EditText etEntityId = new EditText(this); etEntityId.setHint("CNIC or Vehicle Plate");
        EditText etEntityName = new EditText(this); etEntityName.setHint("Full Name / Description");
        EditText etReason = new EditText(this); etReason.setHint("Reason for blacklisting");

        RadioGroup rgType = new RadioGroup(this);
        rgType.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbPerson = new RadioButton(this); rbPerson.setText("Person");
        RadioButton rbVehicle = new RadioButton(this); rbVehicle.setText("Vehicle");
        rgType.addView(rbPerson); rgType.addView(rbVehicle); rbPerson.setChecked(true);

        RadioGroup rgBan = new RadioGroup(this);
        rgBan.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbPerm = new RadioButton(this); rbPerm.setText("Permanent");
        RadioButton rbTemp = new RadioButton(this); rbTemp.setText("Temporary (7 days)");
        rgBan.addView(rbPerm); rgBan.addView(rbTemp); rbPerm.setChecked(true);

        TextView tvTypeLabel = new TextView(this); tvTypeLabel.setText("Type:");
        TextView tvBanLabel = new TextView(this); tvBanLabel.setText("Ban:");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp8 * 2, dp8, dp8 * 2, dp8);
        layout.addView(etEntityId); layout.addView(etEntityName); layout.addView(etReason);
        layout.addView(tvTypeLabel); layout.addView(rgType);
        layout.addView(tvBanLabel); layout.addView(rgBan);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(layout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add to Blacklist")
                .setView(scrollView)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        Button addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        addButton.setOnClickListener(v -> {
            String entityId = etEntityId.getText().toString().trim();
            String entityName = etEntityName.getText().toString().trim();
            String reason = etReason.getText().toString().trim();

            if (entityId.isEmpty() || entityName.isEmpty() || reason.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            String entityType = rbVehicle.isChecked() ? BlacklistEntry.TYPE_VEHICLE : BlacklistEntry.TYPE_PERSON;
            String banType = rbTemp.isChecked() ? BlacklistEntry.BAN_TEMPORARY : BlacklistEntry.BAN_PERMANENT;
            Timestamp expiry = rbTemp.isChecked() ? sevenDaysFromNow() : null;
            String adminId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

            blacklistService.addToBlacklist(entityId, entityType, entityName, reason, banType, expiry, adminId,
                    new BlacklistService.BlacklistWriteCallback() {
                        @Override public void onSuccess() {
                            Toast.makeText(AdminDashboardActivity.this, entityName + " added.", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        }
                        @Override public void onError(Exception e) {
                            Toast.makeText(AdminDashboardActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private Timestamp sevenDaysFromNow() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 7);
        return new Timestamp(cal.getTime());
    }

    private void fetchPendingCarRequests() {
        db.collection("car_registration_requests")
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Car requests listen failed.", e);
                        return;
                    }
                    List<CarRegistrationRequest> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            CarRegistrationRequest r = doc.toObject(CarRegistrationRequest.class);
                            if (r != null) {
                                if (r.getRequestId().isEmpty()) r.setRequestId(doc.getId());
                                list.add(r);
                            }
                        }
                    }
                    carAdapter.updateData(list);
                    binding.tvCarRequestsEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.rvCarRequests.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    private void approveCarRequest(CarRegistrationRequest request) {
        if (request.getRequestId().isEmpty()) {
            Toast.makeText(this, "Cannot approve: request ID missing", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("cars_registered")
                .whereEqualTo("licensePlate", request.getLicensePlate())
                .get()
                .addOnSuccessListener(existingCars -> {
                    if (!existingCars.isEmpty()) {
                        Toast.makeText(this, "License plate already registered", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if ("student".equals(request.getOwnerRole())) {
                        enforceStudentCarApprovalLimit(request);
                    } else {
                        createRegisteredVehicle(request);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Approval check failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void enforceStudentCarApprovalLimit(CarRegistrationRequest request) {
        db.collection("cars_registered")
                .whereEqualTo("studentUid", request.getStudentUid())
                .get()
                .addOnSuccessListener(approvedCars -> {
                    if (VehicleRegistrationUtils.isAtCarLimit(approvedCars.size())) {
                        Toast.makeText(this, "Student already has 2 registered vehicles", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    createRegisteredVehicle(request);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Approval check failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void createRegisteredVehicle(CarRegistrationRequest request) {
        RegisteredVehicle vehicle = new RegisteredVehicle(
                "", request.getOwnerRollNo(), request.getOwnerName(),
                request.getStudentUid(), request.getOwnerRole(), request.getLicensePlate(),
                request.getCarModel(), false, null, null
        );
        DocumentReference vehicleRef = db.collection("cars_registered").document();
        vehicle.setVehicleId(vehicleRef.getId());
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        AuditLog log = new AuditLog("", vehicle.getStudentName(), "", vehicle.getStudentRollNo(), "CAR_REGISTERED", vehicle.getLicensePlate() + " — " + vehicle.getCarModel() + " — " + vehicle.getOwnerRole(), uid, Timestamp.now());
        DocumentReference logRef = db.collection("access_logs").document();
        log.setId(logRef.getId());
        WriteBatch batch = db.batch();
        batch.set(vehicleRef, vehicle);
        batch.update(db.collection("car_registration_requests").document(request.getRequestId()), "status", "approved");
        batch.set(logRef, log);
        batch.commit()
                .addOnSuccessListener(unused -> Toast.makeText(this, "Car approved and registered", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to approve car request", e);
                    Toast.makeText(this, "Failed to approve: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void rejectCarRequest(CarRegistrationRequest request) {
        db.collection("car_registration_requests")
                .document(request.getRequestId())
                .update("status", "rejected")
                .addOnSuccessListener(unused -> Toast.makeText(this, "Car request rejected", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void ensureParkingDocument() {
        db.collection("system_metadata").document("parking_status").get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Map<String, Object> data = new HashMap<>();
                data.put("currentOccupancy", 0L); data.put("maxCapacity", 200L);
                db.collection("system_metadata").document("parking_status").set(data);
            }
        });
    }
}
