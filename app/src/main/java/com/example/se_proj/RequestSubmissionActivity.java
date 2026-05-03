package com.example.se_proj;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.se_proj.databinding.ActivityRequestSubmissionBinding;
import com.example.se_proj.models.CarRegistrationRequest;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.AuditLogUtils;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.RequestValidationUtils;
import com.example.se_proj.rules.UserProfileUtils;
import com.example.se_proj.rules.VehicleRegistrationUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Faculty request-creation screen for scheduled visitors plus real-time handling of walk-in
 * approvals and near-end-time host reminders.
 *
 * <p>Design note: orchestrates UI events and Firestore listeners while delegating validation,
 * status semantics, and reminder/overstay timing logic to rule utilities.</p>
 *
 * <p>Outstanding issues: reminder deduplication is in-memory only ({@code shownReminderRequestIds})
 * and resets on process death, so reminders can reappear after app restart.</p>
 */
public class RequestSubmissionActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "faculty_portal_mode";
    public static final String MODE_GUEST = "guest";
    public static final String MODE_CAR = "car";

    /** Callback interface for time-picker dialogs. */
    private interface OnTimeSelectedListener {
        void onTimeSelected(String time);
    }

    private ActivityRequestSubmissionBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private ListenerRegistration adHocListener;
    private ListenerRegistration overstayListener;
    private final Set<String> shownReminderRequestIds = new HashSet<>();

    private String currentUserId = "";
    private String currentUserName = "";

    private String selectedDate = "";
    private String selectedStartTime = "";
    private String selectedEndTime = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRequestSubmissionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
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

        binding.btnSubmit.setEnabled(false);
        binding.btnSubmitCarRequest.setEnabled(false);
        loadUserProfile();
        setupBottomNavigation();

        binding.etVisitDate.setOnClickListener(v -> showDatePicker());
        binding.etStartTime.setOnClickListener(v ->
                showTimePicker(time -> {
                    selectedStartTime = time;
                    binding.etStartTime.setText(time);
                }));
        binding.etEndTime.setOnClickListener(v ->
                showTimePicker(time -> {
                    selectedEndTime = time;
                    binding.etEndTime.setText(time);
                }));

        binding.btnSubmit.setOnClickListener(v -> submitRequest());
        binding.btnSubmitCarRequest.setOnClickListener(v -> submitCarRequest());

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_CAR.equals(mode)) {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_faculty_car);
        } else {
            binding.bottomNavigation.setSelectedItemId(R.id.nav_faculty_guest);
        }
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_faculty_guest) {
                showGuestRegistration();
                return true;
            } else if (id == R.id.nav_faculty_car) {
                showCarRegistration();
                return true;
            } else if (id == R.id.nav_faculty_approved) {
                Intent intent = new Intent(this, FacultyRequestsActivity.class);
                intent.putExtra(FacultyRequestsActivity.EXTRA_FILTER_MODE,
                        FacultyRequestsActivity.MODE_APPROVED);
                startActivity(intent);
                finish();
                return true;
            } else if (id == R.id.nav_faculty_pending) {
                Intent intent = new Intent(this, FacultyRequestsActivity.class);
                intent.putExtra(FacultyRequestsActivity.EXTRA_FILTER_MODE,
                        FacultyRequestsActivity.MODE_PENDING);
                startActivity(intent);
                finish();
                return true;
            }
            return false;
        });
    }

    private void showGuestRegistration() {
        binding.tvFacultyHeader.setText("Guest Registration");
        binding.tilGuestName.setVisibility(View.VISIBLE);
        binding.tilCnic.setVisibility(View.VISIBLE);
        binding.tilPurpose.setVisibility(View.VISIBLE);
        binding.tilVisitDate.setVisibility(View.VISIBLE);
        binding.llTimeSlots.setVisibility(View.VISIBLE);
        binding.btnSubmit.setVisibility(View.VISIBLE);

        binding.tilFacultyCarPlate.setVisibility(View.GONE);
        binding.tilFacultyCarModel.setVisibility(View.GONE);
        binding.btnSubmitCarRequest.setVisibility(View.GONE);
    }

    private void showCarRegistration() {
        binding.tvFacultyHeader.setText("Car Registration");
        binding.tilGuestName.setVisibility(View.GONE);
        binding.tilCnic.setVisibility(View.GONE);
        binding.tilPurpose.setVisibility(View.GONE);
        binding.tilVisitDate.setVisibility(View.GONE);
        binding.llTimeSlots.setVisibility(View.GONE);
        binding.btnSubmit.setVisibility(View.GONE);

        binding.tilFacultyCarPlate.setVisibility(View.VISIBLE);
        binding.tilFacultyCarModel.setVisibility(View.VISIBLE);
        binding.btnSubmitCarRequest.setVisibility(View.VISIBLE);
    }

    private void loadUserProfile() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null || uid.isEmpty()) {
            Toast.makeText(this, "Please log in again to submit a request", Toast.LENGTH_LONG).show();
            return;
        }

        db.collection("Users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        setupProfileData(doc.getId(), doc.getData());
                    } else {
                        String email = auth.getCurrentUser() != null
                                ? auth.getCurrentUser().getEmail() : null;
                        if (email != null) {
                            db.collection("Users").whereEqualTo("email", email).get()
                                    .addOnSuccessListener(query -> {
                                        if (!query.isEmpty()) {
                                            com.google.firebase.firestore.DocumentSnapshot d =
                                                    query.getDocuments().get(0);
                                            setupProfileData(d.getId(), d.getData());
                                        } else {
                                            Toast.makeText(this, "User profile not found",
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }
                    }
                })
                .addOnFailureListener(error -> {
                    Log.e("RequestSubmission", "Failed to load user profile", error);
                    Toast.makeText(this, "Unable to load your profile", Toast.LENGTH_LONG).show();
                });
    }

    private void setupProfileData(String docId, java.util.Map<String, Object> data) {
        String rollNumber = (data != null && data.get("rollNumber") != null)
                ? data.get("rollNumber").toString() : null;
        String facultyId = (data != null && data.get("facultyId") != null)
                ? data.get("facultyId").toString() : null;

        currentUserId = UserProfileUtils.resolveHostId(rollNumber, facultyId, docId);
        currentUserName = (data != null && data.get("name") != null)
                ? data.get("name").toString() : "";
        boolean profileLoaded = !currentUserId.isEmpty();
        binding.btnSubmit.setEnabled(profileLoaded);
        binding.btnSubmitCarRequest.setEnabled(profileLoaded);
        if (profileLoaded) {
            startAdHocListener();
            startOverstayListener();
        }
    }

    private void startAdHocListener() {
        if (currentUserId.isEmpty()) return;
        adHocListener = db.collection("visitor_requests")
                .whereEqualTo("hostId", currentUserId)
                .whereEqualTo("status", RequestStatus.PENDING_ADHOC)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    List<DocumentChange> changes = snapshots != null
                            ? snapshots.getDocumentChanges()
                            : java.util.Collections.emptyList();
                    for (DocumentChange doc : changes) {
                        if (doc.getType() == DocumentChange.Type.ADDED) {
                            VisitorRequest request = doc.getDocument().toObject(VisitorRequest.class);
                            showAdHocDialog(doc.getDocument().getId(), request);
                        }
                    }
                });
    }

    private void startOverstayListener() {
        if (currentUserId.isEmpty()) return;

        overstayListener = db.collection("visitor_requests")
                .whereEqualTo("hostId", currentUserId)
                .whereEqualTo("onCampus", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    LocalDateTime now = LocalDateTime.now();
                    List<com.google.firebase.firestore.DocumentSnapshot> docs = snapshots != null
                            ? snapshots.getDocuments()
                            : java.util.Collections.emptyList();

                    for (com.google.firebase.firestore.DocumentSnapshot doc : docs) {
                        VisitorRequest request = doc.toObject(VisitorRequest.class);
                        if (request == null) continue;
                        String requestKey = !request.getRequestId().isEmpty()
                                ? request.getRequestId() : doc.getId();

                        if (AuditLogUtils.shouldSendExitReminder(request, now, 15)) {
                            if (shownReminderRequestIds.add(requestKey)) {
                                showOverstayAlert(request.getGuestName(), request.getEndTime());
                            }
                        } else {
                            shownReminderRequestIds.remove(requestKey);
                        }
                    }
                });
    }

    private void showOverstayAlert(String guestName, String endTime) {
        new AlertDialog.Builder(this)
                .setTitle("Upcoming Exit Reminder")
                .setMessage("Your guest " + guestName + " is scheduled to leave at " + endTime
                        + " (within 15 minutes).")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showAdHocDialog(String requestId, VisitorRequest request) {
        new AlertDialog.Builder(this)
                .setTitle("Walk-in Approval Request")
                .setMessage("Guest " + request.getGuestName() + " is at the gate for: "
                        + request.getPurpose() + ". Approve entry?")
                .setPositiveButton("Approve", (dialog, which) ->
                        updateRequestStatus(requestId, RequestStatus.APPROVED))
                .setNegativeButton("Deny", (dialog, which) ->
                        updateRequestStatus(requestId, RequestStatus.DENIED))
                .setCancelable(false)
                .show();
    }

    private void updateRequestStatus(String requestId, String newStatus) {
        db.collection("visitor_requests").document(requestId)
                .update("status", newStatus)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Request " + newStatus, Toast.LENGTH_SHORT).show());
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d",
                    dayOfMonth, month + 1, year);
            binding.etVisitDate.setText(selectedDate);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(OnTimeSelectedListener onTimeSelected) {
        Calendar calendar = Calendar.getInstance();
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
            onTimeSelected.onTimeSelected(time);
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
    }

    private void submitRequest() {
        String name = binding.etGuestName.getText().toString().trim();
        String cnic = RequestValidationUtils.normalizeCnic(binding.etCnic.getText().toString());
        String purpose = binding.etPurpose.getText().toString().trim();

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Your profile is still loading", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestValidationUtils.ValidationResult validation =
                RequestValidationUtils.validateScheduledRequest(name, cnic, purpose,
                        selectedDate, selectedStartTime, selectedEndTime);
        if (!validation.isValid()) {
            Toast.makeText(this, validation.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        VisitorRequest request = new VisitorRequest(
                "", name, cnic, purpose, selectedDate, selectedStartTime, selectedEndTime,
                currentUserId,
                auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "",
                "faculty", RequestStatus.PENDING, null, null, false
        );

        binding.btnSubmit.setEnabled(false);
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";

        db.collection("visitor_requests")
                .whereEqualTo("creatorId", uid)
                .whereEqualTo("guestCNIC", request.getGuestCNIC())
                .whereEqualTo("visitDate", request.getVisitDate())
                .get()
                .addOnSuccessListener(documents -> {
                    List<VisitorRequest> existing = documents.toObjects(VisitorRequest.class);
                    boolean hasDuplicate = false;
                    for (VisitorRequest r : existing) {
                        if (RequestValidationUtils.matchesDuplicateCandidate(
                                r, request.getHostId(), request.getGuestCNIC(), request.getVisitDate())) {
                            hasDuplicate = true;
                            break;
                        }
                    }

                    if (hasDuplicate) {
                        binding.btnSubmit.setEnabled(true);
                        Toast.makeText(this,
                                "A similar request already exists for this guest and date",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    saveVisitorRequest(request);
                })
                .addOnFailureListener(e -> {
                    binding.btnSubmit.setEnabled(true);
                    Log.e("FirestoreError", "Error checking duplicates", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveVisitorRequest(VisitorRequest request) {
        db.collection("visitor_requests")
                .add(request)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Request Submitted Successfully", Toast.LENGTH_SHORT).show();
                    clearForm();
                })
                .addOnFailureListener(e -> {
                    binding.btnSubmit.setEnabled(true);
                    Log.e("FirestoreError", "Error adding document", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void clearForm() {
        if (binding.etGuestName.getText() != null) binding.etGuestName.getText().clear();
        if (binding.etCnic.getText() != null) binding.etCnic.getText().clear();
        if (binding.etPurpose.getText() != null) binding.etPurpose.getText().clear();
        if (binding.etVisitDate.getText() != null) binding.etVisitDate.getText().clear();
        if (binding.etStartTime.getText() != null) binding.etStartTime.getText().clear();
        if (binding.etEndTime.getText() != null) binding.etEndTime.getText().clear();
        selectedDate = "";
        selectedStartTime = "";
        selectedEndTime = "";
        binding.btnSubmit.setEnabled(true);
    }

    private void submitCarRequest() {
        String plate = binding.etFacultyCarPlate.getText() != null
                ? binding.etFacultyCarPlate.getText().toString().trim().toUpperCase(Locale.ROOT)
                : "";
        String model = binding.etFacultyCarModel.getText() != null
                ? binding.etFacultyCarModel.getText().toString().trim()
                : "";

        VehicleRegistrationUtils.ValidationResult validation =
                VehicleRegistrationUtils.validateCarRequest(plate, model);
        if (!validation.isValid()) {
            Toast.makeText(this, validation.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Your profile is still loading", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnSubmitCarRequest.setEnabled(false);
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
                        binding.btnSubmitCarRequest.setEnabled(true);
                        return;
                    }
                    checkRegisteredPlateAndSubmit(plate, model);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.btnSubmitCarRequest.setEnabled(true);
                });
    }

    private void checkRegisteredPlateAndSubmit(String plate, String model) {
        db.collection("cars_registered")
                .whereEqualTo("licensePlate", plate)
                .get()
                .addOnSuccessListener(regSnap -> {
                    if (!regSnap.isEmpty()) {
                        Toast.makeText(this,
                                "License plate already registered in the system",
                                Toast.LENGTH_SHORT).show();
                        binding.btnSubmitCarRequest.setEnabled(true);
                        return;
                    }
                    saveCarRequest(plate, model);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.btnSubmitCarRequest.setEnabled(true);
                });
    }

    private void saveCarRequest(String plate, String model) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        CarRegistrationRequest request = new CarRegistrationRequest(
                "", uid, currentUserName, currentUserId, "faculty",
                plate, model, "pending", Timestamp.now()
        );

        db.collection("car_registration_requests").add(request)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Car request submitted — pending admin approval",
                            Toast.LENGTH_SHORT).show();
                    clearCarForm();
                    binding.btnSubmitCarRequest.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.btnSubmitCarRequest.setEnabled(true);
                });
    }

    private void clearCarForm() {
        if (binding.etFacultyCarPlate.getText() != null) {
            binding.etFacultyCarPlate.getText().clear();
        }
        if (binding.etFacultyCarModel.getText() != null) {
            binding.etFacultyCarModel.getText().clear();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adHocListener != null) adHocListener.remove();
        if (overstayListener != null) overstayListener.remove();
    }
}
