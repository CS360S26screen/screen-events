package com.example.se_proj

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.se_proj.databinding.ActivityLoginBinding
import com.example.se_proj.rules.LoginInputUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Authenticates users and routes them to the correct dashboard based on Firestore role data.
 *
 * Design pattern: application-service style Activity that delegates credential normalization
 * to `LoginInputUtils` and keeps Firebase auth/profile lookup orchestration in one place.
 *
 * Outstanding issues: profile-link fallback (`email` query -> UID document copy) can race when
 * multiple devices sign in simultaneously and should be migrated to a server-side migration path.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            performLogin()
        }

        binding.tvRoleSelectHint.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun performLogin() {
        val inputId = binding.etUserId.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (!LoginInputUtils.hasCredentials(inputId, password)) {
            Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show()
            return
        }

        setLoginInProgress(true)
        val email = LoginInputUtils.normalizeEmail(inputId)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid
                if (uid != null) {
                    fetchUserRoleAndRedirect(uid)
                } else {
                    setLoginInProgress(false)
                    Log.e("Auth", "Authenticated user did not return a UID")
                    Toast.makeText(this, "Auth Failed: user record missing", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                setLoginInProgress(false)
                Log.e("Auth", "Login failed for $email", e)
                Toast.makeText(this, "Auth Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun fetchUserRoleAndRedirect(uid: String) {
        val userEmail = auth.currentUser?.email
        
        // 1. Try finding document by UID (Standard Firebase way that Rules expect)
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    processUserDocument(document.id, document.data)
                } else if (userEmail != null) {
                    // 2. Fallback: Search by email and "Link" the account so Rules work
                    linkProfileByEmail(userEmail, uid)
                } else {
                    handleUserNotFound(uid)
                }
            }
            .addOnFailureListener { e ->
                setLoginInProgress(false)
                Log.e("Auth", "Firestore fetch failed", e)
                Toast.makeText(this, "Database Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun linkProfileByEmail(email: String, uid: String) {
        db.collection("Users").whereEqualTo("email", email).get()
            .addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    val existingDoc = query.documents[0]
                    val userData = existingDoc.data?.toMutableMap() ?: mutableMapOf()
                    
                    // Ensure the Roll Number or Faculty ID is preserved in the new document
                    val role = userData["role"]?.toString()?.lowercase() ?: ""
                    if (role == "student" && !userData.containsKey("rollNumber")) {
                        userData["rollNumber"] = existingDoc.id
                    } else if ((role == "faculty" || role == "admin") && !userData.containsKey("facultyId")) {
                        userData["facultyId"] = existingDoc.id
                    }
                    
                    // Create the UID-based document that Security Rules expect
                    db.collection("Users").document(uid).set(userData)
                        .addOnSuccessListener {
                            Log.d("Auth", "Profile linked successfully for $uid with ID ${existingDoc.id}")
                            processUserDocument(uid, userData)
                        }
                        .addOnFailureListener { e ->
                            Log.e("Auth", "Failed to link profile", e)
                            processUserDocument(existingDoc.id, userData)
                        }
                } else {
                    handleUserNotFound(email)
                }
            }
            .addOnFailureListener { e ->
                setLoginInProgress(false)
                Toast.makeText(this, "Search failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun processUserDocument(docId: String, data: Map<String, Any>?) {
        val role = data?.get("role")?.toString()?.lowercase() ?: ""
        val name = data?.get("name")?.toString() ?: "User"
        
        Toast.makeText(this, "Welcome $name", Toast.LENGTH_SHORT).show()
        
        val intent = when (role) {
            "admin" -> Intent(this, AdminDashboardActivity::class.java)
            "guard" -> Intent(this, GuardDashboardActivity::class.java)
            "faculty" -> Intent(this, RequestSubmissionActivity::class.java)
            "student" -> Intent(this, StudentRequestActivity::class.java)
            else -> {
                setLoginInProgress(false)
                Log.e("Auth", "Role '$role' not recognized for user $docId")
                Toast.makeText(this, "Access Denied: Role '$role' not recognized", Toast.LENGTH_LONG).show()
                null
            }
        }
        
        intent?.let {
            startActivity(it)
            finish()
        }
    }

    private fun handleUserNotFound(identifier: String) {
        setLoginInProgress(false)
        Log.e("Auth", "User profile not found for: $identifier")
        Toast.makeText(this, "Profile not found in 'Users' collection. Ensure document exists.", Toast.LENGTH_LONG).show()
    }

    private fun setLoginInProgress(inProgress: Boolean) {
        binding.btnLogin.isEnabled = !inProgress
        binding.btnLogin.text = if (inProgress) "Authenticating..." else "Secure Login"
    }
}
