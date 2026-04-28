package com.example.se_proj;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.se_proj.databinding.ActivityLoginBinding;
import com.example.se_proj.rules.LoginInputUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Authenticates users and routes them to the correct dashboard based on Firestore role data.
 *
 * <p>Design pattern: application-service style Activity that delegates credential normalization
 * to {@link LoginInputUtils} and keeps Firebase auth/profile lookup orchestration in one place.</p>
 *
 * <p>Outstanding issues: profile-link fallback (email query → UID document copy) can race when
 * multiple devices sign in simultaneously and should be migrated to a server-side migration path.</p>
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> performLogin());

        binding.tvRoleSelectHint.setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
    }

    private void performLogin() {
        String inputId = binding.etUserId.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        if (!LoginInputUtils.hasCredentials(inputId, password)) {
            Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoginInProgress(true);
        String email = LoginInputUtils.normalizeEmail(inputId);

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser() != null ? authResult.getUser().getUid() : null;
                    if (uid != null) {
                        fetchUserRoleAndRedirect(uid);
                    } else {
                        setLoginInProgress(false);
                        Log.e("Auth", "Authenticated user did not return a UID");
                        Toast.makeText(this, "Auth Failed: user record missing", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoginInProgress(false);
                    Log.e("Auth", "Login failed for " + email, e);
                    Toast.makeText(this, "Auth Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void fetchUserRoleAndRedirect(String uid) {
        String userEmail = auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : null;

        // 1. Try finding document by UID (Standard Firebase way that Rules expect)
        db.collection("Users").document(uid).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        processUserDocument(document.getId(), document.getData());
                    } else if (userEmail != null) {
                        // 2. Fallback: Search by email and "Link" the account so Rules work
                        linkProfileByEmail(userEmail, uid);
                    } else {
                        handleUserNotFound(uid);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoginInProgress(false);
                    Log.e("Auth", "Firestore fetch failed", e);
                    Toast.makeText(this, "Database Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void linkProfileByEmail(String email, String uid) {
        db.collection("Users").whereEqualTo("email", email).get()
                .addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot existingDoc = query.getDocuments().get(0);
                        Map<String, Object> userData = existingDoc.getData() != null
                                ? new HashMap<>(existingDoc.getData())
                                : new HashMap<>();

                        // Ensure the Roll Number or Faculty ID is preserved in the new document
                        String role = userData.containsKey("role")
                                ? userData.get("role").toString().toLowerCase()
                                : "";
                        if ("student".equals(role) && !userData.containsKey("rollNumber")) {
                            userData.put("rollNumber", existingDoc.getId());
                        } else if (("faculty".equals(role) || "admin".equals(role))
                                && !userData.containsKey("facultyId")) {
                            userData.put("facultyId", existingDoc.getId());
                        }

                        // Create the UID-based document that Security Rules expect
                        final Map<String, Object> finalUserData = userData;
                        final String existingDocId = existingDoc.getId();
                        db.collection("Users").document(uid).set(userData)
                                .addOnSuccessListener(unused -> {
                                    Log.d("Auth", "Profile linked successfully for " + uid
                                            + " with ID " + existingDocId);
                                    processUserDocument(uid, finalUserData);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e("Auth", "Failed to link profile", e);
                                    processUserDocument(existingDocId, finalUserData);
                                });
                    } else {
                        handleUserNotFound(email);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoginInProgress(false);
                    Toast.makeText(this, "Search failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void processUserDocument(String docId, Map<String, Object> data) {
        String role = (data != null && data.get("role") != null)
                ? data.get("role").toString().toLowerCase()
                : "";
        String name = (data != null && data.get("name") != null)
                ? data.get("name").toString()
                : "User";

        Toast.makeText(this, "Welcome " + name, Toast.LENGTH_SHORT).show();

        Intent intent;
        if ("admin".equals(role)) {
            intent = new Intent(this, AdminDashboardActivity.class);
        } else if ("guard".equals(role)) {
            intent = new Intent(this, GuardDashboardActivity.class);
        } else if ("faculty".equals(role)) {
            intent = new Intent(this, RequestSubmissionActivity.class);
        } else if ("student".equals(role)) {
            intent = new Intent(this, StudentRequestActivity.class);
        } else {
            setLoginInProgress(false);
            Log.e("Auth", "Role '" + role + "' not recognized for user " + docId);
            Toast.makeText(this, "Access Denied: Role '" + role + "' not recognized",
                    Toast.LENGTH_LONG).show();
            return;
        }

        startActivity(intent);
        finish();
    }

    private void handleUserNotFound(String identifier) {
        setLoginInProgress(false);
        Log.e("Auth", "User profile not found for: " + identifier);
        Toast.makeText(this,
                "Profile not found in 'Users' collection. Ensure document exists.",
                Toast.LENGTH_LONG).show();
    }

    private void setLoginInProgress(boolean inProgress) {
        binding.btnLogin.setEnabled(!inProgress);
        binding.btnLogin.setText(inProgress ? "Authenticating..." : "Secure Login");
    }
}
