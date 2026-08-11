package com.widgerestimable.webhub.session

import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebStorage
import android.webkit.WebView
import java.net.URI

/**
 * WebAppSessionManager — implémente une VRAIE déconnexion ciblée pour une
 * seule Web App, en s'appuyant sur les API natives Android WebView.
 *
 * Ce que fait réellement chaque opération (honnêteté technique, voir README) :
 *
 * 1. clearCookiesForOrigin() — CookieManager n'expose pas de méthode
 *    "supprimer les cookies d'un domaine" directe : on lit les cookies
 *    présents pour l'URL, puis on force l'expiration de CHACUN d'eux
 *    (Max-Age=0) sur ce domaine précis. Les cookies des autres domaines
 *    (donc des autres Web Apps) ne sont jamais lus ni modifiés.
 *
 * 2. clearWebStorageForOrigin() — WebStorage.deleteOrigin(origin) est une
 *    API publique Android qui supprime le localStorage/IndexedDB d'UNE
 *    origine précise, sans toucher aux autres. C'est une isolation réelle,
 *    pas une convention côté JavaScript.
 *
 * LIMITE HONNÊTE : le cache HTTP disque de WebView (WebView.clearCache) est
 * partagé par TOUTES les WebView du processus — Android n'expose aucune API
 * publique pour vider le cache HTTP d'une seule origine. "Vider le cache"
 * par Web App ne peut donc PAS être garanti isolé ; seul un vidage global
 * (tous les caches WebHub) l'est réellement. C'est pourquoi l'action
 * "Vider le cache" dans WebHub est proposée au niveau des Paramètres
 * (globale), et non comme une garantie par application.
 */
object WebAppSessionManager {

    /**
     * Déconnecte une seule Web App : efface ses cookies et son WebStorage
     * (localStorage/IndexedDB) pour son origine uniquement. N'affecte jamais
     * les autres Web Apps. [webView] est l'instance actuellement affichée
     * (utilisée pour clearFormData/clearHistory, qui sont déjà isolés par
     * instance).
     */
    fun logout(url: String, webView: WebView?, onDone: (() -> Unit)? = null) {
        val origin = originOf(url)
        if (origin == null) {
            onDone?.invoke()
            return
        }
        clearCookiesForOrigin(url)
        clearWebStorageForOrigin(origin) {
            webView?.clearFormData()
            webView?.clearHistory()
            onDone?.invoke()
        }
    }

    fun originOf(url: String): String? = try {
        val uri = URI(url)
        if (uri.scheme == null || uri.host == null) null
        else "${uri.scheme}://${uri.host}" + (if (uri.port != -1) ":${uri.port}" else "")
    } catch (e: Exception) {
        null
    }

    private fun clearCookiesForOrigin(url: String) {
        val cm = CookieManager.getInstance()
        val cookieString = cm.getCookie(url) ?: return
        val host = try { URI(url).host } catch (e: Exception) { null } ?: return

        cookieString.split(";").forEach { pair ->
            val name = pair.substringBefore("=").trim()
            if (name.isNotEmpty()) {
                // Expire le cookie sur le domaine exact et sur sa forme ".domaine"
                // (les cookies peuvent être posés avec ou sans le point de préfixe).
                cm.setCookie(url, "$name=; Max-Age=0; Path=/;")
                cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=$host;")
                cm.setCookie(url, "$name=; Max-Age=0; Path=/; Domain=.$host;")
            }
        }
        cm.flush()
    }

    private fun clearWebStorageForOrigin(origin: String, onDone: () -> Unit) {
        val storage = WebStorage.getInstance()
        storage.getOrigins(ValueCallback { originsMap ->
            val matchKey = originsMap?.keys?.firstOrNull { key ->
                key.trimEnd('/') == origin.trimEnd('/')
            }
            if (matchKey != null) {
                storage.deleteOrigin(matchKey)
            } else {
                // Repli : tente quand même la suppression avec l'origine construite.
                storage.deleteOrigin(origin)
            }
            onDone()
        })
    }

    /**
     * Vide le cache HTTP partagé de TOUTES les Web Apps (voir limite ci-dessus).
     * À utiliser uniquement depuis les Paramètres globaux, jamais présenté
     * comme une action isolée à une seule Web App.
     */
    fun clearGlobalHttpCache(webView: WebView) {
        webView.clearCache(true)
    }
}

