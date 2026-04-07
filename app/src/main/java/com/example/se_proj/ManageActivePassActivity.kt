package com.example.se_proj

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.se_proj.databinding.ActivityManageActivePassBinding
import com.example.se_proj.models.VisitorRequest
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class ManageActivePassActivity : AppCompatActivity() {

    private lateinit var binding: ActivityManageActivePassBinding
    private val db = Firebase.firestore
    private var requestId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageActivePassBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestId = intent.getStringExtra("REQUEST_ID")
        
        if (requestId != null) {
            loadPassDetails(requestId!!)
        } else {
            // If no ID passed, try to find the current active one for this user
            findActivePass()
        }

        binding.btnCancelPass.setOnClickListener {
            showCancelConfirmation()
        }

        binding.bottomNavigation.selectedItemId = R.id.nav_my_guests
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_new_pass -> {
                    finish() // Go back to request screen
                    true
                }
                R.id.nav_profile -> {
                    // Navigate to profile
                    true
                }
                else -> true
            }
        }
    }

    private fun loadPassDetails(id: String) {
        db.collection("visitor_requests").document(id)
            .get()
            .addOnSuccessListener { doc ->
                val request = doc.toObject(VisitorRequest::class.java)
                if (request != null) {
                    displayDetails(request)
                }
            }
    }

    private fun findActivePass() {
        val studentRollNo = "27100xxx" // Get from context
        db.collection("visitor_requests")
            .whereEqualTo("hostId", studentRollNo)
            .whereEqualTo("status", "approved")
            .whereEqualTo("onCampus", true)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val doc = documents.documents[0]
                    requestId = doc.id
                    val request = doc.toObject(VisitorRequest::class.java)
                    request?.let { displayDetails(it) }
                } else {
                    Toast.makeText(this, "No active pass found", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun displayDetails(request: VisitorRequest) {
        binding.tvGuestName.text = request.guestName
        binding.tvCnic.text = request.guestCNIC
        binding.tvVisitWindow.text = "Today, ${request.startTime} - ${request.endTime}"
        
        val statusText = if (request.onCampus) "Status: Inside Campus" else "Status: Approved & Expected"
        binding.chipStatus.text = statusText
    }

    private fun showCancelConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Guest Pass")
            .setMessage("Are you sure you want to cancel this pass? This will immediately revoke access.")
            .setPositiveButton("Cancel Pass") { _, _ ->
                cancelPass()
            }
            .setNegativeButton("Keep Pass", null)
            .show()
    }

    private fun cancelPass() {
        requestId?.let { id ->
            db.collection("visitor_requests").document(id)
                .update("status", "cancelled")
                .addOnSuccessListener {
                    Toast.makeText(this, "Pass Cancelled Successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
