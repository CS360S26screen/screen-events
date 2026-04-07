package com.example.se_proj.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.se_proj.databinding.ItemFacultyRequestBinding
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.RequestStatus
import com.example.se_proj.rules.UiFormatUtils

/**
 * RecyclerView adapter for faculty-owned requests in the "My Requests" screen.
 *
 * Design note: Adapter/ViewHolder pattern with intent-revealing callbacks for edit and cancel
 * operations, keeping business decisions outside the adapter.
 *
 * Outstanding issues: row updates are not diffed and all item buttons remain active during
 * in-flight writes, which can trigger duplicate taps.
 */
class FacultyRequestAdapter(
    private var requests: List<VisitorRequest>,
    private val onEditClick: (VisitorRequest) -> Unit,
    private val onCancelClick: (VisitorRequest) -> Unit
) : RecyclerView.Adapter<FacultyRequestAdapter.ViewHolder>() {

    /** ViewHolder wrapping view-binding references for a single faculty request row. */
    class ViewHolder(val binding: ItemFacultyRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFacultyRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        holder.binding.tvGuestName.text = request.guestName
        holder.binding.tvVisitInfo.text = UiFormatUtils.formatFacultyVisitInfo(request)
        holder.binding.chipStatus.text = RequestStatus.normalize(request.status).uppercase()

        holder.binding.btnEdit.setOnClickListener { onEditClick(request) }
        holder.binding.btnCancel.setOnClickListener { onCancelClick(request) }
    }

    override fun getItemCount() = requests.size

    /** Replaces the adapter data with a new snapshot from Firestore. */
    fun updateData(newRequests: List<VisitorRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}
