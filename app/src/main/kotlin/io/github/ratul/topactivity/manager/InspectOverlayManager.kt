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
package io.github.ratul.topactivity.manager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import io.github.ratul.topactivity.App
import io.github.ratul.topactivity.services.AccessibilityMonitoringService
import io.github.ratul.topactivity.services.ViewInfo
import io.github.ratul.topactivity.ui.ViewInspectorDialog

class InspectOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: InspectOverlayView? = null
    var isInspecting = false

    fun show() {
        if (overlayView != null) return

        val overlay = InspectOverlayView(context, this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(overlay, layoutParams)
        overlayView = overlay
        isInspecting = true
        
        overlay.startDrawingViewBoxes()
    }

    fun hide() {
        isInspecting = false
        overlayView?.stopDrawingViewBoxes()
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    fun inspectViewAt(x: Int, y: Int) {
        val accessibilityService = AccessibilityMonitoringService.instance ?: return

        val viewInfo = accessibilityService.findViewAt(x, y) ?: return

        isInspecting = false
        hide()

        showInspectorDialog(viewInfo)
    }

    private fun showInspectorDialog(viewInfo: ViewInfo) {
        Handler(Looper.getMainLooper()).post {
            ViewInspectorDialog(App.instance, viewInfo).show()
        }
    }
}

class InspectOverlayView(
    context: Context,
    private val manager: InspectOverlayManager
) : FrameLayout(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.GREEN
    }
    
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.TRANSPARENT
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f
        isFakeBoldText = true
    }
    
    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 0, 0, 0)
    }

    private var viewBoxes: List<ViewBox> = emptyList()
    private var isDrawing = false
    private val drawRunnable = Runnable { drawViewBoxes() }
    private var lastViewCount = 0
    private var insetTop = 0
    private var insetBottom = 0

    init {
        setBackgroundColor(Color.TRANSPARENT)

        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            insetTop = context.resources.getDimensionPixelSize(resourceId)
        }
        
        val navResourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (navResourceId > 0) {
            insetBottom = context.resources.getDimensionPixelSize(navResourceId)
        }
    }

    fun startDrawingViewBoxes() {
        isDrawing = true
        drawViewBoxes()
    }

    fun stopDrawingViewBoxes() {
        isDrawing = false
        removeCallbacks(drawRunnable)
        viewBoxes = emptyList()
        invalidate()
    }

    private fun drawViewBoxes() {
        if (!isDrawing) return
        
        val accessibilityService = AccessibilityMonitoringService.instance
            ?: return

        val root = accessibilityService.rootInActiveWindow
            ?: return

        val newBoxes = collectViewBoxes(root)
        if (newBoxes.isNotEmpty() && newBoxes.size != lastViewCount) {
            viewBoxes = newBoxes
            lastViewCount = newBoxes.size
            Log.d("InspectOverlay", "Drew ${viewBoxes.size} view boxes")
            invalidate()
        }
        
        postDelayed(drawRunnable, 500)
    }

    private fun collectViewBoxes(node: AccessibilityNodeInfo): List<ViewBox> {
        val boxes = mutableListOf<ViewBox>()
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val adjustedBounds = Rect(bounds)
        if(Build.VERSION.SDK_INT > 32) {
            // I got no idea what is real cause of this problem this is what work in my android 13 device I checked no problem in android 12 emulator or android 10 phone
            adjustedBounds.top -= insetTop
            adjustedBounds.bottom -= insetTop
        }

        if (adjustedBounds.width() > 0 && adjustedBounds.height() > 0) {
            boxes.add(ViewBox(
                bounds = adjustedBounds,
                className = node.className?.toString() ?: "Unknown",
                resourceId = node.viewIdResourceName
            ))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            boxes.addAll(collectViewBoxes(child))
            child.recycle()
        }

        return boxes
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!manager.isInspecting) return false
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                manager.inspectViewAt(x, y)
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        for (box in viewBoxes) {
            val b = box.bounds
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), fillPaint)
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
            
            val text = box.className.substringAfterLast(".").substringAfterLast("$")
            val textWidth = textPaint.measureText(text)
            val textX = b.left.toFloat() + 4f
            val textY = b.top.toFloat() - 4f
            
            canvas.drawRect(
                textX - 2f, textY - textPaint.textSize - 2f,
                textX + textWidth + 4f, textY + 2f,
                textBgPaint
            )
            
            canvas.drawText(text, textX, textY, textPaint)
        }
    }
}

data class ViewBox(
    val bounds: Rect,
    val className: String,
    val resourceId: String?
)