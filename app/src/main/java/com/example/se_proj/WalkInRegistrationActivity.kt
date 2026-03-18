package com.example.se_proj

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.se_proj.databinding.ActivityWalkInRegistrationBinding
import com.example.se_proj.models.VisitorRequest
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalkInRegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalkInRegistrationBinding
    private val db = Firebase.firestore
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalkInRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSubmitWalkIn.setOnClickListener { submitWalkInRequest() }
    }

    private fun submitWalkInRequest() {
        val hostId = binding.etHostId.text.toString().trim()
        val name = binding.etGuestName.text.toString().trim()
        val cnic = binding.etCnic.text.toString().trim()
        val purpose = binding.etPurpose.text.toString().trim()

        if (hostId.isEmpty() || name.isEmpty() || cnic.isEmpty() || purpose.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        val request = VisitorRequest(
            guestName = name,
            guestCNIC = cnic,
            purpose = purpose,
            visitDate = currentDate,
            startTime = currentTime,
            endTime = "23:59", // Walk-in typically valid for the day
            hostId = hostId,
            hostType = "faculty",
            status = "pending_adhoc",
            onCampus = false
        )

        binding.btnSubmitWalkIn.isEnabled = false
        
        db.collection("visitor_requests")
            .add(request)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(this, "Request sent to Faculty for approval", Toast.LENGTH_SHORT).show()
                listenForApproval(documentReference.id)
            }
            .addOnFailureListener { e ->
                binding.btnSubmitWalkIn.isEnabled = true
                Toast.makeText(this, "Failed to send request: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenForApproval(requestId: String) {
        binding.tvApprovalStatus.visibility = View.VISIBLE
        binding.tvApprovalStatus.text = "Waiting for Faculty Approval..."

        listenerRegistration = db.collection("visitor_requests").document(requestId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("FirestoreListener", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val status = snapshot.getString("status")
                    if (status == "approved") {
                        binding.tvApprovalStatus.text = "APPROVED! You can now check them in."
                        binding.tvApprovalStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, theme))
                        Toast.makeText(this, "Request Approved!", Toast.LENGTH_LONG).show()
                        // Optionally auto-close or enable a check-in button here
                    } else if (status == "denied") {
                        binding.tvApprovalStatus.text = "DENIED by Faculty."
                        binding.tvApprovalStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                        binding.btnSubmitWalkIn.isEnabled = true
                    }
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listenerRegistration?.remove()
    }
}
