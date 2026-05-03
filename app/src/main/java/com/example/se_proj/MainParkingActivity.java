package com.example.se_proj;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.se_proj.adapters.ParkingVehicleAdapter;
import com.example.se_proj.databinding.ActivityMainParkingBinding;
import com.example.se_proj.models.RegisteredVehicle;
import com.example.se_proj.rules.ParkingOccupancyUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real-time parking occupancy dashboard with live vehicle list.
 *
 * <p>Manual ±1 override buttons are shown only to admin users; automated parking updates
 * happen through guard check-in/out and car exit flows. The live vehicle list streams
     * from {@code cars_registered} where {@code onCampus == true}.</p>
 */
public class MainParkingActivity extends AppCompatActivity {

    private ActivityMainParkingBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ParkingVehicleAdapter vehicleAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainParkingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupToolbar();
        setupDrawer();
        setupBottomNavigation();
        setupVehicleList();
        ensureParkingDocument();
        setupParkingCounter();
        checkAdminAndShowButtons();

        binding.btnParkingPlus.setOnClickListener(v -> updateParking(1));
        binding.btnParkingMinus.setOnClickListener(v -> updateParking(-1));
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v ->
                binding.drawerLayout.openDrawer(GravityCompat.START));
    }

    private void setupDrawer() {
        binding.drawerNavigation.setCheckedItem(R.id.drawer_main_parking);
        binding.drawerNavigation.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.drawer_in_gate) {
                startActivity(new Intent(this, GuardDashboardActivity.class));
                finish();
                return true;
            } else if (id == R.id.drawer_out_gate) {
                Intent intent = new Intent(this, GuardDashboardActivity.class);
                intent.putExtra(GuardDashboardActivity.EXTRA_GATE_MODE,
                        GuardDashboardActivity.MODE_OUT_GATE);
                startActivity(intent);
                finish();
                return true;
            } else if (id == R.id.drawer_main_parking) {
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            } else if (id == R.id.drawer_wing_scanner) {
                startActivity(new Intent(this, WingScannerActivity.class));
                binding.drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
            return false;
        });
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, GuardDashboardActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_logs) {
                startActivity(new Intent(this, AdminAuditActivity.class));
                return true;
            } else if (id == R.id.nav_adhoc) {
                startActivity(new Intent(this, WalkInRegistrationActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                Toast.makeText(this, "Settings not available yet", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
    }

    // -------------------------------------------------------------------------
    // Parking counter
    // -------------------------------------------------------------------------

    private void setupParkingCounter() {
        db.collection("system_metadata").document("parking_status")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;
                    long occupancy = snapshot.getLong("currentOccupancy") != null
                            ? snapshot.getLong("currentOccupancy") : 0;
                    long capacity = snapshot.getLong("maxCapacity") != null
                            ? snapshot.getLong("maxCapacity") : 200;

                    binding.tvParkingCounter.setText(occupancy + " / " + capacity);
                    binding.pbParking.setMax((int) capacity);
                    binding.pbParking.setProgress((int) occupancy);

                    float ratio = (float) occupancy / capacity;
                    int color;
                    if (ratio > 0.9f) {
                        color = R.color.status_denied_text;
                    } else if (ratio > 0.7f) {
                        color = android.R.color.holo_orange_dark;
                    } else {
                        color = R.color.primary_purple;
                    }
                    binding.pbParking.setIndicatorColor(ContextCompat.getColor(this, color));
                });
    }

    // -------------------------------------------------------------------------
    // Live vehicle list
    // -------------------------------------------------------------------------

    private void setupVehicleList() {
        vehicleAdapter = new ParkingVehicleAdapter(new ArrayList<>());
        binding.rvParkingVehicles.setLayoutManager(new LinearLayoutManager(this));
        binding.rvParkingVehicles.setAdapter(vehicleAdapter);

        db.collection("cars_registered")
                .whereEqualTo("onCampus", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    List<RegisteredVehicle> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        RegisteredVehicle v = doc.toObject(RegisteredVehicle.class);
                        if (v != null) list.add(v);
                    }
                    vehicleAdapter.updateData(list);
                    binding.tvNoVehicles.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.rvParkingVehicles.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    // -------------------------------------------------------------------------
    // Admin role check
    // -------------------------------------------------------------------------

    private void checkAdminAndShowButtons() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) return;

        db.collection("Users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    if ("admin".equals(role)) {
                        binding.llAdminButtons.setVisibility(View.VISIBLE);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Parking document bootstrap
    // -------------------------------------------------------------------------

    private void ensureParkingDocument() {
        com.google.firebase.firestore.DocumentReference docRef =
                db.collection("system_metadata").document("parking_status");
        docRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Map<String, Object> data = new HashMap<>();
                data.put("currentOccupancy", 0L);
                data.put("maxCapacity", 200L);
                docRef.set(data);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Admin manual override
    // -------------------------------------------------------------------------

    private void updateParking(long delta) {
        com.google.firebase.firestore.DocumentReference documentRef =
                db.collection("system_metadata").document("parking_status");

        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot =
                    transaction.get(documentRef);
            long currentOccupancy =
                    snapshot.exists() && snapshot.getLong("currentOccupancy") != null
                            ? snapshot.getLong("currentOccupancy") : 0L;
            long maxCapacity =
                    snapshot.exists() && snapshot.getLong("maxCapacity") != null
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
            String errorMsg;
            if (e instanceof FirebaseFirestoreException
                    && ((FirebaseFirestoreException) e).getCode()
                    == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                errorMsg = "Permission denied for parking update";
            } else {
                errorMsg = "Unable to update parking: " + e.getMessage();
            }
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
        });
    }
}
