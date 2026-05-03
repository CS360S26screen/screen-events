package com.example.se_proj;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.se_proj.databinding.ActivityWingScannerBinding;
import com.example.se_proj.models.WingAccessPermission;
import com.example.se_proj.models.ZoneAccessLog;
import com.example.se_proj.rules.WingAccessPermissionUtils;
import com.example.se_proj.rules.WingConstants;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Self-service wing access scanner (simulated kiosk).
 *
 * <p>The student enters their roll number and selects which wing they want to enter.
 * The system checks {@code wing_access_permissions} for a valid, active, non-expired
 * permission and shows a green "ACCESS GRANTED" or red "ACCESS DENIED" result.
 * Every attempt — regardless of outcome — is logged to {@code zone_access_logs}.</p>
 *
 * <p>No Firebase Authentication is required; this screen is intended to be left
 * open on a tablet at each wing entrance.</p>
 */
public class WingScannerActivity extends AppCompatActivity {

    private ActivityWingScannerBinding binding;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWingScannerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        ArrayAdapter<String> wingAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, WingConstants.ALL_WINGS);
        binding.actvScannerWing.setAdapter(wingAdapter);

        binding.btnCheckAccess.setOnClickListener(v -> checkAccess());
    }

    private void checkAccess() {
        String roll = binding.etScannerRoll.getText() != null
                ? binding.etScannerRoll.getText().toString().trim() : "";
        String wing = binding.actvScannerWing.getText().toString().trim();

        if (roll.isEmpty()) {
            Toast.makeText(this, "Please enter a roll number", Toast.LENGTH_SHORT).show();
            return;
        }
        if (wing.isEmpty()) {
            Toast.makeText(this, "Please select a wing", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("wing_access_permissions")
                .whereEqualTo("studentRollNo", roll)
                .whereEqualTo("wing", wing)
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<WingAccessPermission> permissions = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : snapshots.getDocuments()) {
                        WingAccessPermission p = doc.toObject(WingAccessPermission.class);
                        if (p != null) permissions.add(p);
                    }

                    Calendar today = Calendar.getInstance();
                    WingAccessPermission validPerm = null;
                    boolean hasExpired = false;
                    for (WingAccessPermission perm : permissions) {
                        if (WingAccessPermissionUtils.isPermissionValid(perm, today)) {
                            validPerm = perm;
                            break;
                        } else {
                            hasExpired = true;
                        }
                    }

                    if (validPerm != null) {
                        showResult(true, "ACCESS GRANTED",
                                wing + "\n" + roll,
                                roll, wing, "Access granted");
                    } else if (hasExpired) {
                        showResult(false, "ACCESS DENIED",
                                "Your permission has expired",
                                roll, wing, "Expired");
                    } else {
                        showResult(false, "ACCESS DENIED",
                                "No permission found for this wing",
                                roll, wing, "No permission");
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Error checking access: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void showResult(boolean allowed, String title, String detail,
                             String roll, String wing, String reason) {
        binding.cvScannerResult.setVisibility(View.VISIBLE);

        int bgColor = allowed ? R.color.status_approved_bg : R.color.status_denied_bg;
        int textColor = allowed ? R.color.status_approved_text : R.color.status_denied_text;

        binding.cvScannerResult.setCardBackgroundColor(
                ContextCompat.getColor(this, bgColor));
        binding.tvScannerResultTitle.setText(title);
        binding.tvScannerResultTitle.setTextColor(ContextCompat.getColor(this, textColor));
        binding.tvScannerResultDetail.setText(detail);
        binding.tvScannerResultDetail.setTextColor(ContextCompat.getColor(this, textColor));

        logZoneAccess(roll, wing, allowed ? "ALLOWED" : "DENIED", reason);
    }

    private void logZoneAccess(String roll, String wing, String result, String reason) {
        ZoneAccessLog log = new ZoneAccessLog("", roll, wing, result, reason, Timestamp.now());
        db.collection("zone_access_logs").add(log);
    }
}
