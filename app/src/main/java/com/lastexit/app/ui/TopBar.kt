package com.lastexit.app.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.lastexit.app.R
import com.lastexit.app.ui.kit.Palette
import com.lastexit.app.ui.kit.Ui
import com.lastexit.app.ui.kit.dpi
import com.lastexit.app.ui.kit.markAsHeading

/** Back arrow + title + optional trailing views. */
object TopBar {
    fun create(
        context: Context,
        palette: Palette,
        title: String,
        onBack: () -> Unit,
        vararg trailing: View,
        onGradient: Boolean = false,
    ): LinearLayout {
        val fg = if (onGradient) android.graphics.Color.WHITE else palette.onSurface
        val bar = Ui.horizontal(context)
        bar.setPadding(context.dpi(4), context.dpi(4), context.dpi(4), context.dpi(4))
        bar.minimumHeight = context.dpi(56)
        bar.addView(Ui.iconButton(context, palette, R.drawable.ic_arrow_back, "Back", tint = fg) { onBack() })
        val titleView = Ui.text(context, 20f, fg, bold = true, value = title, maxLines = 1)
        titleView.markAsHeading()
        bar.addView(titleView, Ui.weight().apply { marginStart = context.dpi(4) })
        trailing.forEach { bar.addView(it) }
        return bar
    }
}
