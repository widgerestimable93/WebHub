package com.widgerestimable.webhub.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputEditText
import com.widgerestimable.webhub.R
import com.widgerestimable.webhub.data.WebAppEntry
import com.widgerestimable.webhub.data.WebAppRepository
import com.widgerestimable.webhub.utils.UrlValidator

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

    fun show(
        context: Context,
        existing: WebAppEntry?,
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

        inputName.setText(existing?.name ?: "")
        inputDescription.setText(existing?.description ?: "")
        inputUrl.setText(existing?.url ?: "")
        inputIcon.setText(existing?.iconEmoji ?: "")
        inputCategory.setText(existing?.category ?: "")
        checkboxFavorite.isChecked = existing?.favorite ?: false

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
                val entry = existing?.apply {
                    this.name = name
                    this.description = inputDescription.text?.toString()?.trim().orEmpty()
                    this.url = url
                    this.iconEmoji = inputIcon.text?.toString()?.trim().orEmpty()
                    this.category = inputCategory.text?.toString()?.trim().orEmpty()
                    this.color = selectedColor
                    this.favorite = checkboxFavorite.isChecked
                } ?: WebAppEntry(
                    id = repo.generateId(name),
                    name = name,
                    description = inputDescription.text?.toString()?.trim().orEmpty(),
                    url = url,
                    iconEmoji = inputIcon.text?.toString()?.trim().orEmpty(),
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

