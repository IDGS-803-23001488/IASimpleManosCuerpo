package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ComboAdapter(
    var combos: MutableList<Combo>,
    private val onEditClick: (Combo) -> Unit
) : RecyclerView.Adapter<ComboAdapter.ComboViewHolder>() {

    inner class ComboViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvComboName: TextView = itemView.findViewById(R.id.tvComboName)
        val tvComboDetails: TextView = itemView.findViewById(R.id.tvComboDetails)
        val ivEdit: ImageView = itemView.findViewById(R.id.ivEdit)

        init {
            itemView.setOnClickListener {
                onEditClick(combos[adapterPosition])
            }
            ivEdit.setOnClickListener {
                onEditClick(combos[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComboViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_combo, parent, false)
        return ComboViewHolder(view)
    }

    override fun onBindViewHolder(holder: ComboViewHolder, position: Int) {
        val combo = combos[position]
        holder.tvComboName.text = combo.name
        
        val actText = combo.activador?.nombreGesto ?: "Ninguno"
        val deactText = combo.deactivador?.nombreGesto ?: "Ninguno"
        val pasosCount = combo.pasos.size
        
        holder.tvComboDetails.text = "Act: $actText | Pasos: $pasosCount | Des: $deactText"
    }

    override fun getItemCount(): Int = combos.size
    
    fun removeItem(position: Int) {
        combos.removeAt(position)
        notifyItemRemoved(position)
    }
}
