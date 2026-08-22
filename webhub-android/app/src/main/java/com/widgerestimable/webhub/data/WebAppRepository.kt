package com.widgerestimable.webhub.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * WebAppDataManager — source unique de vérité pour la liste des Web Apps
 * enregistrées. Persistance simple en JSON (SharedPreferences), suffisante
 * pour ce volume de données (quelques dizaines d'entrées au plus) et sans
 * dépendance lourde (pas de Room ici, par choix de simplicité — voir README).
 *
 * Ne stocke QUE les métadonnées (nom, url, icône, catégorie...). Les
 * cookies/sessions/localStorage de chaque Web App vivent dans le moteur
 * WebView lui-même (voir WebAppSessionManager), jamais ici.
 */
class WebAppRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext
        .getSharedPreferences("webhub_apps", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<MutableList<WebAppEntry>>() {}.type

    companion object {
        private const val KEY_APPS = "apps_json"

        @Volatile private var instance: WebAppRepository? = null

        fun getInstance(context: Context): WebAppRepository =
            instance ?: synchronized(this) {
                instance ?: WebAppRepository(context).also { instance = it }
            }

        fun defaultApps(): List<WebAppEntry> = listOf(
            WebAppEntry(
                id = "app_baccpro", name = "baccPRO", description = "Révision du Bac",
                url = "", iconEmoji = "📚", color = "#14315C", category = "Éducation"
            ),
            WebAppEntry(
                id = "app_9pro", name = "9PRO", description = "Révision du 9e AF",
                url = "", iconEmoji = "🎓", color = "#1E4785", category = "Éducation"
            ),
            WebAppEntry(
                id = "app_collection_doree", name = "Collection Dorée", description = "Boutique en ligne",
                url = "", iconEmoji = "🛒", color = "#B8860B", category = "Commerce"
            ),
            WebAppEntry(
                id = "app_oddslab", name = "OddsLab", description = "Analyse sportive",
                url = "", iconEmoji = "⚽", color = "#0E7C61", category = "Sport"
            )
        )
    }

    @Synchronized
    fun getAll(): MutableList<WebAppEntry> {
        val json = prefs.getString(KEY_APPS, null) ?: run {
            val defaults = defaultApps().toMutableList()
            saveAll(defaults)
            return defaults
        }
        return try {
            gson.fromJson(json, listType) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    fun saveAll(apps: List<WebAppEntry>) {
        prefs.edit().putString(KEY_APPS, gson.toJson(apps)).apply()
    }

    fun getById(id: String): WebAppEntry? = getAll().find { it.id == id }

    fun add(entry: WebAppEntry): WebAppEntry {
        val apps = getAll()
        apps.add(entry)
        saveAll(apps)
        return entry
    }

    fun update(entry: WebAppEntry) {
        val apps = getAll()
        val idx = apps.indexOfFirst { it.id == entry.id }
        if (idx >= 0) {
            apps[idx] = entry
            saveAll(apps)
        }
    }

    /** Supprime UNIQUEMENT cette Web App — n'affecte jamais les autres. */
    fun delete(id: String) {
        val apps = getAll()
        apps.removeAll { it.id == id }
        saveAll(apps)
        com.widgerestimable.webhub.utils.IconStorage.deleteIcon(appContext, id)
    }

    fun markOpened(id: String) {
        val apps = getAll()
        apps.find { it.id == id }?.let {
            it.lastOpenedAt = System.currentTimeMillis()
            saveAll(apps)
        }
    }

    fun generateId(name: String): String {
        val base = "app_" + name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "webapp" }
        val existingIds = getAll().map { it.id }.toSet()
        if (base !in existingIds) return base
        return base + "_" + UUID.randomUUID().toString().take(6)
    }

    fun exportJson(): String = gson.toJson(getAll())

    /** Remplace entièrement la liste par le contenu importé. */
    fun importJson(json: String): Boolean = try {
        val imported: MutableList<WebAppEntry> = gson.fromJson(json, listType) ?: mutableListOf()
        saveAll(imported)
        true
    } catch (e: Exception) {
        false
    }

    /** Supprime toutes les Web Apps enregistrées (les données WebView elles-mêmes sont effacées séparément). */
    fun resetAll() {
        prefs.edit().remove(KEY_APPS).apply()
    }
}

