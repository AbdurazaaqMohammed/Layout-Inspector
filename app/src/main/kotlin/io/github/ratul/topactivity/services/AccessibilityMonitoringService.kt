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
package io.github.ratul.topactivity.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Rect
import android.util.LruCache
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.github.ratul.topactivity.extensions.isActivity
import io.github.ratul.topactivity.repository.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

@SuppressLint("AccessibilityPolicy")
class AccessibilityMonitoringService : AccessibilityService() {

    private val activityCache = LruCache<String, Boolean>(500)
    private val inspectorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var inspectorJob: Job? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!DataRepository.appState.value.running) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleWindowStateChange(event)
        }
    }

    private fun handleWindowStateChange(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        val cacheKey = "$pkg/$cls"

        val isCachedActivity = activityCache[cacheKey]
        if (isCachedActivity != null) {
            if (isCachedActivity) DataRepository.updateData(pkg, cls)
            return
        }

        val isActivity = packageManager.isActivity(pkg, cls)
        activityCache.put(cacheKey, isActivity)

        if (isActivity) DataRepository.updateData(pkg, cls)
    }

    fun findViewAt(x: Int, y: Int): ViewInfo? {
        val root = rootInActiveWindow ?: return null
        return findViewAtRecursive(root, x, y)?.let { getViewInfo(it) }
    }

    private fun findViewAtRecursive(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!bounds.contains(x, y)) {
            return null
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findViewAtRecursive(child, x, y)
            if (result != null) {
                return result
            }
        }

        return node
    }

    private fun collectViewsRecursive(node: AccessibilityNodeInfo, views: MutableList<ViewInfo>) {
        val viewInfo = getViewInfo(node)
        if (viewInfo.bounds.width() > 0 && viewInfo.bounds.height() > 0) {
            views.add(viewInfo)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectViewsRecursive(child, views)
            child.recycle()
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onRebind(intent: Intent) {
        instance = this
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        instance = null
        return true
    }

    override fun onDestroy() {
        inspectorJob?.cancel()
        inspectorScope.coroutineContext[Job]?.cancel()
        instance = null
        super.onDestroy()
    }

    companion object {
        var instance: AccessibilityMonitoringService? = null
            private set

        fun getViewInfo(nodeInfo: AccessibilityNodeInfo): ViewInfo {
            val bounds = Rect()
            nodeInfo.getBoundsInScreen(bounds)

            val location = IntArray(2)
            location[0] = bounds.left
            location[1] = bounds.top

            val parent = nodeInfo.parent
            val parentClass = parent?.className?.toString() ?: "null"

            val viewIdResourceName = nodeInfo.viewIdResourceName
            val pkgName = nodeInfo.packageName?.toString()
            return ViewInfo(
                className = nodeInfo.className?.toString() ?: "Unknown",
                resourceId = viewIdResourceName ?: "No ID",
                text = nodeInfo.text?.toString() ?: "",
                contentDescription = nodeInfo.contentDescription?.toString() ?: "",
                bounds = bounds,
                locationOnScreen = location,
                width = bounds.width(),
                height = bounds.height(),
                isVisible = nodeInfo.isVisibleToUser,
                isEnabled = nodeInfo.isEnabled,
                isFocusable = nodeInfo.isFocusable,
                isClickable = nodeInfo.isClickable,
                isLongClickable = nodeInfo.isLongClickable,
                isFocused = nodeInfo.isFocused,
                isSelected = nodeInfo.isSelected,
                isChecked = nodeInfo.isChecked,
                parentClass = parentClass,
                childCount = nodeInfo.childCount,
                packageName = pkgName ?: "",
                parent = parent
            )
        }
    }
}

data class ViewInfo(
    val className: String,
    val resourceId: String,
    val text: String,
    val contentDescription: String,
    val bounds: Rect,
    val locationOnScreen: IntArray,
    val width: Int,
    val height: Int,
    val isVisible: Boolean,
    val isEnabled: Boolean,
    val isFocusable: Boolean,
    val isClickable: Boolean,
    val isLongClickable: Boolean,
    val isFocused: Boolean,
    val isSelected: Boolean,
    val isChecked: Boolean,
    val parentClass: String,
    val childCount: Int,
    val packageName: String,
    val parent: AccessibilityNodeInfo?
)