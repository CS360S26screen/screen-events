package com.example.se_proj

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.se_proj.databinding.ActivityRequestSubmissionBinding
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.AuditLogUtils
import com.example.se_proj.rules.RequestStatus
import com.example.se_proj.rules.RequestValidationUtils
import com.example.se_proj.rules.UserProfileUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.time.LocalDateTime
import java.util.Calendar
import java.util.Locale

/**
 * Faculty request-creation screen for scheduled visitors plus real-time handling of walk-in
 * approvals and near-end-time host reminders.
 *
 * Design note: orchestrates UI events and Firestore listeners while delegating validation,
 * status semantics, and reminder/overstay timing logic to rule utilities.
 *
 * Outstanding issues: reminder deduplication is in-memory only (`shownReminderRequestIds`) and
 * resets on process death, so reminders can reappear after app restart.
 */
class RequestSubmissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestSubmissionBinding
    private val db = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()
    private var adHocListener: ListenerRegistration? = null
    private var overstayListener: ListenerRegistration? = null
    private val shownReminderRequestIds = mutableSetOf<String>()

    private var currentUserId: String = ""
    private var currentUserName: String = ""

    private var selectedDate: String = ""
    private var selectedStartTime: String = ""
    private var selectedEndTime: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestSubmissionBinding.inflate(layoutInflater)
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

        binding.btnSubmit.isEnabled = false
        loadUserProfile()

        binding.etVisitDate.setOnClickListener { showDatePicker() }
        binding.etStartTime.setOnClickListener { showTimePicker { time ->
            selectedStartTime = time
            binding.etStartTime.setText(time)
        }}
        binding.etEndTime.setOnClickListener { showTimePicker { time ->
            selectedEndTime = time
            binding.etEndTime.setText(time)
        }}

        binding.btnSubmit.setOnClickListener { submitRequest() }

        binding.btnViewRequests.setOnClickListener {
            startActivity(Intent(this, FacultyRequestsActivity::class.java))
        }
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in again to submit a request", Toast.LENGTH_LONG).show()
            return
        }

        db.collection("Users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    setupProfileData(doc.id, doc.data)
                } else {
                    val email = auth.currentUser?.email
                    if (email != null) {
                        db.collection("Users").whereEqualTo("email", email).get()
                            .addOnSuccessListener { query ->
                                if (!query.isEmpty) {
                                    val d = query.documents[0]
                                    setupProfileData(d.id, d.data)
                                } else {
                                    Toast.makeText(this, "User profile not found", Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e("RequestSubmission", "Failed to load user profile", error)
                Toast.makeText(this, "Unable to load your profile", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupProfileData(docId: String, data: Map<String, Any>?) {
        currentUserId = UserProfileUtils.resolveHostId(
            data?.get("rollNumber")?.toString(),
            data?.get("facultyId")?.toString(),
            docId
        )
        currentUserName = data?.get("name")?.toString() ?: ""
        binding.btnSubmit.isEnabled = currentUserId.isNotEmpty()
        if (binding.btnSubmit.isEnabled) {
            startAdHocListener()
            startOverstayListener()
        }
    }

    private fun startAdHocListener() {
        if (currentUserId.isEmpty()) return
        adHocListener = db.collection("visitor_requests")
            .whereEqualTo("hostId", currentUserId)
            .whereEqualTo("status", RequestStatus.PENDING_ADHOC)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                for (doc in snapshots?.documentChanges.orEmpty()) {
                    if (doc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val request = doc.document.toObject(VisitorRequest::class.java)
                        showAdHocDialog(doc.document.id, request)
                    }
                }
            }
    }

    private fun startOverstayListener() {
        if (currentUserId.isEmpty()) return

        overstayListener = db.collection("visitor_requests")
            .whereEqualTo("hostId", currentUserId)
            .whereEqualTo("onCampus", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener

                val now = LocalDateTime.now()
                for (doc in snapshots?.documents.orEmpty()) {
                    val request = doc.toObject(VisitorRequest::class.java) ?: continue
                    val requestKey = request.requestId.ifEmpty { doc.id }
                    if (AuditLogUtils.shouldSendExitReminder(request, now, 15)) {
                        if (shownReminderRequestIds.add(requestKey)) {
                            showOverstayAlert(request.guestName, request.endTime)
                        }
                    } else {
                        shownReminderRequestIds.remove(requestKey)
                    }
                }
            }
    }

    private fun showOverstayAlert(guestName: String, endTime: String) {
        AlertDialog.Builder(this)
            .setTitle("Upcoming Exit Reminder")
            .setMessage("Your guest $guestName is scheduled to leave at $endTime (within 15 minutes).")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAdHocDialog(requestId: String, request: VisitorRequest) {
        AlertDialog.Builder(this)
            .setTitle("Walk-in Approval Request")
            .setMessage("Guest ${request.guestName} is at the gate for: ${request.purpose}. Approve entry?")
            .setPositiveButton("Approve") { _, _ ->
                updateRequestStatus(requestId, RequestStatus.APPROVED)
            }
            .setNegativeButton("Deny") { _, _ ->
                updateRequestStatus(requestId, RequestStatus.DENIED)
            }
            .setCancelable(false)
            .show()
    }

    private fun updateRequestStatus(requestId: String, newStatus: String) {
        db.collection("visitor_requests").document(requestId)
            .update("status", newStatus)
            .addOnSuccessListener {
                Toast.makeText(this, "Request $newStatus", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            selectedDate = String.format(Locale.getDefault(), "%02d/%02d/%d", dayOfMonth, month + 1, year)
            binding.etVisitDate.setText(selectedDate)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hourOfDay, minute ->
            val time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            onTimeSelected(time)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun submitRequest() {
        val name = binding.etGuestName.text.toString().trim()
        val cnic = RequestValidationUtils.normalizeCnic(binding.etCnic.text.toString())
        val purpose = binding.etPurpose.text.toString().trim()

        if (currentUserId.isEmpty()) {
            Toast.makeText(this, "Your profile is still loading", Toast.LENGTH_SHORT).show()
            return
        }

        val validation = RequestValidationUtils.validateScheduledRequest(
            name,
            cnic,
            purpose,
            selectedDate,
            selectedStartTime,
            selectedEndTime
        )
        if (!validation.isValid) {
            Toast.makeText(this, validation.message, Toast.LENGTH_SHORT).show()
            return
        }

        val request = VisitorRequest(
            guestName = name,
            guestCNIC = cnic,
            purpose = purpose,
            visitDate = selectedDate,
            startTime = selectedStartTime,
            endTime = selectedEndTime,
            hostId = currentUserId,
            creatorId = auth.currentUser?.uid ?: "",
            hostType = "faculty",
            status = RequestStatus.PENDING
        )

        binding.btnSubmit.isEnabled = false
        val uid = auth.currentUser?.uid ?: ""

        db.collection("visitor_requests")
            .whereEqualTo("creatorId", uid)
            .whereEqualTo("guestCNIC", request.guestCNIC)
            .whereEqualTo("visitDate", request.visitDate)
            .get()
            .addOnSuccessListener { documents ->
                val hasDuplicate = documents.toObjects(VisitorRequest::class.java).any {
                    RequestValidationUtils.matchesDuplicateCandidate(
                        it,
                        request.hostId,
                        request.guestCNIC,
                        request.visitDate
                    )
                }

                if (hasDuplicate) {
                    binding.btnSubmit.isEnabled = true
                    Toast.makeText(this, "A similar request already exists for this guest and date", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                saveVisitorRequest(request)
            }
            .addOnFailureListener { e ->
                binding.btnSubmit.isEnabled = true
                Log.e("FirestoreError", "Error checking duplicates", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveVisitorRequest(request: VisitorRequest) {
        db.collection("visitor_requests")
            .add(request)
            .addOnSuccessListener {
                Toast.makeText(this, "Request Submitted Successfully", Toast.LENGTH_SHORT).show()
                clearForm()
            }
            .addOnFailureListener { e ->
                binding.btnSubmit.isEnabled = true
                Log.e("FirestoreError", "Error adding document", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun clearForm() {
        binding.etGuestName.text?.clear()
        binding.etCnic.text?.clear()
        binding.etPurpose.text?.clear()
        binding.etVisitDate.text?.clear()
        binding.etStartTime.text?.clear()
        binding.etEndTime.text?.clear()
        selectedDate = ""
        selectedStartTime = ""
        selectedEndTime = ""
        binding.btnSubmit.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        adHocListener?.remove()
        overstayListener?.remove()
    }
}
