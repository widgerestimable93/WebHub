package com.widgerestimable.webhub.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Gère la copie locale des icônes personnalisées choisies par l'utilisateur
 * pour chaque Web App (voir [AddEditWebAppDialog]).
 *
 * L'image est copiée dans le stockage interne de l'app (`filesDir/icons/`)
 * plutôt que référencée par son [Uri] d'origine : un `content://Uri` de la
 * galerie peut devenir invalide après un redémarrage ou si le fichier
 * source est déplacé/supprimé côté utilisateur. La copie locale est
 * strictement liée à l'`id` de la Web App concernée — supprimer ou
 * remplacer l'icône d'une Web App ne touche jamais celle des autres,
 * conformément au principe d'isolation de WebHub.
 */
object IconStorage {

    private const val MAX_DIMENSION_PX = 256

    private fun iconsDir(context: Context): File =
        File(context.filesDir, "icons").apply { if (!exists()) mkdirs() }

    fun iconFile(context: Context, entryId: String): File =
        File(iconsDir(context), "$entryId.png")

    /**
     * Copie et redimensionne l'image pointée par [sourceUri] vers le stockage
     * interne, pour la Web App [entryId]. Retourne le chemin absolu du fichier
     * créé, ou null en cas d'échec (image illisible, etc.).
     */
    fun saveIcon(context: Context, entryId: String, sourceUri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return null
            val original = input.use { BitmapFactory.decodeStream(it) } ?: return null

            val scale = MAX_DIMENSION_PX.toFloat() / maxOf(original.width, original.height)
            val bitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    original,
                    (original.width * scale).toInt().coerceAtLeast(1),
                    (original.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else original

            val dest = iconFile(context, entryId)
            FileOutputStream(dest).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (bitmap !== original) original.recycle()
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /** Supprime UNIQUEMENT l'icône de cette Web App — n'affecte jamais les autres. */
    fun deleteIcon(context: Context, entryId: String) {
        val f = iconFile(context, entryId)
        if (f.exists()) f.delete()
    }

    /** Renomme le fichier d'icône si l'id de la Web App change (cas rare, non utilisé actuellement). */
    fun renameIcon(context: Context, oldEntryId: String, newEntryId: String): String? {
        val old = iconFile(context, oldEntryId)
        if (!old.exists()) return null
        val new = iconFile(context, newEntryId)
        return if (old.renameTo(new)) new.absolutePath else null
    }
}
