package com.example.se_proj.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.se_proj.R
import com.example.se_proj.databinding.ItemVisitorRequestBinding
import com.example.se_proj.models.VisitorRequest

class VisitorRequestAdapter(
    private var requests: List<VisitorRequest>,
    private val onApproveClick: (VisitorRequest) -> Unit,
    private val onRejectClick: (VisitorRequest) -> Unit
) : RecyclerView.Adapter<VisitorRequestAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemVisitorRequestBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemVisitorRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        val context = holder.binding.root.context

        holder.binding.tvGuestName.text = request.guestName
        holder.binding.tvHostInfo.text = request.purpose
        holder.binding.tvTimeWindow.text = "${request.visitDate} | ${request.startTime} - ${request.endTime}"
        holder.binding.chipStatus.text = request.status.uppercase()

        val (bgColorRes, textColorRes) = when (request.status.lowercase()) {
            "approved" -> R.color.status_approved_bg to R.color.status_approved_text
            "rejected", "denied", "cancelled" -> R.color.status_denied_bg to R.color.status_denied_text
            else -> R.color.status_pending_bg to R.color.status_pending_text
        }

        holder.binding.chipStatus.chipBackgroundColor =
            ColorStateList.valueOf(ContextCompat.getColor(context, bgColorRes))
        holder.binding.chipStatus.setTextColor(ContextCompat.getColor(context, textColorRes))

        holder.binding.btnApprove.setOnClickListener { onApproveClick(request) }
        holder.binding.btnReject.setOnClickListener { onRejectClick(request) }
    }

    override fun getItemCount() = requests.size

    fun updateData(newRequests: List<VisitorRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }
}
