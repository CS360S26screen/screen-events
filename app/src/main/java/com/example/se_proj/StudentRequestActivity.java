package com.example.se_proj;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.adapters.StudentGuestLogAdapter;
import com.example.se_proj.databinding.ActivityStudentRequestBinding;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.RequestValidationUtils;
import com.example.se_proj.rules.UserProfileUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Student guest-pass submission screen with single-active-pass and time-clash protections.
 *
 * <p>Design note: thin UI coordinator that delegates domain constraints to
 * {@link RequestValidationUtils} and user identity normalization to {@link UserProfileUtils}.</p>
 *
 * <p>Outstanding issues: profile fallback reads {@code studentId} while most flows use
 * {@code rollNumber}/{@code facultyId}; schema inconsistency should be normalized across user
 * documents.</p>
 */
public class StudentRequestActivity extends AppCompatActivity {

    /** Callback interface for time-picker dialogs. */
    private interface OnTimeSelectedListener {
        void onTimeSelected(String time);
    }

    private ActivityStudentRequestBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private String studentRollNo = "";
    private String studentUid = "";
    private RecyclerView myGuestsRecycler;
    private TextView myGuestsEmptyView;
    private StudentGuestLogAdapter myGuestsAdapter;

    private String selectedDate = "";
    private String selectedStartTime = "";
    private String selectedEndTime = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudentRequestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
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

        binding.toolbar.setNavigationIcon(null);
        setupMyGuestsViews();
        setupBottomNavigation();
        loadUserProfile();

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

