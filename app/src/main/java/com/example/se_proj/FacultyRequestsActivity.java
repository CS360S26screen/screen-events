package com.example.se_proj;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.se_proj.adapters.FacultyRequestAdapter;
import com.example.se_proj.databinding.ActivityFacultyRequestsBinding;
import com.example.se_proj.models.CarRegistrationRequest;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.UserProfileUtils;
import com.example.se_proj.rules.VehicleRegistrationUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Faculty "My Requests" management screen — view, edit, and cancel guest visit requests.
 *
 * <p>Two additional FABs provide quick navigation to:
 * <ul>
 *   <li>"Register Car" — opens a dialog to submit a {@link CarRegistrationRequest} for admin
 *       approval (same approval flow as students).</li>
 *   <li>"Wing Access" — opens {@link WingAccessRequestActivity} where faculty can request
 *       wing access on behalf of a student.</li>
 * </ul>
 * </p>
 */
public class FacultyRequestsActivity extends AppCompatActivity {

    private ActivityFacultyRequestsBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private FacultyRequestAdapter adapter;
    private String facultyId = "";
    private String facultyName = "";
    private String facultyUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFacultyRequestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.toolbar.inflateMenu(R.menu.top_app_bar);
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

        setupRecyclerView();
        loadUserProfile();

        binding.fabAddRequest.setOnClickListener(v ->
                startActivity(new Intent(this, RequestSubmissionActivity.class)));

        binding.fabMyCars.setOnClickListener(v -> showCarRegistrationDialog());

