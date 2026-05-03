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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.se_proj.adapters.PendingCarRequestAdapter;
import com.example.se_proj.adapters.StudentGuestLogAdapter;
import com.example.se_proj.adapters.VehicleListAdapter;
import com.example.se_proj.databinding.ActivityStudentRequestBinding;
import com.example.se_proj.models.CarRegistrationRequest;
import com.example.se_proj.models.RegisteredVehicle;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.RequestValidationUtils;
import com.example.se_proj.rules.UserProfileUtils;
import com.example.se_proj.rules.VehicleRegistrationUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Student dashboard with four tabs: New Pass, My Guests, My Cars, Wing Access.
 *
 * <p><b>My Cars tab</b>: students submit a registration request (plate + model) which goes to
 * admin for approval. Pending requests are shown with a status chip and can be cancelled.
 * Admin-approved cars are shown read-only from {@code cars_registered}.</p>
 *
 * <p>The 2-vehicle cap counts pending AND approved vehicles so students cannot queue unlimited
 * requests.</p>
 */
public class StudentRequestActivity extends AppCompatActivity {

    public static final String EXTRA_SELECTED_TAB = "selected_tab";

    private interface OnTimeSelectedListener {
        void onTimeSelected(String time);
    }

    private ActivityStudentRequestBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private String studentRollNo = "";
    private String studentUid = "";
    private String studentName = "";

    // My Guests views (added programmatically)
    private RecyclerView myGuestsRecycler;
    private TextView myGuestsEmptyView;
    private StudentGuestLogAdapter myGuestsAdapter;

    // My Cars — approved vehicles from cars_registered
    private RecyclerView carsRecycler;
    private TextView carsEmptyView;
    private VehicleListAdapter carsAdapter;

