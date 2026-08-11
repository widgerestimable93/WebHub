package com.widgerestimable.webhub.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.widgerestimable.webhub.R
import com.widgerestimable.webhub.data.WebAppEntry
import com.widgerestimable.webhub.data.WebAppRepository
import com.widgerestimable.webhub.session.WebAppSessionManager
import com.widgerestimable.webhub.utils.ThemeManager

class HubActivity : AppCompatActivity() {

    private lateinit var repo: WebAppRepository
    private lateinit var adapter: WebAppAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var searchInput: TextInputEditText
    private lateinit var chipGroup: ChipGroup

    private var query: String = ""
    private var filter: String = "all" // all | favorites | recent

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hub)

        repo = WebAppRepository.getInstance(this)

        recycler = findViewById(R.id.recycler_apps)
        emptyState = findViewById(R.id.empty_state)
        searchInput = findViewById(R.id.input_search)
        chipGroup = findViewById(R.id.chip_group_filters)

        adapter = WebAppAdapter(
            mode = WebAppListMode.HUB,
            onOpen = { openApp(it) },
            onEdit = { editApp(it) },
            onDelete = { confirmDelete(it) },
            onToggleFavorite = { toggleFavorite(it) },
            onLogout = { confirmLogout(it) },
            onClearData = { confirmClearData(it) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString()?.trim()?.lowercase().orEmpty()
                refresh()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            filter = when (checkedIds.firstOrNull()) {
                R.id.chip_favorites -> "favorites"
                R.id.chip_recent -> "recent"
                else -> "all"
            }
            refresh()
        }

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            AddEditWebAppDialog.show(this, null) { refresh() }
        }

        findViewById<android.widget.ImageButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<android.widget.ImageButton>(R.id.btn_manager).setOnClickListener {
            startActivity(Intent(this, ManagerActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        var list = repo.getAll().filter { it.active }
        if (query.isNotEmpty()) {
            list = list.filter {
                it.name.lowercase().contains(query) ||
                    it.description.lowercase().contains(query) ||
                    it.category.lowercase().contains(query)
            }
        }
        list = when (filter) {
            "favorites" -> list.filter { it.favorite }
            "recent" -> list.filter { it.lastOpenedAt > 0 }.sortedByDescending { it.lastOpenedAt }
            else -> list.sortedBy { it.name.lowercase() }
        }
        adapter.submit(list)
        emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openApp(entry: WebAppEntry) {
        if (entry.url.isBlank()) {
            AddEditWebAppDialog.show(this, entry) { refresh() }
            return
        }
        repo.markOpened(entry.id)
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra(WebViewActivity.EXTRA_APP_ID, entry.id)
        startActivity(intent)
    }

    private fun editApp(entry: WebAppEntry) {
        AddEditWebAppDialog.show(this, entry) { refresh() }
    }

    private fun toggleFavorite(entry: WebAppEntry) {
        entry.favorite = !entry.favorite
        repo.update(entry)
        refresh()
    }

    private fun confirmDelete(entry: WebAppEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                WebAppSessionManager.logout(entry.url, null)
                repo.delete(entry.id)
                refresh()
            }
            .show()
    }

    private fun confirmLogout(entry: WebAppEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_logout) { _, _ ->
                WebAppSessionManager.logout(entry.url, null) {
                    runOnUiThread {
                        android.widget.Toast.makeText(
                            this, getString(R.string.logout_done, entry.name), android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun confirmClearData(entry: WebAppEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_data_confirm_title)
            .setMessage(R.string.clear_data_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                WebAppSessionManager.logout(entry.url, null)
            }
            .show()
    }
}

