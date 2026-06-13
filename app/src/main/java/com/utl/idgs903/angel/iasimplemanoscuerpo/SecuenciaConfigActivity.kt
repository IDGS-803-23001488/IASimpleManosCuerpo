package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class SecuenciaConfigActivity : AppCompatActivity() {

    private lateinit var rvSteps: RecyclerView
    private lateinit var etComboName: EditText
    private lateinit var tvActivatorDesc: TextView
    private lateinit var btnEditActivator: Button
    private lateinit var tvDeactivadorDesc: TextView
    private lateinit var btnEditDeactivador: Button
    private lateinit var btnSave: Button
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var toolbar: Toolbar
    
    private lateinit var adapter: SecuenciaAdapter
    
    private var todosCombos: MutableList<Combo> = mutableListOf()
    private lateinit var comboActual: Combo
    private var comboIndex: Int = -1

    private val wizardLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val nombreGesto = data.getStringExtra("POSE_NAME") ?: return@registerForActivityResult
            val manoObjetivoStr = data.getStringExtra("TARGET_HAND") ?: ManoObjetivo.ANY.name
            val frames = data.getIntExtra("FRAMES", 15)
            val extraType = data.getStringExtra("EXTRA_TYPE")
            
            val manoObjetivo = ManoObjetivo.valueOf(manoObjetivoStr)
            val newStep = PasoSecuencia(nombreGesto, manoObjetivo, frames)

            when (extraType) {
                "ACTIVATOR" -> {
                    comboActual.activador = newStep
                    updateHeaders()
                }
                "DEACTIVATOR" -> {
                    comboActual.deactivador = newStep
                    updateHeaders()
                }
                "STEP" -> {
                    adapter.pasos.add(newStep)
                    adapter.notifyItemInserted(adapter.pasos.size - 1)
                }
                "EDIT_STEP" -> {
                    val editIndex = data.getIntExtra("EDIT_INDEX", -1)
                    if (editIndex != -1) {
                        adapter.pasos[editIndex] = newStep
                        adapter.notifyItemChanged(editIndex)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sequence_config)

        toolbar = findViewById(R.id.toolbar)
        rvSteps = findViewById(R.id.recyclerView)
        etComboName = findViewById(R.id.etComboName)
        tvActivatorDesc = findViewById(R.id.tvActivatorDesc)
        btnEditActivator = findViewById(R.id.btnEditActivator)
        tvDeactivadorDesc = findViewById(R.id.tvDeactivadorDesc)
        btnEditDeactivador = findViewById(R.id.btnEditDeactivador)
        btnSave = findViewById(R.id.btnSave)
        fabAdd = findViewById(R.id.fabAdd)

        if (!loadCurrentConfig()) {
            return
        }

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                adapter.removeItem(viewHolder.adapterPosition)
            }
        })

        adapter = SecuenciaAdapter(comboActual.pasos, { holder ->
            itemTouchHelper.startDrag(holder)
        }, { position ->
            val intent = Intent(this, PasoWizardActivity::class.java)
            intent.putExtra("EXTRA_TYPE", "EDIT_STEP")
            intent.putExtra("EDIT_INDEX", position)
            
            // Pass the existing values to pre-select them in the wizard
            val step = comboActual.pasos[position]
            intent.putExtra("INITIAL_POSE", step.nombreGesto)
            intent.putExtra("INITIAL_HAND", step.manoObjetivo.name)
            intent.putExtra("INITIAL_FRAMES", step.cuadrosRequeridos)
            
            wizardLauncher.launch(intent)
        })
        rvSteps.layoutManager = LinearLayoutManager(this)
        rvSteps.adapter = adapter
        itemTouchHelper.attachToRecyclerView(rvSteps)

        etComboName.setText(comboActual.name)
        etComboName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                comboActual.name = s?.toString()?.takeIf { it.isNotBlank() } ?: "Combo Sin Nombre"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        fabAdd.setOnClickListener {
            launchWizard("STEP")
        }

        btnEditActivator.setOnClickListener {
            if (comboActual.activador != null) {
                comboActual.activador = null
                updateHeaders()
            } else {
                launchWizard("ACTIVATOR", null)
            }
        }
        
        findViewById<View>(R.id.cardActivator).setOnClickListener {
            if (comboActual.activador != null) {
                launchWizard("ACTIVATOR", comboActual.activador)
            } else {
                launchWizard("ACTIVATOR", null)
            }
        }

        btnEditDeactivador.setOnClickListener {
            if (comboActual.deactivador != null) {
                comboActual.deactivador = null
                updateHeaders()
            } else {
                launchWizard("DEACTIVATOR", null)
            }
        }
        
        findViewById<View>(R.id.cardDeactivador).setOnClickListener {
            if (comboActual.deactivador != null) {
                launchWizard("DEACTIVATOR", comboActual.deactivador)
            } else {
                launchWizard("DEACTIVATOR", null)
            }
        }

        btnSave.setOnClickListener {
            SecuenciaConfigManager.saveCombos(this, todosCombos)
            Toast.makeText(this, "Combo guardado", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun launchWizard(extraType: String, step: PasoSecuencia? = null) {
        val intent = Intent(this, PasoWizardActivity::class.java)
        intent.putExtra("EXTRA_TYPE", extraType)
        if (step != null) {
            intent.putExtra("INITIAL_POSE", step.nombreGesto)
            intent.putExtra("INITIAL_HAND", step.manoObjetivo.name)
            intent.putExtra("INITIAL_FRAMES", step.cuadrosRequeridos)
        }
        wizardLauncher.launch(intent)
    }

    private fun loadCurrentConfig(): Boolean {
        val comboId = intent.getStringExtra("COMBO_ID")
        todosCombos = SecuenciaConfigManager.loadCombos(this).toMutableList()
        
        comboIndex = todosCombos.indexOfFirst { it.id == comboId }
        if (comboIndex == -1) {
            Toast.makeText(this, "Error cargando combo", Toast.LENGTH_SHORT).show()
            finish()
            return false
        }
        
        comboActual = todosCombos[comboIndex]
        updateHeaders()
        return true
    }

    private fun updateHeaders() {
        if (comboActual.activador != null) {
            tvActivatorDesc.text = "${comboActual.activador?.nombreGesto} (${comboActual.activador?.manoObjetivo})"
            tvActivatorDesc.setTextColor(android.graphics.Color.WHITE)
            btnEditActivator.text = "Quitar"
            btnEditActivator.setTextColor(android.graphics.Color.parseColor("#CF6679"))
        } else {
            tvActivatorDesc.text = "NINGUNO"
            tvActivatorDesc.setTextColor(android.graphics.Color.GRAY)
            btnEditActivator.text = "Añadir"
            btnEditActivator.setTextColor(android.graphics.Color.parseColor("#03DAC5"))
        }
        
        if (comboActual.deactivador != null) {
            tvDeactivadorDesc.text = "${comboActual.deactivador?.nombreGesto} (${comboActual.deactivador?.manoObjetivo})"
            tvDeactivadorDesc.setTextColor(android.graphics.Color.WHITE)
            btnEditDeactivador.text = "Quitar"
            btnEditDeactivador.setTextColor(android.graphics.Color.parseColor("#CF6679"))
        } else {
            tvDeactivadorDesc.text = "NINGUNO"
            tvDeactivadorDesc.setTextColor(android.graphics.Color.GRAY)
            btnEditDeactivador.text = "Añadir"
            btnEditDeactivador.setTextColor(android.graphics.Color.parseColor("#03DAC5"))
        }
    }
}
