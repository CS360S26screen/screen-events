package com.example.se_proj;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.se_proj.databinding.ActivityManageActivePassBinding;
import com.example.se_proj.models.VisitorRequest;
import com.example.se_proj.rules.RequestStatus;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Displays the details of a student's active guest pass and allows cancellation.
 *
 * <p>Accepts an optional {@code REQUEST_ID} Intent extra; if absent, queries Firestore for
 * the currently active on-campus pass belonging to this user.</p>
 */
public class ManageActivePassActivity extends AppCompatActivity {

    private ActivityManageActivePassBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String requestId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManageActivePassBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        requestId = getIntent().getStringExtra("REQUEST_ID");

        if (requestId != null) {
            loadPassDetails(requestId);
        } else {
            findActivePass();
        }

        binding.btnCancelPass.setOnClickListener(v -> showCancelConfirmation());

        binding.bottomNavigation.setSelectedItemId(R.id.nav_my_guests);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_new_pass) {
                finish();
                return true;
            }
            return true;
        });
    }

    private void loadPassDetails(String id) {
        db.collection("visitor_requests").document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    VisitorRequest request = doc.toObject(VisitorRequest.class);
                    if (request != null) {
                        displayDetails(request);
                    }
                });
    }

    private void findActivePass() {
        String studentRollNo = "27100xxx"; // Get from context
        db.collection("visitor_requests")
                .whereEqualTo("hostId", studentRollNo)
                .whereEqualTo("status", "approved")
                .whereEqualTo("onCampus", true)
                .limit(1)
                .get()
                .addOnSuccessListener(documents -> {
                    if (!documents.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot doc = documents.getDocuments().get(0);
                        requestId = doc.getId();
                        VisitorRequest request = doc.toObject(VisitorRequest.class);
                        if (request != null) {
                            displayDetails(request);
                        }
                    } else {
                        Toast.makeText(this, "No active pass found", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void displayDetails(VisitorRequest request) {
        binding.tvGuestName.setText(request.getGuestName());
        binding.tvCnic.setText(request.getGuestCNIC());
        binding.tvVisitWindow.setText("Today, " + request.getStartTime() + " - " + request.getEndTime());

        String statusText = request.isOnCampus()
                ? "Status: Inside Campus"
                : "Status: Approved & Expected";
        binding.chipStatus.setText(statusText);
    }

    private void showCancelConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Guest Pass")
                .setMessage("Are you sure you want to cancel this pass? This will immediately revoke access.")
                .setPositiveButton("Cancel Pass", (dialog, which) -> cancelPass())
                .setNegativeButton("Keep Pass", null)
                .show();
    }

    private void cancelPass() {
        if (requestId == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", RequestStatus.CANCELLED);
        updates.put("onCampus", false);

        db.collection("visitor_requests").document(requestId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Pass Cancelled Successfully", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
