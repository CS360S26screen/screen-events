package com.example.se_proj.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.se_proj.databinding.ItemFacultyRequestBinding
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.RequestStatus
import com.example.se_proj.rules.UiFormatUtils

/**
 * Read-only adapter for the student dashboard "My Guests" history list.
 */
class StudentGuestLogAdapter(
    private var requests: List<VisitorRequest>
) : RecyclerView.Adapter<StudentGuestLogAdapter.ViewHolder>() {

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

        // History view only: hide action buttons.
        holder.binding.btnEdit.visibility = View.GONE
        holder.binding.btnCancel.visibility = View.GONE
    }

    override fun getItemCount(): Int = requests.size

    fun updateData(newRequests: List<VisitorRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}
