package com.dude.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionExecutor(
    private val context: Context,
    private val encryptionManager: EncryptionManager,
    private val tts: TTSManager,
    private val onDismiss: () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    fun execute(cmd: CommandParser.ParsedCommand) {
        when (cmd.action) {
            CommandParser.ActionType.FAREWELL -> handleFarewell()
            CommandParser.ActionType.GREETING -> handleGreeting()
            CommandParser.ActionType.WHATSAPP_MESSAGE -> handleWhatsApp(cmd.params)
            CommandParser.ActionType.OPEN_APP -> handleOpenApp(cmd.params["app"] ?: "")
            CommandParser.ActionType.CLOSE_APP -> handleCloseApp()
            CommandParser.ActionType.BACK -> handleBack()
            CommandParser.ActionType.HOME -> handleHome()
            CommandParser.ActionType.RECENT_APPS -> handleRecents()
            CommandParser.ActionType.SCREENSHOT -> handleScreenshot()
            CommandParser.ActionType.CALL -> handleCall(cmd.params["contact"] ?: "")
            CommandParser.ActionType.VOLUME_SET -> handleVolumeSet(cmd.params["level"]?.toIntOrNull() ?: 5)
            CommandParser.ActionType.VOLUME_UP -> handleVolume(true)
            CommandParser.ActionType.VOLUME_DOWN -> handleVolume(false)
            CommandParser.ActionType.SEARCH_WEB -> handleSearch(cmd.params["query"] ?: cmd.rawText)
            CommandParser.ActionType.CLICK_TEXT -> handleClickText(cmd.params["text"] ?: "")
            CommandParser.ActionType.CLICK_COLOR -> handleClickColor(cmd.params["color"] ?: "")
            CommandParser.ActionType.SCROLL_UP -> handleScroll(up = true)
            CommandParser.ActionType.SCROLL_DOWN -> handleScroll(up = false)
            CommandParser.ActionType.NOTIFICATION_PANEL -> handleNotificationPanel()
            CommandParser.ActionType.WIFI_TOGGLE -> handleWifi()
            CommandParser.ActionType.BLUETOOTH_TOGGLE -> handleBluetooth()
            CommandParser.ActionType.FLASHLIGHT_TOGGLE -> handleFlashlight()
            CommandParser.ActionType.ANSWER_QUESTION -> handleQuestion(cmd.params["q"] ?: cmd.rawText)
            else -> tts.speak("Samajh nahi aaya. Dobara likho.")
        }
    }

    private fun handleFarewell() {
        tts.speak("Bye! Phir milenge.")
        Handler(Looper.getMainLooper()).postDelayed({ onDismiss() }, 1500)
    }

    private fun handleGreeting() {
        tts.speak("Salam! Kya karna hai?")
    }

    private fun handleWhatsApp(params: Map<String, String>) {
        val contactName = params["contact"] ?: "mama"
        val message = params["message"] ?: "Kya hal hai?"
        val phone = encryptionManager.getContact(contactName)
        if (phone == null) {
            tts.speak("$contactName ka number nahi mila. App mein add karein.")
            return
        }
        tts.speak("$contactName ko message bhej raha hun...")
        try {
            val cleanPhone = phone.replace(Regex("[^\\d+]"), "")
            val encoded = java.net.URLEncoder.encode(message, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$cleanPhone?text=$encoded")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            tts.speak("WhatsApp nahi khula.")
        }
    }

    private fun handleOpenApp(appName: String) {
        tts.speak("$appName khol raha hun...")
        val app = appName.lowercase().trim()

        val packageMap = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "facebook" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "camera" to "com.sec.android.app.camera",
            "gallery" to "com.sec.android.gallery3d",
            "settings" to "com.android.settings",
            "maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "tiktok" to "com.zhiliaoapp.musically",
            "telegram" to "org.telegram.messenger",
            "gmail" to "com.google.android.gm",
            "calculator" to "com.sec.android.app.popupcalculator",
            "clock" to "com.sec.android.app.clockpackage",
            "calendar" to "com.samsung.android.calendar",
            "play store" to "com.android.vending",
            "files" to "com.sec.android.app.myfiles",
            "phone" to "com.samsung.android.dialer",
            "messages" to "com.samsung.android.messaging",
            "contacts" to "com.samsung.android.contacts",
            "browser" to "com.android.browser"
        )

        val pkg = packageMap[app]
        val pm = context.packageManager

        if (pkg != null) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
                context.startActivity(intent)
                return
            }
        }

        // Package na mile to naam se dhundo
        val installedApps = pm.getInstalledApplications(0)
        for (info in installedApps) {
            val label = pm.getApplicationLabel(info).toString().lowercase()
            if (label.contains(app)) {
                val intent = pm.getLaunchIntentForPackage(info.packageName)
                if (intent != null) {
                    intent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                    context.startActivity(intent)
                    return
                }
            }
        }
        tts.speak("$appName nahi mila.")
    }

    private fun handleCloseApp() {
        AssistantAccessibilityService.instance?.pressRecents()
        Handler(Looper.getMainLooper()).postDelayed({
            AssistantAccessibilityService.instance?.clickNodeWithText("close")
        }, 600)
    }

    private fun handleBack() {
        AssistantAccessibilityService.instance?.pressBack()
    }

    private fun handleHome() {
        AssistantAccessibilityService.instance?.pressHome()
    }

    private fun handleRecents() {
        AssistantAccessibilityService.instance?.pressRecents()
    }

    private fun handleScreenshot() {
        AssistantAccessibilityService.instance?.takeScreenshot()
    }

    private fun handleCall(contactName: String) {
        val phone = encryptionManager.getContact(contactName)
        if (phone == null) {
            tts.speak("$contactName ka number nahi mila.")
            return
        }
        tts.speak("$contactName ko call kar raha hun...")
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun handleVolumeSet(level: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val vol = ((level.coerceIn(0, 10).toFloat() / 10f) * max).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
        tts.speak("Volume $level kar diya.")
    }

    private fun handleVolume(increase: Boolean) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val dir = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
    }

    private fun handleSearch(query: String) {
        if (NetworkHelper.isOnline(context)) {
            tts.speak("Search kar raha hun...")
            scope.launch {
                val answer = NetworkHelper.askDuckDuckGo(query)
                Handler(Looper.getMainLooper()).post {
                    tts.speak(answer.take(300))
                }
            }
        } else {
            val ans = NetworkHelper.offlineAnswer(query)
            tts.speak(ans)
        }
    }

    private fun handleClickText(text: String) {
        val success = AssistantAccessibilityService.instance?.clickNodeWithText(text) ?: false
        if (!success) tts.speak("$text nahi mila.")
    }

    private fun handleClickColor(color: String) {
        AssistantAccessibilityService.instance?.clickButtonByColor(color)
    }

    private fun handleScroll(up: Boolean) {
        if (up) AssistantAccessibilityService.instance?.scrollUp()
        else AssistantAccessibilityService.instance?.scrollDown()
    }

    private fun handleNotificationPanel() {
        AssistantAccessibilityService.instance?.pullNotificationBar()
    }

    private fun handleWifi() {
        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun handleBluetooth() {
        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun handleFlashlight() {
        AssistantAccessibilityService.instance?.pullNotificationBar()
        Handler(Looper.getMainLooper()).postDelayed({
            AssistantAccessibilityService.instance?.clickNodeWithText("flashlight")
                ?: AssistantAccessibilityService.instance?.clickNodeWithText("torch")
        }, 800)
    }

    private fun handleQuestion(question: String) {
        if (NetworkHelper.isOnline(context)) {
            tts.speak("Jawab dhoondh raha hun...")
            scope.launch {
                val answer = NetworkHelper.askDuckDuckGo(question)
                Handler(Looper.getMainLooper()).post {
                    tts.speak(
                        if (answer.length > 10) answer.take(300)
                        else "Jawab nahi mila."
                    )
                }
            }
        } else {
            tts.speak(NetworkHelper.offlineAnswer(question))
        }
    }
}
