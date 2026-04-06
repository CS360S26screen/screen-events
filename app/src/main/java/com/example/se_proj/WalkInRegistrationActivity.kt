package com.example.se_proj

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.se_proj.databinding.ActivityWalkInRegistrationBinding
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.RequestStatus
import com.example.se_proj.rules.RequestValidationUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalkInRegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalkInRegistrationBinding
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var listenerRegistration: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWalkInRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }

        binding.btnSubmitWalkIn.setOnClickListener { submitWalkInRequest() }
    }

    private fun submitWalkInRequest() {
        val hostId = binding.etHostId.text.toString().trim()
        val name = binding.etGuestName.text.toString().trim()
        val cnic = RequestValidationUtils.normalizeCnic(binding.etCnic.text.toString())
        val purpose = binding.etPurpose.text.toString().trim()

        val validation = RequestValidationUtils.validateWalkInRequest(hostId, name, cnic, purpose)
        if (!validation.isValid) {
            Toast.makeText(this, validation.message, Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in again", Toast.LENGTH_LONG).show()
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
            endTime = "23:59",
            hostId = hostId,
            creatorId = uid,
            hostType = "faculty",
            status = RequestStatus.PENDING_ADHOC,
            onCampus = false
        )

        binding.btnSubmitWalkIn.isEnabled = false

        db.collection("visitor_requests")
            .whereEqualTo("creatorId", uid)
            .whereEqualTo("guestCNIC", cnic)
            .whereEqualTo("visitDate", currentDate)
            .get()
            .addOnSuccessListener { documents ->
                val hasDuplicate = documents.toObjects(VisitorRequest::class.java).any {
                    RequestValidationUtils.matchesDuplicateCandidate(it, hostId, cnic, currentDate)
                }
                if (hasDuplicate) {
                    binding.btnSubmitWalkIn.isEnabled = true
                    Toast.makeText(this, "A walk-in request already exists for this guest today", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                db.collection("visitor_requests")
                    .add(request)
                    .addOnSuccessListener { documentReference ->
                        Toast.makeText(this, "Request sent to Faculty for approval", Toast.LENGTH_SHORT).show()
                        listenForApproval(documentReference.id)
                    }
                    .addOnFailureListener { e ->
                        binding.btnSubmitWalkIn.isEnabled = true
                        Log.e("WalkIn", "Failed to send request", e)
                        Toast.makeText(this, "Failed to send request: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.btnSubmitWalkIn.isEnabled = true
                Log.e("WalkIn", "Failed to validate request", e)
                Toast.makeText(this, "Failed to validate request: ${e.message}", Toast.LENGTH_SHORT).show()
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
