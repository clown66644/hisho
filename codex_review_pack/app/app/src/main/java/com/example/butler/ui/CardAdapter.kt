package com.example.butler.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.butler.R

class CardAdapter(
    private val onApproveClicked: (String) -> Unit,
    private val onRejectClicked: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<CardItem> = emptyList()

    companion object {
        private const val TYPE_TODO = 1
        private const val TYPE_CONFIRMATION = 2
    }

    fun submitList(newList: List<CardItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is CardItem.TodoCardItem -> TYPE_TODO
            is CardItem.ConfirmationCardItem -> TYPE_CONFIRMATION
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_TODO) {
            val view = inflater.inflate(R.layout.item_todo_card, parent, false)
            TodoViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_confirmation_card, parent, false)
            ConfirmationViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is CardItem.TodoCardItem -> (holder as TodoViewHolder).bind(item)
            is CardItem.ConfirmationCardItem -> (holder as ConfirmationViewHolder).bind(item, onApproveClicked, onRejectClicked)
        }
    }

    override fun getItemCount(): Int = items.size

    class TodoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRankTag: TextView = itemView.findViewById(R.id.tvRankTag)
        private val tvTodoTitle: TextView = itemView.findViewById(R.id.tvTodoTitle)
        private val tvTodoDetail: TextView = itemView.findViewById(R.id.tvTodoDetail)

        fun bind(item: CardItem.TodoCardItem) {
            tvRankTag.text = item.rankTag
            tvTodoTitle.text = item.title
            tvTodoDetail.text = item.detail ?: ""
            tvTodoDetail.visibility = if (item.detail.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    class ConfirmationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvConfirmTitle: TextView = itemView.findViewById(R.id.tvConfirmTitle)
        private val tvConfirmDescription: TextView = itemView.findViewById(R.id.tvConfirmDescription)
        private val btnApprove: Button = itemView.findViewById(R.id.btnApprove)
        private val btnReject: Button = itemView.findViewById(R.id.btnReject)

        fun bind(
            item: CardItem.ConfirmationCardItem,
            onApproveClicked: (String) -> Unit,
            onRejectClicked: (String) -> Unit
        ) {
            tvConfirmTitle.text = item.title
            tvConfirmDescription.text = item.description
            btnApprove.setOnClickListener { onApproveClicked(item.id) }
            btnReject.setOnClickListener { onRejectClicked(item.id) }
        }
    }
}
