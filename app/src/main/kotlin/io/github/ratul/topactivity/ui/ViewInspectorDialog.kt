/*
 *   Copyright (C) 2022 Ratul Hasan
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.ratul.topactivity.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.os.Build
import android.text.TextUtils
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import io.github.ratul.topactivity.R
import io.github.ratul.topactivity.services.AccessibilityMonitoringService
import io.github.ratul.topactivity.services.ViewInfo
import java.io.File

class ViewInspectorDialog(context: Context, private val viewInfo: ViewInfo) : Dialog(context, R.style.AppTheme) {

    private val clipboardManager: ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(createContentView(context))
        setupWindow()
    }

    private fun setupWindow() {
        window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setDimAmount(0.5f)

            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
    }

    private fun createContentView(context: Context): View {
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.view_info)
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        container.addView(title)

        val infoItems = collectViewInfo(context)

        for ((label, value, copyable) in infoItems) {
            container.addView(createInfoRow(context, label, value, copyable))
        }

        val closeBtn = TextView(context).apply {
            text = context.getString(R.string.cancel)
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
            setOnClickListener { dismiss() }
        }
        container.addView(closeBtn)

        scrollView.addView(container)
        return scrollView
    }

    private fun collectViewInfo(context: Context): List<Triple<String, String, Boolean>> {
        val info = mutableListOf<Triple<String, String, Boolean>>()

        info.add(Triple(context.getString(R.string.view_class), viewInfo.className, true))
        val pkgName = viewInfo.packageName
        info.add(Triple(context.getString(R.string.view_package), pkgName, true))

        val resourceIdName = viewInfo.resourceId
        if (resourceIdName == "No ID") {
            info.add(Triple(context.getString(R.string.view_id), resourceIdName, true))
            info.add(Triple(context.getString(R.string.view_id_int), resourceIdName, true))
            info.add(Triple(context.getString(R.string.view_id_hex), resourceIdName, true))
        } else {
            val viewIdResourceName = resourceIdName.split(File.separatorChar)[1]
            info.add(Triple(context.getString(R.string.view_id), "R.id.$viewIdResourceName", true))
            if(!TextUtils.isEmpty(pkgName)) {
                val packageContext = context.createPackageContext(pkgName, 0)
                val viewIdInt = packageContext.resources.getIdentifier(viewIdResourceName, "id", pkgName)
                val hex = "0x" + Integer.toHexString(viewIdInt)
                info.add(Triple(context.getString(R.string.view_id_int), viewIdInt.toString(), true))
                info.add(Triple(context.getString(R.string.view_id_hex), hex, true))
            }
        }

        info.add(Triple(
            context.getString(R.string.view_bounds),
            "left=${viewInfo.bounds.left}, top=${viewInfo.bounds.top}, right=${viewInfo.bounds.right}, bottom=${viewInfo.bounds.bottom}",
            true
        ))

        info.add(Triple(
            context.getString(R.string.view_location),
            "x=${viewInfo.locationOnScreen[0]}, y=${viewInfo.locationOnScreen[1]}",
            true
        ))

        info.add(Triple(
            context.getString(R.string.view_size),
            "${viewInfo.width} x ${viewInfo.height}",
            true
        ))

        info.add(Triple(
            context.getString(R.string.view_visibility),
            if (viewInfo.isVisible) "Visible" else "Not Visible",
            false
        ))

        info.add(Triple(context.getString(R.string.view_enabled), viewInfo.isEnabled.toString(), false))
        info.add(Triple(context.getString(R.string.view_focusable), viewInfo.isFocusable.toString(), false))
        info.add(Triple(context.getString(R.string.view_clickable), viewInfo.isClickable.toString(), false))
        info.add(Triple(context.getString(R.string.view_long_clickable), viewInfo.isLongClickable.toString(), false))
        info.add(Triple(context.getString(R.string.view_focused), viewInfo.isFocused.toString(), false))
        info.add(Triple(context.getString(R.string.view_selected), viewInfo.isSelected.toString(), false))

        viewInfo.contentDescription.let {
            if (it.isNotEmpty()) {
                info.add(Triple(context.getString(R.string.view_content_desc), it, true))
            }
        }

        viewInfo.text.let {
            if (it.isNotEmpty()) {
                info.add(Triple(context.getString(R.string.view_text), it, true))
            }
        }

        info.add(Triple(context.getString(R.string.view_parent), viewInfo.parentClass, true))
        info.add(Triple(context.getString(R.string.view_children_count), viewInfo.childCount.toString(), false))

        return info
    }

    private fun createInfoRow(context: Context, label: String, value: String, copyable: Boolean): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val labelView = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.argb(180, 255, 255, 255))
        }
        container.addView(labelView)

        val valueContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val valueView = TextView(context).apply {
            text = value.ifEmpty { context.getString(R.string.unknown) }
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            if(label == context.getString(R.string.view_parent) && viewInfo.parent != null) {
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary))
                container.setOnClickListener {
                    dismiss()
                    show(context, AccessibilityMonitoringService.getViewInfo(viewInfo.parent))
                }
            } else {
                setTextColor(Color.WHITE)
                setTextIsSelectable(true)
            }
        }
        valueContainer.addView(valueView)

        if (copyable) {
            val copyBtn = TextView(context).apply {
                text = context.getString(R.string.copy)
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary))
                setPadding(16, 8, 8, 8)
                setOnClickListener {
                    val clip = ClipData.newPlainText(label, value)
                    clipboardManager.setPrimaryClip(clip)
                    window?.decorView?.rootView?.let { view ->
                        Snackbar.make(view, context.getString(R.string.copied_to_clipboard, label), Snackbar.LENGTH_SHORT)
                    }?.show()
                }
            }
            valueContainer.addView(copyBtn)
        }

        container.addView(valueContainer)

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
            setBackgroundColor(Color.argb(50, 255, 255, 255))
        }
        container.addView(divider)

        return container
    }

    override fun show() {
        window?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                attributes = attributes.apply { type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY }
            } else {
                @Suppress("DEPRECATION")
                attributes = attributes.apply { type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT }
            }
        }
        super.show()
        window?.decorView?.setBackgroundColor(Color.BLACK)
    }

    companion object {
        fun show(context: Context, viewInfo: ViewInfo) {
            ViewInspectorDialog(context, viewInfo).show()
        }
    }
}