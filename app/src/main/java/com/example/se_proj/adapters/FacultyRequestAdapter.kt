package com.example.se_proj.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.se_proj.databinding.ItemFacultyRequestBinding
import com.example.se_proj.models.VisitorRequest
import com.example.se_proj.rules.UiFormatUtils

class FacultyRequestAdapter(
    private var requests: List<VisitorRequest>,
    private val onEditClick: (VisitorRequest) -> Unit,
    private val onCancelClick: (VisitorRequest) -> Unit
) : RecyclerView.Adapter<FacultyRequestAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFacultyRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFacultyRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        holder.binding.tvGuestName.text = request.guestName
        holder.binding.tvVisitInfo.text = UiFormatUtils.formatFacultyVisitInfo(request)
        holder.binding.tvStatus.text = UiFormatUtils.formatRequestStatus(request.status)

        holder.binding.btnEdit.setOnClickListener { onEditClick(request) }
        holder.binding.btnCancel.setOnClickListener { onCancelClick(request) }
    }

    override fun getItemCount() = requests.size

    fun updateData(newRequests: List<VisitorRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}
