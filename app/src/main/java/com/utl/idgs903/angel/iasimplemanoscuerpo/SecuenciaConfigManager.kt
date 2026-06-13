package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

import java.util.UUID

data class Combo(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "Nuevo Combo",
    var activador: PasoSecuencia? = null,
    var pasos: MutableList<PasoSecuencia> = mutableListOf(),
    var deactivador: PasoSecuencia? = null
)

object SecuenciaConfigManager {
    private const val PREFS_NAME = "sequence_prefs_v2"
    private const val KEY_COMBOS = "combos_array"

    fun saveCombos(context: Context, combos: List<Combo>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val combosArray = JSONArray()
        for (combo in combos) {
            val comboObj = JSONObject()
            comboObj.put("id", combo.id)
            comboObj.put("name", combo.name)
            if (combo.activador != null) comboObj.put("activador", stepToJson(combo.activador!!))
            if (combo.deactivador != null) comboObj.put("deactivador", stepToJson(combo.deactivador!!))
            
            val pasosArray = JSONArray()
            for (step in combo.pasos) {
                pasosArray.put(stepToJson(step))
            }
            comboObj.put("pasos", pasosArray)

            combosArray.put(comboObj)
        }
        editor.putString(KEY_COMBOS, combosArray.toString())
        editor.apply()
    }

    fun loadCombos(context: Context): List<Combo> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val combosStr = prefs.getString(KEY_COMBOS, null)

        val defaultCombo = Combo(
            id = "default-combo",
            name = "Ataque Base",
            activador = PasoSecuencia("TE AMO ILY", ManoObjetivo.ANY, 15),
            pasos = mutableListOf(
                PasoSecuencia("L", ManoObjetivo.ANY, 10),
                PasoSecuencia("ROCK", ManoObjetivo.ANY, 10),
                PasoSecuencia("L", ManoObjetivo.ANY, 10)
            ),
            deactivador = PasoSecuencia("TE AMO ILY", ManoObjetivo.ANY, 15)
        )

        if (combosStr == null) {
            return listOf(defaultCombo)
        }

        try {
            val combosArray = JSONArray(combosStr)
            val combosList = mutableListOf<Combo>()
            for (i in 0 until combosArray.length()) {
                val comboObj = combosArray.getJSONObject(i)
                val combo = Combo(
                    id = comboObj.optString("id", UUID.randomUUID().toString()),
                    name = comboObj.optString("name", "Combo Desconocido")
                )
                if (comboObj.has("activador")) {
                    combo.activador = stepFromJson(comboObj.getJSONObject("activador"))
                }
                if (comboObj.has("deactivador")) {
                    combo.deactivador = stepFromJson(comboObj.getJSONObject("deactivador"))
                }
                if (comboObj.has("pasos")) {
                    val pasosArray = comboObj.getJSONArray("pasos")
                    for (j in 0 until pasosArray.length()) {
                        combo.pasos.add(stepFromJson(pasosArray.getJSONObject(j)))
                    }
                }
                combosList.add(combo)
            }
            return combosList
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf(defaultCombo)
        }
    }

    private fun stepToJson(step: PasoSecuencia): JSONObject {
        val obj = JSONObject()
        obj.put("nombreGesto", step.nombreGesto)
        obj.put("manoObjetivo", step.manoObjetivo.name)
        obj.put("cuadrosRequeridos", step.cuadrosRequeridos)
        return obj
    }

    private fun stepFromJson(obj: JSONObject): PasoSecuencia {
        return PasoSecuencia(
            nombreGesto = obj.getString("nombreGesto"),
            manoObjetivo = ManoObjetivo.valueOf(obj.getString("manoObjetivo")),
            cuadrosRequeridos = obj.getInt("cuadrosRequeridos")
        )
    }
}