    // My Cars — pending requests from car_registration_requests
    private RecyclerView pendingRequestsRecycler;
    private TextView pendingRequestsEmptyView;
    private PendingCarRequestAdapter pendingRequestsAdapter;

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
        setupMyCarsViews();
        setupBottomNavigation();
        setupMyCarsBottomNavigation();
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
        binding.myCarsLayout.btnRegisterCar.setOnClickListener(v -> submitCarRequest());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding == null) return;

        int selectedNavId = binding.bottomNavigation.getSelectedItemId();
        if (selectedNavId == R.id.nav_my_guests) {
            fetchMyGuestsLog();
        } else if (selectedNavId == R.id.nav_my_cars) {
            loadApprovedCars();
            loadPendingCarRequests();
        }
    }

    // -------------------------------------------------------------------------
    // Bottom navigation
    // -------------------------------------------------------------------------

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_new_pass) {
                showNewPassForm();
                return true;
            } else if (id == R.id.nav_my_guests) {
                showMyGuestsLog();
                return true;
            } else if (id == R.id.nav_my_cars) {
                showMyCars();
                return true;
            } else if (id == R.id.nav_wing_access) {
                startActivity(new Intent(this, WingAccessRequestActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });

        int selectedTab = getIntent().getIntExtra(EXTRA_SELECTED_TAB, R.id.nav_new_pass);
        binding.bottomNavigation.setSelectedItemId(selectedTab);
    }

    private void setupMyCarsBottomNavigation() {
        binding.myCarsLayout.bottomNavigation.setSelectedItemId(R.id.nav_my_cars);
        binding.myCarsLayout.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_my_cars) {
                return true;
            } else if (id == R.id.nav_new_pass || id == R.id.nav_my_guests) {
                binding.bottomNavigation.setSelectedItemId(id);
                return true;
            } else if (id == R.id.nav_wing_access) {
                startActivity(new Intent(this, WingAccessRequestActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    // -------------------------------------------------------------------------
    // Tab visibility helpers
    // -------------------------------------------------------------------------

    private void showNewPassForm() {
        binding.myCarsLayout.getRoot().setVisibility(View.GONE);
        binding.appBarLayout.setVisibility(View.VISIBLE);
        binding.studentContentScroll.setVisibility(View.VISIBLE);
        binding.bottomNavigation.setVisibility(View.VISIBLE);
        binding.tvHeader.setText("Register New Guest");
        binding.tilGuestName.setVisibility(View.VISIBLE);
        binding.tilCnic.setVisibility(View.VISIBLE);
        binding.tilVisitDate.setVisibility(View.VISIBLE);
        binding.llTimeSlots.setVisibility(View.VISIBLE);
        binding.btnSubmit.setVisibility(View.VISIBLE);
        setMyGuestsVisible(false);
        setMyCarsVisible(false);
    }

    private void showMyGuestsLog() {
        binding.myCarsLayout.getRoot().setVisibility(View.GONE);
        binding.appBarLayout.setVisibility(View.VISIBLE);
        binding.studentContentScroll.setVisibility(View.VISIBLE);
        binding.bottomNavigation.setVisibility(View.VISIBLE);
        binding.tvHeader.setText("My Guests");
        binding.tilGuestName.setVisibility(View.GONE);
        binding.tilCnic.setVisibility(View.GONE);
        binding.tilVisitDate.setVisibility(View.GONE);
        binding.llTimeSlots.setVisibility(View.GONE);
        binding.btnSubmit.setVisibility(View.GONE);
        setMyCarsVisible(false);
        setMyGuestsVisible(true);
        fetchMyGuestsLog();
    }

    private void showMyCars() {
        binding.appBarLayout.setVisibility(View.GONE);
        binding.studentContentScroll.setVisibility(View.GONE);
        binding.bottomNavigation.setVisibility(View.GONE);
        binding.myCarsLayout.getRoot().setVisibility(View.VISIBLE);
        binding.myCarsLayout.bottomNavigation.setSelectedItemId(R.id.nav_my_cars);
        setMyGuestsVisible(false);
        setMyCarsVisible(true);
        loadApprovedCars();
        loadPendingCarRequests();
    }

    private void setMyGuestsVisible(boolean visible) {
        if (myGuestsRecycler != null)
            myGuestsRecycler.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (myGuestsEmptyView != null)
            myGuestsEmptyView.setVisibility(View.GONE);
    }

    private void setMyCarsVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        if (carsRecycler != null) carsRecycler.setVisibility(v);
        if (carsEmptyView != null) carsEmptyView.setVisibility(View.GONE);
        if (pendingRequestsRecycler != null) pendingRequestsRecycler.setVisibility(v);
        if (pendingRequestsEmptyView != null) pendingRequestsEmptyView.setVisibility(View.GONE);
    }

    // -------------------------------------------------------------------------
    // My Guests
    // -------------------------------------------------------------------------

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
                    for (DocumentSnapshot doc : documents.getDocuments()) {
                        VisitorRequest request = doc.toObject(VisitorRequest.class);
                        if (request != null) {
                            if (RequestStatus.CANCELLED.equals(
                                    RequestStatus.normalize(request.getStatus()))) {
                                continue;
                            }
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

    // -------------------------------------------------------------------------
    // My Cars — programmatic views setup
    // -------------------------------------------------------------------------

    private void setupMyCarsViews() {
        // Approved cars (read-only, no delete button)
        carsAdapter = new VehicleListAdapter(new ArrayList<>(), false, vehicle -> {});

        carsRecycler = binding.myCarsLayout.rvApprovedCars;
        carsRecycler.setLayoutManager(new LinearLayoutManager(this));
        carsRecycler.setAdapter(carsAdapter);
        carsRecycler.setVisibility(View.GONE);

        carsEmptyView = binding.myCarsLayout.tvMyCarsEmpty;
        carsEmptyView.setVisibility(View.GONE);

        // Pending car requests
        pendingRequestsAdapter = new PendingCarRequestAdapter(
                new ArrayList<>(), this::cancelCarRequest);

        pendingRequestsRecycler = binding.myCarsLayout.rvPendingCarRequests;
        pendingRequestsRecycler.setLayoutManager(new LinearLayoutManager(this));
        pendingRequestsRecycler.setAdapter(pendingRequestsAdapter);
        pendingRequestsRecycler.setVisibility(View.GONE);

        pendingRequestsEmptyView = binding.myCarsLayout.tvPendingCarRequestsEmpty;
        pendingRequestsEmptyView.setVisibility(View.GONE);
    }

    // -------------------------------------------------------------------------
    // My Cars — data loading
    // -------------------------------------------------------------------------

    /** Loads admin-approved vehicles for this student from {@code cars_registered}. */
    private void loadApprovedCars() {
        if (studentUid.isEmpty()) {
            if (carsEmptyView != null) carsEmptyView.setVisibility(View.VISIBLE);
            return;
        }

        db.collection("cars_registered")
                .whereEqualTo("studentUid", studentUid)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e("StudentRequest", "Failed to load approved cars", e);
                        return;
                    }
                    List<RegisteredVehicle> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            RegisteredVehicle v = doc.toObject(RegisteredVehicle.class);
                            if (v != null) list.add(v);
                        }
                    }
                    carsAdapter.updateData(list);
                    binding.myCarsLayout.tvRegisteredVehiclesCount.setText(
                            String.format(Locale.getDefault(), "%d total", list.size()));
                    if (carsEmptyView != null) {
                        carsEmptyView.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    /** Loads pending/rejected car requests for this student from {@code car_registration_requests}. */
    private void loadPendingCarRequests() {
        if (studentUid.isEmpty()) return;

        db.collection("car_registration_requests")
                .whereEqualTo("studentUid", studentUid)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e("StudentRequest", "Failed to load car requests", e);
                        return;
                    }
                    List<CarRegistrationRequest> list = new ArrayList<>();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            CarRegistrationRequest r = doc.toObject(CarRegistrationRequest.class);
                            if (r != null) list.add(r);
                        }
                    }
                    pendingRequestsAdapter.updateData(list);
                    if (pendingRequestsEmptyView != null) {
                        pendingRequestsEmptyView.setVisibility(
                                list.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Car request submission
    // -------------------------------------------------------------------------

    private void submitCarRequest() {
        String plate = binding.myCarsLayout.etLicensePlate.getText() != null
                ? binding.myCarsLayout.etLicensePlate.getText().toString().trim().toUpperCase() : "";
        String model = binding.myCarsLayout.etCarModel.getText() != null
                ? binding.myCarsLayout.etCarModel.getText().toString().trim() : "";

        VehicleRegistrationUtils.ValidationResult validation =
                VehicleRegistrationUtils.validateCarRequest(plate, model);
        if (!validation.isValid()) {
            Toast.makeText(this, validation.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (studentUid.isEmpty()) {
            Toast.makeText(this, "Your profile is still loading", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.myCarsLayout.btnRegisterCar.setEnabled(false);

        // Count pending + approved together against the 2-vehicle cap
        db.collection("car_registration_requests")
                .whereEqualTo("studentUid", studentUid)
                .whereEqualTo("status", "pending")
                .get()
                .addOnSuccessListener(pendingSnap -> {
                    db.collection("cars_registered")
                            .whereEqualTo("studentUid", studentUid)
                            .get()
                            .addOnSuccessListener(approvedSnap -> {
                                int total = pendingSnap.size() + approvedSnap.size();
                                if (VehicleRegistrationUtils.isAtCarLimit(total)) {
                                    Toast.makeText(this,
                                            "Maximum 2 vehicles allowed per student",
                                            Toast.LENGTH_SHORT).show();
                                    binding.myCarsLayout.btnRegisterCar.setEnabled(true);
                                    return;
                                }
                                checkPlateAndSubmit(plate, model);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                                binding.myCarsLayout.btnRegisterCar.setEnabled(true);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.myCarsLayout.btnRegisterCar.setEnabled(true);
                });
    }

    private void checkPlateAndSubmit(String plate, String model) {
        // Check plate not already pending/approved for this user
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
                        binding.myCarsLayout.btnRegisterCar.setEnabled(true);
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
                                    binding.myCarsLayout.btnRegisterCar.setEnabled(true);
                                    return;
                                }
                                saveCarRequest(plate, model);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Error: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                                binding.myCarsLayout.btnRegisterCar.setEnabled(true);
                            });
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.myCarsLayout.btnRegisterCar.setEnabled(true);
                });
    }

    private void saveCarRequest(String plate, String model) {
        CarRegistrationRequest request = new CarRegistrationRequest(
                "", studentUid, studentName, studentRollNo, "student",
                plate, model, "pending", Timestamp.now()
        );

        db.collection("car_registration_requests").add(request)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Car request submitted — pending admin approval",
                            Toast.LENGTH_SHORT).show();
                    clearCarForm();
                    binding.myCarsLayout.btnRegisterCar.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    binding.myCarsLayout.btnRegisterCar.setEnabled(true);
                });
    }

    private void cancelCarRequest(CarRegistrationRequest request) {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Request")
                .setMessage("Cancel request for " + request.getLicensePlate() + "?")
                .setPositiveButton("Yes, Cancel", (d, w) ->
                        db.collection("car_registration_requests")
                                .document(request.getRequestId())
                                .delete()
                                .addOnSuccessListener(u ->
                                        Toast.makeText(this, "Request cancelled",
                                                Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show()))
                .setNegativeButton("No", null)
                .show();
    }

    private void clearCarForm() {
        if (binding.myCarsLayout.etLicensePlate.getText() != null) binding.myCarsLayout.etLicensePlate.getText().clear();
        if (binding.myCarsLayout.etCarModel.getText() != null) binding.myCarsLayout.etCarModel.getText().clear();
    }

    // -------------------------------------------------------------------------
    // User profile
    // -------------------------------------------------------------------------

    private void loadUserProfile() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        studentUid = uid != null ? uid : "";
        if (uid == null || uid.isEmpty()) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_LONG).show();
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
                                            DocumentSnapshot d = query.getDocuments().get(0);
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
        String name = (data != null && data.get("name") != null)
                ? data.get("name").toString() : null;
        String email = (data != null && data.get("email") != null)
                ? data.get("email").toString()
                : (auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : null);

        studentRollNo = UserProfileUtils.resolveHostId(rollNumber, studentId, docId, email);
        studentName = name != null ? name : "";
        binding.btnSubmit.setEnabled(!studentRollNo.isEmpty());

        int selectedNavId = binding.bottomNavigation.getSelectedItemId();
        if (selectedNavId == R.id.nav_my_guests) {
            fetchMyGuestsLog();
        } else if (selectedNavId == R.id.nav_my_cars) {
            loadApprovedCars();
            loadPendingCarRequests();
        }
    }

    // -------------------------------------------------------------------------
    // Guest pass form
    // -------------------------------------------------------------------------

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
                    clearGuestForm();
                })
                .addOnFailureListener(e -> {
                    binding.btnSubmit.setEnabled(true);
                    Log.e("FirestoreError", "Error adding document", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void clearGuestForm() {
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
