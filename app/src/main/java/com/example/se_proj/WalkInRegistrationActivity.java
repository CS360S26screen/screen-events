package com.example.se_proj;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.se_proj.databinding.ActivityWalkInRegistrationBinding;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.RequestStatus;
import com.example.se_proj.rules.RequestValidationUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Captures guard-initiated walk-in visitor requests and waits for host approval in real time.
 *
 * <p>Design note: event-driven workflow using a Firestore document listener to update UI as the
 * request status transitions from {@code pending_adhoc} to {@code approved}/{@code denied}.</p>
 *
 * <p>Outstanding issues: listener lifecycle is tied to this screen only; if the guard navigates
 * away, approval updates are missed until the request is searched again.</p>
 */
public class WalkInRegistrationActivity extends AppCompatActivity {

    private ActivityWalkInRegistrationBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private ListenerRegistration listenerRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWalkInRegistrationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finishWithoutAnimation());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_logout) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finishWithoutAnimation();
                return true;
            }
            return false;
        });

        binding.btnSubmitWalkIn.setOnClickListener(v -> submitWalkInRequest());
        setupBottomNavigation();
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_adhoc);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_adhoc) {
                return true;
            } else if (id == R.id.nav_home) {
                startGuardPortalActivity(new Intent(this, GuardDashboardActivity.class), true);
                return true;
            } else if (id == R.id.nav_logs) {
                Intent intent = new Intent(this, AdminAuditActivity.class);
                intent.putExtra(AdminAuditActivity.EXTRA_SHOW_GUARD_NAV, true);
                startGuardPortalActivity(intent, true);
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
            finishWithoutAnimation();
        }
    }

    private void submitWalkInRequest() {
        String hostId = binding.etHostId.getText().toString().trim();
        String name = binding.etGuestName.getText().toString().trim();
        String cnic = RequestValidationUtils.normalizeCnic(binding.etCnic.getText().toString());
        String purpose = binding.etPurpose.getText().toString().trim();

        RequestValidationUtils.ValidationResult validation =
                RequestValidationUtils.validateWalkInRequest(hostId, name, cnic, purpose);
        if (!validation.isValid()) {
            Toast.makeText(this, validation.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null || uid.isEmpty()) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_LONG).show();
            return;
        }

        String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String currentDate = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());

        VisitorRequest request = new VisitorRequest(
                "", name, cnic, purpose, currentDate, currentTime, "23:59",
                hostId, uid, "faculty", RequestStatus.PENDING_ADHOC, null, null, false
        );

        binding.btnSubmitWalkIn.setEnabled(false);

        db.collection("visitor_requests")
                .add(request)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Request sent to Faculty for approval", Toast.LENGTH_SHORT).show();
                    listenForApproval(documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    binding.btnSubmitWalkIn.setEnabled(true);
                    Log.e("WalkIn", "Failed to send request", e);
                    Toast.makeText(this, "Failed to send request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void listenForApproval(String requestId) {
        binding.tvApprovalStatus.setVisibility(View.VISIBLE);
        binding.tvApprovalStatus.setText("Waiting for Faculty Approval...");

        listenerRegistration = db.collection("visitor_requests").document(requestId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w("FirestoreListener", "Listen failed.", e);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        String status = snapshot.getString("status");
                        if ("approved".equals(status)) {
                            binding.tvApprovalStatus.setText("APPROVED! You can now check them in.");
                            binding.tvApprovalStatus.setTextColor(
                                    getResources().getColor(android.R.color.holo_green_dark, getTheme()));
                            Toast.makeText(this, "Request Approved!", Toast.LENGTH_LONG).show();
                        } else if ("denied".equals(status)) {
                            binding.tvApprovalStatus.setText("DENIED by Faculty.");
                            binding.tvApprovalStatus.setTextColor(
                                    getResources().getColor(android.R.color.holo_red_dark, getTheme()));
                            binding.btnSubmitWalkIn.setEnabled(true);
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }

    private void finishWithoutAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }
}
