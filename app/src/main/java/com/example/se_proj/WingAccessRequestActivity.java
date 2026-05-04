package com.example.se_proj;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.se_proj.databinding.ActivityWingAccessRequestBinding;
import com.example.se_proj.models.WingAccessRequest;
import com.example.se_proj.rules.UserProfileUtils;
import com.example.se_proj.rules.WingConstants;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Wing/lab access request screen for students and faculty.
 *
 * <p>Students request wing access for themselves; their roll number and name are auto-filled
 * from their Firestore profile after {@link #loadUserProfile()} completes. The submit button
 * is disabled until the profile has loaded to prevent a race condition.</p>
 *
 * <p>A reason is required. Submitted requests appear in admin's Wing Access Management
 * screen for approval.</p>
 */
public class WingAccessRequestActivity extends AppCompatActivity {

    private ActivityWingAccessRequestBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private String userRollNo = "";
    private String userName = "";
    private boolean profileLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWingAccessRequestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNavigation.setSelectedItemId(R.id.nav_wing_access);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_wing_access) {
                return true;
            }

            Intent intent = new Intent(this, StudentRequestActivity.class);
            if (id == R.id.nav_my_guests) {
                intent.putExtra(StudentRequestActivity.EXTRA_SELECTED_TAB, R.id.nav_my_guests);
            } else if (id == R.id.nav_my_cars) {
                intent.putExtra(StudentRequestActivity.EXTRA_SELECTED_TAB, R.id.nav_my_cars);
            } else {
                intent.putExtra(StudentRequestActivity.EXTRA_SELECTED_TAB, R.id.nav_new_pass);
            }
            startActivity(intent);
            overridePendingTransition(0, 0);
            finish();
            overridePendingTransition(0, 0);
            return true;
        });

        ArrayAdapter<String> wingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, WingConstants.ALL_WINGS);
        binding.actvWing.setAdapter(wingAdapter);

        binding.btnSubmitWingRequest.setEnabled(false);
        loadUserProfile();
        binding.btnSubmitWingRequest.setOnClickListener(v -> submitRequest());
    }

    private void loadUserProfile() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_LONG).show();
            return;
        }

        db.collection("Users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "User profile not found", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String n = doc.getString("name");
                    userName = n != null ? n : "";
                    userRollNo = UserProfileUtils.resolveHostId(
                            doc.getString("rollNumber"),
                            doc.getString("studentId"),
                            doc.getId(),
                            doc.getString("email")
                    );
                    binding.tvSelfRollDisplay.setText(userRollNo);
                    profileLoaded = true;
                    binding.btnSubmitWingRequest.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load profile: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void submitRequest() {
        if (!profileLoaded) {
            Toast.makeText(this, "Your profile is still loading, please wait",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String wing = binding.actvWing.getText().toString().trim();
        if (wing.isEmpty()) {
            Toast.makeText(this, "Please select a wing", Toast.LENGTH_SHORT).show();
            return;
        }

        String reason = binding.etReason.getText() != null
                ? binding.etReason.getText().toString().trim() : "";
        if (reason.isEmpty()) {
            Toast.makeText(this, "Please provide a reason for access", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userRollNo.isEmpty()) {
            Toast.makeText(this, "Your roll number is missing — contact support",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";

        WingAccessRequest request = new WingAccessRequest(
                "", userRollNo, userName, wing,
                uid, "student", "", "pending", reason, Timestamp.now()
        );

        binding.btnSubmitWingRequest.setEnabled(false);
        db.collection("wing_access_requests").add(request)
                .addOnSuccessListener(docRef -> {
                    Toast.makeText(this, "Request submitted for admin approval",
                            Toast.LENGTH_SHORT).show();
                    binding.tvWingRequestStatus.setVisibility(View.VISIBLE);
                    binding.tvWingRequestStatus.setText("Request submitted successfully.");
                    binding.tvWingRequestStatus.setTextColor(
                            getColor(R.color.status_approved_text));
                    binding.btnSubmitWingRequest.setEnabled(true);
                    clearForm();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to submit: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    binding.btnSubmitWingRequest.setEnabled(true);
                });
    }

    private void clearForm() {
        binding.actvWing.setText("", false);
        if (binding.etReason.getText() != null) binding.etReason.getText().clear();
    }
}
