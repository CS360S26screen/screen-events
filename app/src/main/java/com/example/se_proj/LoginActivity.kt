package com.example.se_proj

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.se_proj.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

        if (inputId.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter credentials", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = "Authenticating..."

        val email = if (!inputId.contains("@")) "$inputId@campus.edu" else inputId

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid
                if (uid != null) {
                    fetchUserRoleAndRedirect(uid)
                }
            }
            .addOnFailureListener { e ->
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Secure Login"
                Log.e("Auth", "Login failed for $email", e)
                Toast.makeText(this, "Auth Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun fetchUserRoleAndRedirect(uid: String) {
        // Updated to use "Users" with a capital 'U' as per your Firestore setup
        db.collection("Users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role")?.lowercase()
                    val name = document.getString("name")
                    Toast.makeText(this, "Welcome $name", Toast.LENGTH_SHORT).show()
                    
                    val intent = when (role) {
                        "admin" -> Intent(this, AdminDashboardActivity::class.java)
                        "guard" -> Intent(this, GuardDashboardActivity::class.java)
                        "faculty" -> Intent(this, RequestSubmissionActivity::class.java)
                        "student" -> Intent(this, StudentRequestActivity::class.java)
                        else -> {
                            Log.e("Auth", "Role '$role' not recognized")
                            Toast.makeText(this, "Access Denied: Role '$role' not recognized", Toast.LENGTH_LONG).show()
                            null
                        }
                    }
                    
                    intent?.let {
                        startActivity(it)
                        finish()
                    }
                } else {
                    binding.btnLogin.isEnabled = true
                    binding.btnLogin.text = "Secure Login"
                    Log.e("Auth", "Document not found in 'Users' collection for UID: $uid")
                    Toast.makeText(this, "User details not found (Check 'Users' collection)", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = "Secure Login"
                Log.e("Auth", "Firestore fetch failed", e)
                Toast.makeText(this, "Database Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
