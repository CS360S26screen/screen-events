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
import com.example.se_proj.adapters.PendingCarRequestAdapter;
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
import com.google.firebase.firestore.ListenerRegistration;

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

    public static final String EXTRA_FILTER_MODE = "faculty_request_filter_mode";
    public static final String MODE_APPROVED = "approved";
    public static final String MODE_PENDING = "pending";

    private ActivityFacultyRequestsBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private FacultyRequestAdapter adapter;
    private PendingCarRequestAdapter carAdapter;
    private String facultyId = "";
    private String facultyName = "";
    private String facultyUid = "";
    private ListenerRegistration guestRequestsListener;
    private ListenerRegistration carRequestsListener;
    private String filterMode = MODE_PENDING;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFacultyRequestsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        filterMode = resolveFilterMode(getIntent());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        LogoutUtils.attachLogoutConfirmation(this, binding.facultyLogoutButton);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                LogoutUtils.showLogoutConfirmation(this);
                return true;
            }
            return false;
        });

        setupRecyclerView();
        setupScreenLabels();
        setupBottomNavigation();
        loadUserProfile();
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(MODE_APPROVED.equals(filterMode)
                ? R.id.nav_faculty_approved
                : R.id.nav_faculty_pending);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_faculty_guest) {
                Intent intent = new Intent(this, RequestSubmissionActivity.class);
                intent.putExtra(RequestSubmissionActivity.EXTRA_MODE, RequestSubmissionActivity.MODE_GUEST);
                startActivity(intent);
                finish();
                return true;
            } else if (id == R.id.nav_faculty_car) {
                Intent intent = new Intent(this, RequestSubmissionActivity.class);
                intent.putExtra(RequestSubmissionActivity.EXTRA_MODE, RequestSubmissionActivity.MODE_CAR);
                startActivity(intent);
                finish();
                return true;
            } else if (id == R.id.nav_faculty_approved) {
                if (MODE_APPROVED.equals(filterMode)) {
                    return true;
                }
                openFilteredRequests(MODE_APPROVED);
                return true;
            } else if (id == R.id.nav_faculty_pending) {
                if (MODE_PENDING.equals(filterMode)) {
                    return true;
                }
                openFilteredRequests(MODE_PENDING);
                return true;
            }
            return false;
        });
    }

    private String resolveFilterMode(Intent intent) {
        String mode = intent != null ? intent.getStringExtra(EXTRA_FILTER_MODE) : null;
        return MODE_APPROVED.equals(mode) ? MODE_APPROVED : MODE_PENDING;
    }

    private void openFilteredRequests(String mode) {
        Intent intent = new Intent(this, FacultyRequestsActivity.class);
        intent.putExtra(EXTRA_FILTER_MODE, mode);
        startActivity(intent);
        finish();
    }

    private void setupScreenLabels() {
        boolean approved = MODE_APPROVED.equals(filterMode);
        binding.toolbar.setTitle("Campus Hub");
        binding.tvGuestRequestsTitle.setText(approved
                ? "Approved Guest Requests"
                : "Pending Guest Requests");
        binding.tvCarRequestsTitle.setText(approved
                ? "Approved Car Requests"
                : "Pending Car Requests");
        binding.tvEmptyGuestRequests.setText(approved
                ? "No approved guest requests yet."
                : "No pending guest requests.");
        binding.tvEmptyCarRequests.setText(approved
                ? "No approved car requests yet."
                : "No pending car requests.");
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

        carAdapter = new PendingCarRequestAdapter(
                new ArrayList<>(),
                this::showCancelCarRequestConfirmation
        );
        binding.rvCarRequests.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCarRequests.setAdapter(carAdapter);
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
                            doc.getId(),
                            doc.getString("email")
                    );
                    String n = doc.getString("name");
                    facultyName = n != null ? n : "";
                    fetchMyGuestRequests();
                    fetchMyCarRequests();
                });
    }

    private void fetchMyGuestRequests() {
        if (facultyId.isEmpty()) return;
        if (guestRequestsListener != null) {
            guestRequestsListener.remove();
        }
        guestRequestsListener = db.collection("visitor_requests")
                .whereEqualTo("hostId", facultyId)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    List<VisitorRequest> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            VisitorRequest request = doc.toObject(VisitorRequest.class);
                            if (request != null) {
                                if (!shouldShowGuestRequest(request)) {
                                    continue;
                                }
                                if (request.getRequestId().isEmpty()) {
                                    request = request.withRequestId(doc.getId());
                                }
                                list.add(request);
                            }
                        }
                    }
                    adapter.updateData(list);
                    binding.tvEmptyGuestRequests.setVisibility(
                            list.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void fetchMyCarRequests() {
        if (facultyUid.isEmpty()) return;
        if (carRequestsListener != null) {
            carRequestsListener.remove();
        }
        carRequestsListener = db.collection("car_registration_requests")
                .whereEqualTo("studentUid", facultyUid)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    List<CarRegistrationRequest> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            CarRegistrationRequest request =
                                    doc.toObject(CarRegistrationRequest.class);
                            if (request != null) {
                                if (!shouldShowCarRequest(request)) {
                                    continue;
                                }
                                if (request.getRequestId().isEmpty()) {
                                    request.setRequestId(doc.getId());
                                }
                                list.add(request);
                            }
                        }
                    }
                    carAdapter.updateData(list);
                    binding.tvEmptyCarRequests.setVisibility(
                            list.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private boolean shouldShowGuestRequest(VisitorRequest request) {
        String status = RequestStatus.normalize(request.getStatus());
        if (MODE_APPROVED.equals(filterMode)) {
            return RequestStatus.APPROVED.equals(status);
        }
        return RequestStatus.PENDING.equals(status)
                || RequestStatus.PENDING_ADHOC.equals(status);
    }

    private boolean shouldShowCarRequest(CarRegistrationRequest request) {
        String status = request.getStatus() == null
                ? ""
                : request.getStatus().trim().toLowerCase(Locale.ROOT);
        if (MODE_APPROVED.equals(filterMode)) {
            return MODE_APPROVED.equals(status);
        }
        return MODE_PENDING.equals(status);
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

        checkFacultyPlateAndSubmit(plate, model);
    }

    private void checkFacultyPlateAndSubmit(String plate, String model) {
        db.collection("car_registration_requests")
                .whereEqualTo("licensePlate", plate)
                .get()
                .addOnSuccessListener(existingSnap -> {
                    boolean hasActiveRequest = false;
                    for (DocumentSnapshot doc : existingSnap.getDocuments()) {
                        CarRegistrationRequest existing =
                                doc.toObject(CarRegistrationRequest.class);
                        if (existing != null
                                && ("pending".equals(existing.getStatus())
                                || "approved".equals(existing.getStatus()))) {
                            hasActiveRequest = true;
                            break;
                        }
                    }
                    if (hasActiveRequest) {
                        Toast.makeText(this,
                                "This plate already has a pending or approved request",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.collection("cars_registered")
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

    private void showCancelCarRequestConfirmation(CarRegistrationRequest request) {
        if (request.getRequestId().isEmpty()) {
            Toast.makeText(this, "Cannot cancel: request ID missing", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!"pending".equals(request.getStatus())) {
            Toast.makeText(this, "Only pending car requests can be cancelled",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Cancel Car Request")
                .setMessage("Cancel the registration request for "
                        + request.getLicensePlate() + "?")
                .setPositiveButton("Yes, Cancel", (dialog, which) ->
                        db.collection("car_registration_requests")
                                .document(request.getRequestId())
                                .delete()
                                .addOnSuccessListener(unused ->
                                        Toast.makeText(this, "Car request cancelled",
                                                Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Cancel failed: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show()))
                .setNegativeButton("No", null)
                .show();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (guestRequestsListener != null) guestRequestsListener.remove();
        if (carRequestsListener != null) carRequestsListener.remove();
    }
}
