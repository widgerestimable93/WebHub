package com.widgerestimable.webhub.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.widgerestimable.webhub.R
import com.widgerestimable.webhub.data.WebAppEntry

enum class WebAppListMode { HUB, MANAGER }

class WebAppAdapter(
    private val mode: WebAppListMode,
    private val onOpen: (WebAppEntry) -> Unit,
    private val onEdit: (WebAppEntry) -> Unit,
    private val onDelete: (WebAppEntry) -> Unit,
    private val onToggleFavorite: (WebAppEntry) -> Unit,
    private val onLogout: (WebAppEntry) -> Unit,
    private val onClearData: (WebAppEntry) -> Unit,
    private val onToggleActive: ((WebAppEntry) -> Unit)? = null
) : RecyclerView.Adapter<WebAppAdapter.VH>() {

    private val items = mutableListOf<WebAppEntry>()

    fun submit(list: List<WebAppEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_webapp, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val blob = itemView.findViewById<android.widget.TextView>(R.id.blob_icon)
        private val name = itemView.findViewById<android.widget.TextView>(R.id.text_name)
        private val description = itemView.findViewById<android.widget.TextView>(R.id.text_description)
        private val status = itemView.findViewById<android.widget.TextView>(R.id.text_status)
        private val btnOpen = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_open)
        private val btnFavorite = itemView.findViewById<android.widget.ImageButton>(R.id.btn_favorite)
        private val btnMore = itemView.findViewById<android.widget.ImageButton>(R.id.btn_more)

        fun bind(entry: WebAppEntry) {
            blob.text = entry.iconEmoji.ifBlank { entry.name.take(1).uppercase() }
            try {
                blob.background.mutate()
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 14f * itemView.resources.displayMetrics.density
                    setColor(Color.parseColor(entry.color.ifBlank { "#14315C" }))
                }.also { blob.background = it }
            } catch (e: Exception) { /* couleur invalide -> conserve le fond par défaut */ }

            name.text = entry.name
            description.text = entry.description

            if (mode == WebAppListMode.MANAGER) {
                status.visibility = View.VISIBLE
                status.text = itemView.context.getString(
                    if (entry.active) R.string.status_active else R.string.status_inactive
                )
            } else {
                status.visibility = View.GONE
            }

            btnFavorite.setImageResource(
                if (entry.favorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            btnFavorite.imageTintList = ColorStateList.valueOf(
                if (entry.favorite) Color.parseColor("#F5B700")
                else itemView.context.getColor(R.color.text_muted_light)
            )

            itemView.setOnClickListener { onOpen(entry) }
            btnOpen.setOnClickListener { onOpen(entry) }
            btnFavorite.setOnClickListener { onToggleFavorite(entry) }
            btnMore.setOnClickListener { anchor ->
                val popup = PopupMenu(anchor.context, anchor)
                popup.menu.add(0, 1, 0, R.string.action_edit)
                popup.menu.add(0, 2, 1, R.string.action_logout)
                popup.menu.add(0, 3, 2, R.string.action_clear_data)
                popup.menu.add(0, 4, 3, R.string.action_delete)
                if (mode == WebAppListMode.MANAGER) {
                    popup.menu.add(
                        0, 5, 4,
                        if (entry.active) R.string.status_inactive else R.string.status_active
                    )
                }
                popup.setOnMenuItemClickListener {
                    when (it.itemId) {
                        1 -> onEdit(entry)
                        2 -> onLogout(entry)
                        3 -> onClearData(entry)
                        4 -> onDelete(entry)
                        5 -> onToggleActive?.invoke(entry)
                    }
                    true
                }
                popup.show()
            }
        }
    }
}

