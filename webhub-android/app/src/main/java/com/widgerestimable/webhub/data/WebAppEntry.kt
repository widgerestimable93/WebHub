package com.widgerestimable.webhub.data

/**
 * Représente une Web App enregistrée dans WebHub.
 *
 * [id] est l'identifiant unique et stable de l'application (ex: "app_baccpro"),
 * utilisé par [com.widgerestimable.webhub.session.WebAppSessionManager] pour
 * cibler précisément les données à effacer lors d'une déconnexion, sans
 * jamais toucher aux autres Web Apps.
 */
data class WebAppEntry(
    val id: String,
    var name: String,
    var description: String,
    var url: String,
    var iconEmoji: String,
    var color: String,
    var category: String,
    var favorite: Boolean = false,
    var active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    var lastOpenedAt: Long = 0L
) {
    /** Domaine (origine) de l'application — utilisé pour l'isolation par origine et la déconnexion ciblée. */
    fun originHost(): String? = try {
        java.net.URI(url).host
    } catch (e: Exception) {
        null
    }
}

