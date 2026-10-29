package fr.perso.randomclicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var etMin: EditText
    private lateinit var etMax: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        etMin = findViewById(R.id.etMin)
        etMax = findViewById(R.id.etMax)

        val prefs = getSharedPreferences(ClickService.PREFS, MODE_PRIVATE)
        etMin.setText(prefs.getInt("min", 10).toString())
        etMax.setText(prefs.getInt("max", 50).toString())

        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            if (save()) Toast.makeText(this, "Enregistré", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnPanel).setOnClickListener {
            val s = ClickService.instance
            if (s == null) {
                Toast.makeText(this, "Active d'abord le service d'accessibilité", Toast.LENGTH_LONG).show()
            } else {
                s.togglePanel()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tvStatus.text = if (ClickService.instance != null)
            "✅ Service actif"
        else
            "❌ Service inactif : Paramètres > Accessibilité > Applications installées > Random Clicker"
    }

    override fun onPause() {
        super.onPause()
        save()
    }

    private fun save(): Boolean {
        val min = etMin.text.toString().toIntOrNull()
        val max = etMax.text.toString().toIntOrNull()
        if (min == null || max == null || min < 1 || max < min) {
            Toast.makeText(this, "Valeurs invalides (min ≥ 1 et max ≥ min)", Toast.LENGTH_LONG).show()
            return false
        }
        getSharedPreferences(ClickService.PREFS, MODE_PRIVATE).edit()
            .putInt("min", min).putInt("max", max).apply()
        return true
    }
}
