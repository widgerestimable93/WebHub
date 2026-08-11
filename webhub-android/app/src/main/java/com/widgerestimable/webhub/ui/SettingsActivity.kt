package com.widgerestimable.webhub.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.widgerestimable.webhub.BuildConfig
import com.widgerestimable.webhub.R
import com.widgerestimable.webhub.data.WebAppRepository
import com.widgerestimable.webhub.utils.ThemeManager
import java.io.BufferedReader
import java.io.InputStreamReader

class SettingsActivity : AppCompatActivity() {

    private lateinit var repo: WebAppRepository

    private val createDocLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportTo(it) } }

    private val openDocLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importFrom(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        repo = WebAppRepository.getInstance(this)

        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val radioGroup = findViewById<RadioGroup>(R.id.radio_theme)
        when (ThemeManager.getMode(this)) {
            "light" -> radioGroup.check(R.id.radio_light)
            "dark" -> radioGroup.check(R.id.radio_dark)
            else -> radioGroup.check(R.id.radio_system)
        }
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radio_light -> "light"
                R.id.radio_dark -> "dark"
                else -> "system"
            }
            ThemeManager.setMode(this, mode)
            recreate()
        }

        findViewById<MaterialButton>(R.id.btn_manager).setOnClickListener {
            startActivity(Intent(this, ManagerActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btn_clear_caches).setOnClickListener {
            val throwaway = WebView(this)
            throwaway.clearCache(true)
            throwaway.destroy()
            Toast.makeText(this, R.string.settings_clear_all_caches, Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btn_reset).setOnClickListener { confirmReset() }

        findViewById<MaterialButton>(R.id.btn_export).setOnClickListener {
            createDocLauncher.launch("webhub-backup.json")
        }
        findViewById<MaterialButton>(R.id.btn_import).setOnClickListener {
            openDocLauncher.launch(arrayOf("application/json"))
        }

        findViewById<android.widget.TextView>(R.id.text_about).text =
            "Version ${BuildConfig.VERSION_NAME}\nDéveloppeur : Widger\nLicence : MIT"
    }

    private fun exportTo(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(repo.exportJson().toByteArray())
            }
            Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFrom(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).readText()
            } ?: return
            if (repo.importJson(json)) {
                Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_confirm_title)
            .setMessage(R.string.reset_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.reset_confirm_action) { _, _ ->
                repo.resetAll()
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
                val throwaway = WebView(this)
                throwaway.clearCache(true)
                throwaway.destroy()
                Toast.makeText(this, R.string.reset_confirm_title, Toast.LENGTH_SHORT).show()
                finish()
            }
            .show()
    }
}

