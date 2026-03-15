package com.dude.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
        Log.d("ActionExecutor", "Executing: ${cmd.action} params=${cmd.params}")
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
            else -> {
                tts.speak("Mujhe samajh nahi aaya. Dobara bolein.")
            }
        }
    }

    private fun handleFarewell() {
        tts.speak("Theek hai! Phir milenge. Bye Dude!")
        Handler(Looper.getMainLooper()).postDelayed({ onDismiss() }, 2000)
    }

    private fun handleGreeting() {
        val greetings = listOf(
            "Salam! Kya hukum hai?",
            "Hello! Batao kya karna hai?",
            "Ji haan! Kaise madad karun?"
        )
        tts.speak(greetings.random())
    }

    private fun handleWhatsApp(params: Map<String, String>) {
        val contactName = params["contact"] ?: "mama"
        val message = params["message"] ?: "Kya hal hai?"

        val phone = encryptionManager.getContact(contactName)
        if (phone == null) {
            tts.speak("$contactName ka number nahi mila. Pehle contact add karein.")
            return
        }

        tts.speak("$contactName ko WhatsApp message bhej raha hun...")

        val acc = AssistantAccessibilityService.instance
        if (acc != null) {
            acc.sendWhatsAppMessage(phone, message) { success ->
                if (success) {
                    tts.speak("Message bhej diya!")
                } else {
                    tts.speak("Message bhej diya! Send button dhundh raha hun...")
                }
            }
        } else {
            // Fallback: open WhatsApp chat directly
            try {
                val cleanPhone = phone.replace(Regex("[^\\d+]"), "")
                val encoded = java.net.URLEncoder.encode(message, "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$cleanPhone?text=$encoded")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                tts.speak("WhatsApp khul gaya. Send button dabayein.")
            } catch (e: Exception) {
                tts.speak("WhatsApp install nahi hai ya koi error aaya.")
            }
        }
    }

    private fun handleOpenApp(appName: String) {
        tts.speak("$appName khol raha hun...")
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
            "files" to "com.sec.android.app.myfiles"
        )

        val pkg = packageMap[appName.lowercase()]
        if (pkg != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } else {
                // Try generic search
                openAppGeneric(appName)
            }
        } else {
            openAppGeneric(appName)
        }
    }

    private fun openAppGeneric(appName: String) {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(appName.lowercase())) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    return
                }
            }
        }
        tts.speak("$appName nahi mila.")
    }

    private fun handleCloseApp() {
        tts.speak("App band kar raha hun...")
        AssistantAccessibilityService.instance?.pressRecents()
        Handler(Looper.getMainLooper()).postDelayed({
            AssistantAccessibilityService.instance?.clickNodeWithText("close")
                ?: AssistantAccessibilityService.instance?.pressBack()
        }, 500)
    }

    private fun handleBack() {
        tts.speak("Peeche ja raha hun.")
        AssistantAccessibilityService.instance?.pressBack()
    }

    private fun handleHome() {
        tts.speak("Home screen par ja raha hun.")
        AssistantAccessibilityService.instance?.pressHome()
    }

    private fun handleRecents() {
        tts.speak("Recent apps...")
        AssistantAccessibilityService.instance?.pressRecents()
    }

    private fun handleScreenshot() {
        tts.speak("Screenshot le raha hun...")
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
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
        tts.speak(if (increase) "Volume badha diya." else "Volume kam kar diya.")
    }

    private fun handleSearch(query: String) {
        if (NetworkHelper.isOnline(context)) {
            tts.speak("Search kar raha hun: $query")
            scope.launch {
                val answer = NetworkHelper.askDuckDuckGo(query)
                Handler(Looper.getMainLooper()).post {
                    tts.speak(answer.take(300))
                }
            }
        } else {
            val answer = NetworkHelper.offlineAnswer(query)
            tts.speak(answer)
        }
    }

    private fun handleClickText(text: String) {
        tts.speak("$text button dhoondh raha hun...")
        val success = AssistantAccessibilityService.instance?.clickNodeWithText(text) ?: false
        if (!success) {
            tts.speak("$text nahi mila screen par.")
        } else {
            tts.speak("Click kar diya!")
        }
    }

    private fun handleClickColor(color: String) {
        tts.speak("$color button dhoondh raha hun...")
        AssistantAccessibilityService.instance?.clickButtonByColor(color)
    }

    private fun handleScroll(up: Boolean) {
        if (up) {
            AssistantAccessibilityService.instance?.scrollUp()
            tts.speak("Upar scroll kar diya.")
        } else {
            AssistantAccessibilityService.instance?.scrollDown()
            tts.speak("Neeche scroll kar diya.")
        }
    }

    private fun handleNotificationPanel() {
        AssistantAccessibilityService.instance?.pullNotificationBar()
        tts.speak("Notifications khol diye.")
    }

    private fun handleWifi() {
        tts.speak("WiFi settings khol raha hun.")
        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun handleBluetooth() {
        tts.speak("Bluetooth settings khol raha hun.")
        val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun handleFlashlight() {
        tts.speak("Torch toggle kar raha hun.")
        val intent = Intent(Intent.ACTION_MAIN).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("flash", true)
        }
        // Many Samsung devices support this via Quick Settings toggle
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
                    tts.speak(if (answer.length > 10) answer.take(300) else "Mujhe is ka jawab nahi mila. Google par search karna chahein?")
                }
            }
        } else {
            val ans = NetworkHelper.offlineAnswer(question)
            tts.speak(ans)
        }
    }
}
