package com.widgerestimable.webhub.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.widgerestimable.webhub.R
import com.widgerestimable.webhub.data.WebAppEntry
import com.widgerestimable.webhub.data.WebAppRepository
import com.widgerestimable.webhub.utils.IconStorage
import com.widgerestimable.webhub.utils.UrlValidator
import java.io.File

/**
 * Boîte de dialogue d'ajout/édition — pas de DialogFragment ici pour rester
 * simple (pas de FragmentManager à gérer), utilisée directement depuis les
 * Activities via [show].
 */
object AddEditWebAppDialog {

    private val swatches = listOf(
        "#14315C", "#00b8a9", "#B8860B", "#0E7C61",
        "#8E44AD", "#E4572E", "#2C7BE5", "#455A64"
    )

    /**
     * @param pickImage Fonction fournie par l'Activity appelante (HubActivity /
     *   ManagerActivity) qui déclenche le sélecteur d'image système (Photo Picker)
     *   et transmet l'[Uri] choisie au callback reçu en paramètre. L'Activity doit
     *   posséder un [androidx.activity.result.ActivityResultLauncher] déjà
     *   enregistré — voir HubActivity/ManagerActivity. Si null, le bouton
     *   "Choisir une image" est masqué et seul l'emoji reste disponible.
     */
    fun show(
        context: Context,
        existing: WebAppEntry?,
        pickImage: ((onPicked: (Uri) -> Unit) -> Unit)? = null,
        onSaved: (WebAppEntry) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_edit_webapp, null)
        val inputName = view.findViewById<TextInputEditText>(R.id.input_name)
        val inputDescription = view.findViewById<TextInputEditText>(R.id.input_description)
        val inputUrl = view.findViewById<TextInputEditText>(R.id.input_url)
        val urlError = view.findViewById<android.widget.TextView>(R.id.text_url_error)
        val inputIcon = view.findViewById<TextInputEditText>(R.id.input_icon)
        val inputCategory = view.findViewById<TextInputEditText>(R.id.input_category)
        val colorRow = view.findViewById<LinearLayout>(R.id.color_row)
        val checkboxFavorite = view.findViewById<CheckBox>(R.id.checkbox_favorite)
        val iconPreview = view.findViewById<ShapeableImageView>(R.id.icon_preview)
        val btnPickIcon = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_pick_icon)
        val btnRemoveIcon = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_remove_icon)

        inputName.setText(existing?.name ?: "")
        inputDescription.setText(existing?.description ?: "")
        inputUrl.setText(existing?.url ?: "")
        inputIcon.setText(existing?.iconEmoji ?: "")
        inputCategory.setText(existing?.category ?: "")
        checkboxFavorite.isChecked = existing?.favorite ?: false

        // --- Icône personnalisée (image) ---
        // pickedIconUri : nouvelle image choisie pendant cette session du dialogue (pas encore copiée).
        // iconRemoved : l'utilisateur a explicitement retiré l'image existante -> retour à l'emoji.
        var pickedIconUri: Uri? = null
        var iconRemoved = false

        fun updateIconPreviewVisibility(hasImage: Boolean) {
            iconPreview.visibility = if (hasImage) View.VISIBLE else View.GONE
            btnRemoveIcon.visibility = if (hasImage) View.VISIBLE else View.GONE
        }

        val existingIconFile = existing?.iconImagePath?.let { File(it) }
        if (existingIconFile != null && existingIconFile.exists()) {
            iconPreview.setImageURI(Uri.fromFile(existingIconFile))
            updateIconPreviewVisibility(true)
        } else {
            updateIconPreviewVisibility(false)
        }

        if (pickImage == null) {
            btnPickIcon.visibility = View.GONE
        } else {
            btnPickIcon.setOnClickListener {
                pickImage { uri ->
                    pickedIconUri = uri
                    iconRemoved = false
                    iconPreview.setImageURI(uri)
                    updateIconPreviewVisibility(true)
                }
            }
        }
        btnRemoveIcon.setOnClickListener {
            pickedIconUri = null
            iconRemoved = true
            updateIconPreviewVisibility(false)
        }

        var selectedColor = existing?.color?.takeIf { it.isNotBlank() } ?: swatches[0]
        val swatchViews = mutableListOf<View>()

        fun refreshSwatchSelection() {
            swatchViews.forEachIndexed { i, v ->
                v.alpha = if (swatches[i] == selectedColor) 1f else 0.4f
            }
        }

        val density = context.resources.displayMetrics.density
        val size = (30 * density).toInt()
        val margin = (6 * density).toInt()
        swatches.forEach { hex ->
            val swatch = View(context)
            val params = LinearLayout.LayoutParams(size, size)
            params.marginEnd = margin
            swatch.layoutParams = params
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(hex))
            }
            swatch.setOnClickListener {
                selectedColor = hex
                refreshSwatchSelection()
            }
            colorRow.addView(swatch)
            swatchViews.add(swatch)
        }
        refreshSwatchSelection()

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (existing == null) R.string.dialog_add_title else R.string.dialog_edit_title)
            .setView(view)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = inputName.text?.toString()?.trim().orEmpty()
                val url = inputUrl.text?.toString()?.trim().orEmpty()

                if (name.isEmpty()) {
                    inputName.error = context.getString(R.string.error_name_required)
                    return@setOnClickListener
                }
                if (!UrlValidator.isValidHttps(url)) {
                    urlError.visibility = View.VISIBLE
                    urlError.text = context.getString(R.string.error_invalid_url)
                    return@setOnClickListener
                }
                urlError.visibility = View.GONE

                val repo = WebAppRepository.getInstance(context)
                val entryId = existing?.id ?: repo.generateId(name)

                // Résout le chemin d'icône final AVANT de construire/mettre à jour l'entrée :
                // - une nouvelle image a été choisie -> on la copie dans le stockage interne ;
                // - l'utilisateur a retiré l'image -> on supprime le fichier et on revient à l'emoji ;
                // - sinon -> on conserve ce qui existait déjà (ou null pour une nouvelle Web App).
                val finalIconPath: String? = when {
                    pickedIconUri != null -> IconStorage.saveIcon(context, entryId, pickedIconUri!!)
                        ?: existing?.iconImagePath
                    iconRemoved -> {
                        IconStorage.deleteIcon(context, entryId)
                        null
                    }
                    else -> existing?.iconImagePath
                }

                val entry = existing?.apply {
                    this.name = name
                    this.description = inputDescription.text?.toString()?.trim().orEmpty()
                    this.url = url
                    this.iconEmoji = inputIcon.text?.toString()?.trim().orEmpty()
                    this.iconImagePath = finalIconPath
                    this.category = inputCategory.text?.toString()?.trim().orEmpty()
                    this.color = selectedColor
                    this.favorite = checkboxFavorite.isChecked
                } ?: WebAppEntry(
                    id = entryId,
                    name = name,
                    description = inputDescription.text?.toString()?.trim().orEmpty(),
                    url = url,
                    iconEmoji = inputIcon.text?.toString()?.trim().orEmpty(),
                    iconImagePath = finalIconPath,
                    color = selectedColor,
                    category = inputCategory.text?.toString()?.trim().orEmpty(),
                    favorite = checkboxFavorite.isChecked
                )

                if (existing == null) repo.add(entry) else repo.update(entry)
                onSaved(entry)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
