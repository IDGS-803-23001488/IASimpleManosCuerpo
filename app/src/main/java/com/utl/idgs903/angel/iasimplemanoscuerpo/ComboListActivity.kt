package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.UUID

class ComboListActivity : AppCompatActivity() {

    private lateinit var rvCombos: RecyclerView
    private lateinit var fabAddCombo: FloatingActionButton
    private lateinit var adapter: ComboAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_combo_list)

        rvCombos = findViewById(R.id.recyclerView)
        fabAddCombo = findViewById(R.id.fabAddCombo)

        adapter = ComboAdapter(mutableListOf()) { combo ->
            val intent = Intent(this, SecuenciaConfigActivity::class.java)
            intent.putExtra("COMBO_ID", combo.id)
            startActivity(intent)
        }

        rvCombos.layoutManager = LinearLayoutManager(this)
        rvCombos.adapter = adapter

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                adapter.removeItem(viewHolder.adapterPosition)
                SecuenciaConfigManager.saveCombos(this@ComboListActivity, adapter.combos)
            }
        })
        itemTouchHelper.attachToRecyclerView(rvCombos)

        fabAddCombo.setOnClickListener {
            val newCombo = Combo(id = UUID.randomUUID().toString(), name = "Nuevo Combo")
            adapter.combos.add(newCombo)
            SecuenciaConfigManager.saveCombos(this, adapter.combos)
            
            val intent = Intent(this, SecuenciaConfigActivity::class.java)
            intent.putExtra("COMBO_ID", newCombo.id)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.combos = SecuenciaConfigManager.loadCombos(this).toMutableList()
        adapter.notifyDataSetChanged()
    }
}
