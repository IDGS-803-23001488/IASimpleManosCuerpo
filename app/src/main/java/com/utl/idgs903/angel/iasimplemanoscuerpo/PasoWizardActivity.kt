package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PasoWizardActivity : AppCompatActivity() {

    private lateinit var layoutStep1: View
    private lateinit var layoutStep2: View
    private lateinit var layoutStep3: View
    private lateinit var toolbarWizard: Toolbar

    private var selectedPoseName: String = ""
    private var selectedHand: ManoObjetivo = ManoObjetivo.ANY
    private var selectedFrames: Int = 15

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_step_wizard)

        layoutStep1 = findViewById(R.id.layoutStep1)
        layoutStep2 = findViewById(R.id.layoutStep2)
        layoutStep3 = findViewById(R.id.layoutStep3)
        toolbarWizard = findViewById(R.id.toolbarWizard)

        val initialPose = intent.getStringExtra("INITIAL_POSE")
        if (initialPose != null) {
            selectedPoseName = initialPose
            val handStr = intent.getStringExtra("INITIAL_HAND") ?: ManoObjetivo.ANY.name
            selectedHand = ManoObjetivo.valueOf(handStr)
            selectedFrames = intent.getIntExtra("INITIAL_FRAMES", 15)
        }

        setupStep1()
        setupStep2()
        setupStep3()

        if (initialPose != null) {
            showStep(3) // Jump to the final step to review/confirm, they can go back if they want
        } else {
            showStep(1)
        }
    }

    private fun showStep(step: Int) {
        layoutStep1.visibility = if (step == 1) View.VISIBLE else View.GONE
        layoutStep2.visibility = if (step == 2) View.VISIBLE else View.GONE
        layoutStep3.visibility = if (step == 3) View.VISIBLE else View.GONE

        when (step) {
            1 -> toolbarWizard.title = "Paso 1: Seleccionar Gesto"
            2 -> toolbarWizard.title = "Paso 2: Mano a usar"
            3 -> toolbarWizard.title = "Paso 3: Veracidad"
        }
    }

    private fun setupStep1() {
        val rvPoses: RecyclerView = findViewById(R.id.rvPoses)
        val allPoses = HandMetrics.HandPose.values().map { it.name.replace("_", " ") }

        rvPoses.layoutManager = GridLayoutManager(this, 2)
        rvPoses.adapter = object : RecyclerView.Adapter<PoseViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoseViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pose, parent, false)
                return PoseViewHolder(view)
            }

            override fun onBindViewHolder(holder: PoseViewHolder, position: Int) {
                holder.tvPoseName.text = allPoses[position]
                holder.itemView.setOnClickListener {
                    selectedPoseName = allPoses[position]
                    showStep(2)
                }
            }

            override fun getItemCount() = allPoses.size
        }
    }

    inner class PoseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPoseName: TextView = view.findViewById(R.id.tvPoseName)
    }

    private fun setupStep2() {
        findViewById<Button>(R.id.btnHandAny).setOnClickListener {
            selectedHand = ManoObjetivo.ANY
            showStep(3)
        }
        findViewById<Button>(R.id.btnHandLeft).setOnClickListener {
            selectedHand = ManoObjetivo.LEFT
            showStep(3)
        }
        findViewById<Button>(R.id.btnHandRight).setOnClickListener {
            selectedHand = ManoObjetivo.RIGHT
            showStep(3)
        }
    }

    private fun setupStep3() {
        val seekBarFrames: SeekBar = findViewById(R.id.seekBarFrames)
        val tvFramesValue: TextView = findViewById(R.id.tvFramesValue)
        val btnFinishWizard: Button = findViewById(R.id.btnFinishWizard)

        seekBarFrames.progress = selectedFrames - 5
        tvFramesValue.text = "$selectedFrames cuadros"

        seekBarFrames.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedFrames = progress + 5
                tvFramesValue.text = "$selectedFrames cuadros"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnFinishWizard.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra("POSE_NAME", selectedPoseName)
            resultIntent.putExtra("TARGET_HAND", selectedHand.name)
            resultIntent.putExtra("FRAMES", selectedFrames)
            
            val extraType = intent.getStringExtra("EXTRA_TYPE")
            if (extraType != null) {
                resultIntent.putExtra("EXTRA_TYPE", extraType)
            }
            
            val editIndex = intent.getIntExtra("EDIT_INDEX", -1)
            if (editIndex != -1) {
                resultIntent.putExtra("EDIT_INDEX", editIndex)
            }

            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onBackPressed() {
        if (layoutStep3.visibility == View.VISIBLE) {
            showStep(2)
        } else if (layoutStep2.visibility == View.VISIBLE) {
            showStep(1)
        } else {
            super.onBackPressed()
        }
    }
}