        binding.btnSubmit.setOnClickListener(v -> checkLimitAndSubmit());
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_new_pass);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_new_pass) {
                showNewPassForm();
                return true;
            } else if (id == R.id.nav_my_guests) {
                showMyGuestsLog();
                return true;
            } else if (id == R.id.nav_profile) {
                Toast.makeText(this, "Profile coming soon", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
    }

    private void setupMyGuestsViews() {
        View child1 = binding.getRoot().getChildAt(1);
        if (!(child1 instanceof androidx.core.widget.NestedScrollView)) return;
        View scrollChild = ((androidx.core.widget.NestedScrollView) child1).getChildAt(0);
        if (!(scrollChild instanceof ConstraintLayout)) return;
        ConstraintLayout contentRoot = (ConstraintLayout) scrollChild;

        myGuestsAdapter = new StudentGuestLogAdapter(new ArrayList<>());

        myGuestsRecycler = new RecyclerView(this);
        myGuestsRecycler.setId(View.generateViewId());
        myGuestsRecycler.setLayoutManager(new LinearLayoutManager(this));
        myGuestsRecycler.setAdapter(myGuestsAdapter);
        myGuestsRecycler.setVisibility(View.GONE);

        myGuestsEmptyView = new TextView(this);
        myGuestsEmptyView.setId(View.generateViewId());
        myGuestsEmptyView.setText("No guest requests found yet.");
        myGuestsEmptyView.setTextSize(14f);
        myGuestsEmptyView.setVisibility(View.GONE);

        contentRoot.addView(myGuestsRecycler);
        contentRoot.addView(myGuestsEmptyView);

        ConstraintLayout.LayoutParams recyclerParams = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        recyclerParams.topToBottom = R.id.tvHeader;
        recyclerParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        recyclerParams.topMargin = 24;
        myGuestsRecycler.setLayoutParams(recyclerParams);

        ConstraintLayout.LayoutParams emptyParams = new ConstraintLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        emptyParams.topToBottom = R.id.tvHeader;
        emptyParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        emptyParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        emptyParams.topMargin = 48;
        myGuestsEmptyView.setLayoutParams(emptyParams);
    }

    private void showNewPassForm() {
        binding.tvHeader.setText("Register New Guest");
        binding.tilGuestName.setVisibility(View.VISIBLE);
        binding.tilCnic.setVisibility(View.VISIBLE);
        binding.tilVisitDate.setVisibility(View.VISIBLE);
        binding.llTimeSlots.setVisibility(View.VISIBLE);
        binding.btnSubmit.setVisibility(View.VISIBLE);
        if (myGuestsRecycler != null) myGuestsRecycler.setVisibility(View.GONE);
        if (myGuestsEmptyView != null) myGuestsEmptyView.setVisibility(View.GONE);
    }

    private void showMyGuestsLog() {
        binding.tvHeader.setText("My Guests");
        binding.tilGuestName.setVisibility(View.GONE);
        binding.tilCnic.setVisibility(View.GONE);
        binding.tilVisitDate.setVisibility(View.GONE);
        binding.llTimeSlots.setVisibility(View.GONE);
        binding.btnSubmit.setVisibility(View.GONE);
        if (myGuestsRecycler != null) myGuestsRecycler.setVisibility(View.VISIBLE);
        fetchMyGuestsLog();
    }

    private void fetchMyGuestsLog() {
        if (studentRollNo.isBlank()) {
            myGuestsAdapter.updateData(new ArrayList<>());
            if (myGuestsEmptyView != null) myGuestsEmptyView.setVisibility(View.VISIBLE);
            return;
        }

        db.collection("visitor_requests")
                .whereEqualTo("hostId", studentRollNo)
                .whereEqualTo("hostType", "student")
                .get()
                .addOnSuccessListener(documents -> {
                    List<VisitorRequest> list = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : documents.getDocuments()) {
                        VisitorRequest request = doc.toObject(VisitorRequest.class);
                        if (request != null) {
                            if (request.getRequestId().isEmpty()) {
                                request = request.withRequestId(doc.getId());
                            }
                            list.add(request);
                        }
                    }
                    myGuestsAdapter.updateData(list);
                    if (myGuestsEmptyView != null) {
                        myGuestsEmptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("StudentRequest", "Failed to load my guests", e);
                    if (myGuestsEmptyView != null) myGuestsEmptyView.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Unable to load guest history", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadUserProfile() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        studentUid = uid != null ? uid : "";
        if (uid == null || uid.isEmpty()) {
            Toast.makeText(this, "Please log in again to submit a guest pass",
                    Toast.LENGTH_LONG).show();
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
                    Log.e("StudentRequest", "Failed to load user profile", error);
                    Toast.makeText(this, "Unable to load your profile", Toast.LENGTH_LONG).show();
                });
    }

    private void setupProfileData(String docId, java.util.Map<String, Object> data) {
        String rollNumber = (data != null && data.get("rollNumber") != null)
                ? data.get("rollNumber").toString() : null;
        String studentId = (data != null && data.get("studentId") != null)
                ? data.get("studentId").toString() : null;

        studentRollNo = UserProfileUtils.resolveHostId(rollNumber, studentId, docId);
        binding.btnSubmit.setEnabled(!studentRollNo.isEmpty());
        if (binding.bottomNavigation.getSelectedItemId() == R.id.nav_my_guests) {
            fetchMyGuestsLog();
        }
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

    private void checkLimitAndSubmit() {
        String name = binding.etGuestName.getText().toString().trim();
        String cnic = RequestValidationUtils.normalizeCnic(binding.etCnic.getText().toString());

        if (studentRollNo.isEmpty()) {
            Toast.makeText(this, "Your profile is still loading", Toast.LENGTH_SHORT).show();
            return;
        }

        RequestValidationUtils.ValidationResult validation =
                RequestValidationUtils.validateStudentGuestRequest(
                        name, cnic, selectedDate, selectedStartTime, selectedEndTime,
                        LocalDate.now(), LocalTime.now());
        if (!validation.isValid()) {
            Toast.makeText(this, validation.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnSubmit.setEnabled(false);
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";

        db.collection("visitor_requests")
                .whereEqualTo("creatorId", uid)
                .get()
                .addOnSuccessListener(documents -> {
                    List<VisitorRequest> existingRequests = documents.toObjects(VisitorRequest.class);

                    if (RequestValidationUtils.hasBlockingStudentPass(existingRequests)) {
                        binding.btnSubmit.setEnabled(true);
                        Toast.makeText(this,
                                "You already have a pending guest pass. Wait for approval first.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (RequestValidationUtils.hasTimeClashWithApproved(
                            existingRequests, selectedDate, selectedStartTime, selectedEndTime)) {
                        binding.btnSubmit.setEnabled(true);
                        Toast.makeText(this,
                                "Time clashes with an already approved guest pass.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    boolean hasDuplicate = false;
                    for (VisitorRequest r : existingRequests) {
                        if (RequestValidationUtils.matchesDuplicateCandidate(
                                r, studentRollNo, cnic, selectedDate)) {
                            hasDuplicate = true;
                            break;
                        }
                    }

                    if (hasDuplicate) {
                        binding.btnSubmit.setEnabled(true);
                        Toast.makeText(this,
                                "A similar guest pass already exists for this visitor and date.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        saveVisitorRequest(name, cnic, studentRollNo);
                    }
                })
                .addOnFailureListener(e -> {
                    binding.btnSubmit.setEnabled(true);
                    Log.e("FirestoreError", "Error checking limits", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveVisitorRequest(String name, String cnic, String rollNo) {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        VisitorRequest request = new VisitorRequest(
                "", name, cnic, "Student Guest Visit",
                selectedDate, selectedStartTime, selectedEndTime,
                rollNo, uid, "student", RequestStatus.PENDING, null, null, false
        );

        db.collection("visitor_requests")
                .add(request)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Request Submitted for Approval", Toast.LENGTH_SHORT).show();
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
        if (binding.etVisitDate.getText() != null) binding.etVisitDate.getText().clear();
        if (binding.etStartTime.getText() != null) binding.etStartTime.getText().clear();
        if (binding.etEndTime.getText() != null) binding.etEndTime.getText().clear();
        selectedDate = "";
        selectedStartTime = "";
        selectedEndTime = "";
        binding.btnSubmit.setEnabled(true);
    }
}
