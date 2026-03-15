package com.dude.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        var instance: AssistantAccessibilityService? = null
        private const val TAG = "DudeAccessibility"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service connected!")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We listen but don't need to handle every event
    }

    override fun onInterrupt() {}

    // ─── Back / Home / Recents ────────────────────────────────────────────────

    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    fun pressRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun pullNotificationBar() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun takeScreenshot() = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    // ─── Click by text/description ────────────────────────────────────────────

    fun clickNodeWithText(targetText: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val node = findNodeByText(rootNode, targetText.lowercase())
        return if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Clicked: $targetText")
            true
        } else {
            // Try by content description
            val descNode = findNodeByDesc(rootNode, targetText.lowercase())
            descNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        }
    }

    fun clickButtonByColor(colorName: String): Boolean {
        // We search for buttons and try to match by color description
        val rootNode = rootInActiveWindow ?: return false
        val colorMap = mapOf(
            "blue" to listOf("blue", "neel", "neela"),
            "red" to listOf("red", "laal", "lal"),
            "green" to listOf("green", "sabz", "hara"),
            "ok" to listOf("ok", "confirm", "yes")
        )
        val keywords = colorMap[colorName.lowercase()] ?: listOf(colorName.lowercase())

        for (kw in keywords) {
            if (clickNodeWithText(kw)) return true
        }
        // Fallback: click first clickable button
        return clickFirstClickable(rootNode)
    }

    private fun clickFirstClickable(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.isEnabled) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickFirstClickable(child)) return true
        }
        return false
    }

    // ─── Type text into focused field ─────────────────────────────────────────

    fun typeText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val editNode = findFocusedEditText(rootNode) ?: findFirstEditText(rootNode) ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ─── Scroll ───────────────────────────────────────────────────────────────

    fun scrollDown() {
        val rootNode = rootInActiveWindow ?: return
        val scrollable = findScrollable(rootNode)
        scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun scrollUp() {
        val rootNode = rootInActiveWindow ?: return
        val scrollable = findScrollable(rootNode)
        scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    // ─── WhatsApp Automation ──────────────────────────────────────────────────

    fun sendWhatsAppMessage(phoneNumber: String, message: String, onDone: (Boolean) -> Unit) {
        Handler(Looper.getMainLooper()).post {
            // Open WhatsApp with pre-filled message via deep link
            try {
                val cleanPhone = phoneNumber.replace(Regex("[^\\d+]"), "")
                val encodedMsg = java.net.URLEncoder.encode(message, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://wa.me/$cleanPhone?text=$encodedMsg")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                applicationContext.startActivity(intent)

                // Wait for WhatsApp to open, then click Send
                Handler(Looper.getMainLooper()).postDelayed({
                    val sent = clickSendButton()
                    onDone(sent)
                }, 3500)
            } catch (e: Exception) {
                Log.e(TAG, "WhatsApp error: ${e.message}")
                onDone(false)
            }
        }
    }

    private fun clickSendButton(): Boolean {
        val sendTexts = listOf("send", "bhejo", "send message", "send_btn", "send button")
        for (text in sendTexts) {
            if (clickNodeWithText(text)) return true
        }
        // Try content description "Send"
        val rootNode = rootInActiveWindow ?: return false
        return findAndClickByDesc(rootNode, "send")
    }

    // ─── Helper finders ───────────────────────────────────────────────────────

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.lowercase() ?: ""
        if (nodeText.contains(text) && (node.isClickable || node.isEnabled)) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByText(child, text)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByDesc(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (nodeDesc.contains(desc) && node.isClickable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeByDesc(child, desc)
            if (found != null) return found
        }
        return null
    }

    private fun findAndClickByDesc(node: AccessibilityNodeInfo, desc: String): Boolean {
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (nodeDesc.contains(desc) && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickByDesc(child, desc)) return true
        }
        return false
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollable(child)
            if (found != null) return found
        }
        return null
    }

    private fun findFocusedEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.className?.contains("EditText") == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedEditText(child)
            if (found != null) return found
        }
        return null
    }

    private fun findFirstEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.contains("EditText") == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditText(child)
            if (found != null) return found
        }
        return null
    }
}
