package com.widgerestimable.webhub.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.widgerestimable.webhub.R
import com.widgerestimable.webhub.data.WebAppEntry
import com.widgerestimable.webhub.data.WebAppRepository
import com.widgerestimable.webhub.session.WebAppSessionManager
import com.widgerestimable.webhub.utils.ThemeManager

class ManagerActivity : AppCompatActivity() {

    private lateinit var repo: WebAppRepository
    private lateinit var adapter: WebAppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager)

        repo = WebAppRepository.getInstance(this)

        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        adapter = WebAppAdapter(
            mode = WebAppListMode.MANAGER,
            onOpen = { entry ->
                if (entry.active && entry.url.isNotBlank()) {
                    repo.markOpened(entry.id)
                    val intent = android.content.Intent(this, WebViewActivity::class.java)
                    intent.putExtra(WebViewActivity.EXTRA_APP_ID, entry.id)
                    startActivity(intent)
                }
            },
            onEdit = { AddEditWebAppDialog.show(this, it) { refresh() } },
            onDelete = { confirmDelete(it) },
            onToggleFavorite = { it.favorite = !it.favorite; repo.update(it); refresh() },
            onLogout = { confirmLogout(it) },
            onClearData = { confirmClearData(it) },
            onToggleActive = { it.active = !it.active; repo.update(it); refresh() }
        )

        findViewById<RecyclerView>(R.id.recycler_manager).apply {
            layoutManager = LinearLayoutManager(this@ManagerActivity)
            adapter = this@ManagerActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val list = repo.getAll().sortedBy { it.name.lowercase() }
        adapter.submit(list)
        findViewById<View>(R.id.empty_state).visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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