        binding.fabWingAccess.setOnClickListener(v ->
                startActivity(new Intent(this, WingAccessRequestActivity.class)));
    }

    private void setupRecyclerView() {
        adapter = new FacultyRequestAdapter(
                new ArrayList<>(),
                request -> {
                    if (RequestStatus.canEdit(request.getStatus())) {
                        showEditOptions(request);
                    } else {
                        Toast.makeText(this, "Cannot edit processed requests",
                                Toast.LENGTH_SHORT).show();
                    }
                },
                request -> {
                    if (RequestStatus.canCancel(request.getStatus(), request.isOnCampus())) {
                        showCancelConfirmation(request);
                    } else {
                        Toast.makeText(this,
                                "Only active off-campus requests can be cancelled",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
        binding.rvRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvRequests.setAdapter(adapter);
    }

    // -------------------------------------------------------------------------
    // User profile
    // -------------------------------------------------------------------------

    private void loadUserProfile() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null || uid.isEmpty()) {
            Toast.makeText(this, "Please log in again to view your requests",
                    Toast.LENGTH_LONG).show();
            return;
        }
        facultyUid = uid;

        db.collection("Users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "User profile not found", Toast.LENGTH_LONG).show();
                        return;
                    }

                    facultyId = UserProfileUtils.resolveHostId(
                            doc.getString("rollNumber"),
                            doc.getString("facultyId"),
                            doc.getId()
                    );
                    String n = doc.getString("name");
                    facultyName = n != null ? n : "";
                    fetchMyRequests();
                });
    }

    private void fetchMyRequests() {
        if (facultyId.isEmpty()) return;
        db.collection("visitor_requests")
                .whereEqualTo("hostId", facultyId)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    List<VisitorRequest> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            VisitorRequest request = doc.toObject(VisitorRequest.class);
                            if (request != null) {
                                if (request.getRequestId().isEmpty()) {
                                    request = request.withRequestId(doc.getId());
                                }
                                list.add(request);
                            }
                        }
                    }
                    adapter.updateData(list);
                });
    }

    // -------------------------------------------------------------------------
    // Car registration dialog
    // -------------------------------------------------------------------------

    private void showCarRegistrationDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_car_registration, null);
        TextInputEditText etPlate = dialogView.findViewById(R.id.etDialogPlate);
        TextInputEditText etModel = dialogView.findViewById(R.id.etDialogModel);

        new AlertDialog.Builder(this)
                .setTitle("Register My Car")
                .setView(dialogView)
                .setPositiveButton("Submit Request", (dialog, which) -> {
                    String plate = etPlate.getText() != null
                            ? etPlate.getText().toString().trim().toUpperCase() : "";
                    String model = etModel.getText() != null
                            ? etModel.getText().toString().trim() : "";
                    submitFacultyCarRequest(plate, model);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitFacultyCarRequest(String plate, String model) {
        VehicleRegistrationUtils.ValidationResult validation =
                VehicleRegistrationUtils.validateCarRequest(plate, model);
        if (!validation.isValid()) {
            Toast.makeText(this, validation.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (facultyUid.isEmpty()) {
            Toast.makeText(this, "Profile not loaded yet, please try again",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("car_registration_requests")
                .whereEqualTo("studentUid", facultyUid)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(pendingSnap -> {
                    db.collection("registered_vehicles")
                            .whereEqualTo("studentUid", facultyUid)
                            .get()
                            .addOnSuccessListener(approvedSnap -> {
                                int total = pendingSnap.size() + approvedSnap.size();
                                if (VehicleRegistrationUtils.isAtCarLimit(total)) {
                                    Toast.makeText(this,
                                            "Maximum 2 vehicles allowed",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                checkFacultyPlateAndSubmit(plate, model);
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void checkFacultyPlateAndSubmit(String plate, String model) {
        db.collection("car_registration_requests")
                .whereEqualTo("licensePlate", plate)
                .get()
                .addOnSuccessListener(existingSnap -> {
                    if (!existingSnap.isEmpty()) {
                        Toast.makeText(this,
                                "This plate already has a pending or approved request",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.collection("registered_vehicles")
                            .whereEqualTo("licensePlate", plate)
                            .get()
                            .addOnSuccessListener(regSnap -> {
                                if (!regSnap.isEmpty()) {
                                    Toast.makeText(this,
                                            "License plate already registered in the system",
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                CarRegistrationRequest request = new CarRegistrationRequest(
                                        "", facultyUid, facultyName, facultyId, "faculty",
                                        plate, model, "pending", Timestamp.now()
                                );
                                db.collection("car_registration_requests").add(request)
                                        .addOnSuccessListener(unused ->
                                                Toast.makeText(this,
                                                        "Car request submitted — pending admin approval",
                                                        Toast.LENGTH_SHORT).show())
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this, "Failed: " + e.getMessage(),
                                                        Toast.LENGTH_SHORT).show());
                            });
                });
    }

    // -------------------------------------------------------------------------
    // Guest request editing
    // -------------------------------------------------------------------------

    private void showEditOptions(VisitorRequest request) {
        String[] options = {"Change Visit Date", "Change Start Time", "Change End Time"};
        new AlertDialog.Builder(this)
                .setTitle("Edit Request")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showDatePicker(request);
                    } else if (which == 1) {
                        showTimePicker(request, "startTime");
                    } else if (which == 2) {
                        showTimePicker(request, "endTime");
                    }
                })
                .show();
    }

    private void showDatePicker(VisitorRequest request) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String newDate = String.format(Locale.getDefault(), "%02d/%02d/%d",
                    dayOfMonth, month + 1, year);
            updateRequestField(request.getRequestId(), "visitDate", newDate);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(VisitorRequest request, String field) {
        Calendar calendar = Calendar.getInstance();
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String newTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
            updateRequestField(request.getRequestId(), field, newTime);
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
    }

    private void updateRequestField(String requestId, String field, String value) {
        if (requestId.isEmpty()) {
            Toast.makeText(this, "Cannot update: request ID missing", Toast.LENGTH_SHORT).show();
            return;
        }
        db.collection("visitor_requests").document(requestId)
                .update(field, value)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, field + " updated", Toast.LENGTH_SHORT).show());
    }

    private void showCancelConfirmation(VisitorRequest request) {
        if (request.getRequestId().isEmpty()) {
            Toast.makeText(this, "Cannot cancel: request ID missing", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Cancel Request")
                .setMessage("Are you sure you want to cancel the request for "
                        + request.getGuestName() + "?")
                .setPositiveButton("Yes, Cancel", (dialog, which) ->
                        db.collection("visitor_requests").document(request.getRequestId())
                                .update("status", RequestStatus.CANCELLED)
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(this, "Request cancelled",
                                                Toast.LENGTH_SHORT).show()))
                .setNegativeButton("No", null)
                .show();
    }
}
