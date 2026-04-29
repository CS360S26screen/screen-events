package com.example.se_proj;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.se_proj.databinding.ActivityWingAccessRequestBinding;
import com.example.se_proj.models.WingAccessRequest;
import com.example.se_proj.rules.WingConstants;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Wing/lab access request screen.
 *
 * <p>Students request access for themselves (roll number auto-filled from profile).
 * Faculty may switch to "On Behalf" mode and supply the target student's details.
 * Submitted requests are queued for admin approval in {@code wing_access_requests}.</p>
 */
public class WingAccessRequestActivity extends AppCompatActivity {

    private ActivityWingAccessRequestBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private String userRole = "student";
    private String userRollNo = "";
    private String userName = "";
    private String userFacultyId = "";
    private boolean onBehalfMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWingAccessRequestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        ArrayAdapter<String> wingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, WingConstants.ALL_WINGS);
        binding.actvWing.setAdapter(wingAdapter);

        binding.toggleRequestMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnModeMyself) {
                onBehalfMode = false;
                binding.llOnBehalfFields.setVisibility(View.GONE);
                binding.llSelfFields.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.btnModeOnBehalf) {
                onBehalfMode = true;
                binding.llSelfFields.setVisibility(View.GONE);
                binding.llOnBehalfFields.setVisibility(View.VISIBLE);
            }
        });

        loadUserProfile();
        binding.btnSubmitWingRequest.setOnClickListener(v -> submitRequest());
    }

    private void loadUserProfile() {
        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (uid == null) return;

        db.collection("Users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String role = doc.getString("role");
                    userRole = role != null ? role : "student";

                    if ("faculty".equals(userRole)) {
                        String fId = doc.getString("facultyId");
                        userFacultyId = fId != null ? fId : "";
                        String n = doc.getString("name");
                        userName = n != null ? n : "";
                        binding.btnModeOnBehalf.setVisibility(View.VISIBLE);
                    } else {
                        String roll = doc.getString("rollNumber");
                        if (roll == null) roll = doc.getString("studentId");
                        userRollNo = roll != null ? roll : "";
                        String n = doc.getString("name");
                        userName = n != null ? n : "";
                        binding.tvSelfRollDisplay.setText(userRollNo);
                        binding.btnModeOnBehalf.setVisibility(View.GONE);
                    }
                });
    }

    private void submitRequest() {
        String wing = binding.actvWing.getText().toString().trim();
        if (wing.isEmpty()) {
            Toast.makeText(this, "Please select a wing", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        String studentRoll;
        String studentName;
        String requesterType;
        String facultyId;

        if (onBehalfMode) {
            studentName = binding.etStudentName.getText() != null
                    ? binding.etStudentName.getText().toString().trim() : "";
            studentRoll = binding.etStudentRoll.getText() != null
                    ? binding.etStudentRoll.getText().toString().trim() : "";
            requesterType = "faculty";
            facultyId = userFacultyId;
            if (studentName.isEmpty() || studentRoll.isEmpty()) {
                Toast.makeText(this, "Please fill in all student details", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            studentRoll = userRollNo;
            studentName = userName;
            requesterType = "student";
            facultyId = "";
            if (studentRoll.isEmpty()) {
                Toast.makeText(this, "Your profile is still loading", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        WingAccessRequest request = new WingAccessRequest(
                "", studentRoll, studentName, wing,
                uid, requesterType, facultyId, "pending", Timestamp.now()
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
        if (binding.etStudentName.getText() != null) binding.etStudentName.getText().clear();
        if (binding.etStudentRoll.getText() != null) binding.etStudentRoll.getText().clear();
    }
}
