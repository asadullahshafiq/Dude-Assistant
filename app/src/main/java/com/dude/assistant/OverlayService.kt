package com.dude.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var tts: TTSManager
    private lateinit var encryptionManager: EncryptionManager
    private lateinit var actionExecutor: ActionExecutor

    private lateinit var tvStatus: TextView
    private lateinit var tvResponse: TextView
    private lateinit var etCommand: EditText

    companion object {
        const val CHANNEL_ID = "dude_channel"
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(intent)
            else
                context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        encryptionManager = EncryptionManager(this)
        tts = TTSManager(this)
        actionExecutor = ActionExecutor(
            context = this,
            encryptionManager = encryptionManager,
            tts = tts,
            onDismiss = { stopSelf() }
        )

        setupOverlay()
        tts.speak("Dude ready hai. Command dein.")
    }

    private fun setupOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_view, null)

        tvStatus = overlayView.findViewById(R.id.tv_status)
        tvResponse = overlayView.findViewById(R.id.tv_response)
        etCommand = overlayView.findViewById(R.id.et_command)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(overlayView, params)

        setupButtons()
        setupTextInput()
    }

    private fun setupButtons() {
        // Close
        overlayView.findViewById<TextView>(R.id.btn_close).setOnClickListener {
            tts.speak("Bye Dude!")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopSelf()
            }, 1500)
        }

        // WhatsApp
        overlayView.findViewById<TextView>(R.id.btn_whatsapp).setOnClickListener {
            showPrompt("mama ko WhatsApp message karo ke kya hal hai")
        }

        // YouTube
        overlayView.findViewById<TextView>(R.id.btn_youtube).setOnClickListener {
            executeCommand("youtube kholo")
        }

        // Camera
        overlayView.findViewById<TextView>(R.id.btn_camera).setOnClickListener {
            executeCommand("camera kholo")
        }

        // Call
        overlayView.findViewById<TextView>(R.id.btn_call).setOnClickListener {
            showPrompt("mama ko call karo")
        }

        // Screenshot
        overlayView.findViewById<TextView>(R.id.btn_screenshot).setOnClickListener {
            executeCommand("screenshot lo")
        }

        // Back
        overlayView.findViewById<TextView>(R.id.btn_back).setOnClickListener {
            executeCommand("wapas jao")
        }

        // Volume Up
        overlayView.findViewById<TextView>(R.id.btn_vol_up).setOnClickListener {
            executeCommand("volume badhao")
        }

        // Volume Down
        overlayView.findViewById<TextView>(R.id.btn_vol_down).setOnClickListener {
            executeCommand("volume kam karo")
        }

        // Torch
        overlayView.findViewById<TextView>(R.id.btn_torch).setOnClickListener {
            executeCommand("torch on karo")
        }

        // Send button
        overlayView.findViewById<TextView>(R.id.btn_send).setOnClickListener {
            runCommand()
        }
    }

    private fun setupTextInput() {
        // Enter key se bhi run ho
        etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE) {
                runCommand()
                true
            } else false
        }
    }

    private fun showPrompt(text: String) {
        etCommand.setText(text)
        etCommand.setSelection(text.length)
        etCommand.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etCommand, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun runCommand() {
        val text = etCommand.text.toString().trim()
        if (text.isEmpty()) return

        // Keyboard band karo
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etCommand.windowToken, 0)

        etCommand.text.clear()
        executeCommand(text)
    }

    private fun executeCommand(text: String) {
        updateStatus("> $text")
        updateResponse("> processing...")

        val cmd = CommandParser.parse(text, encryptionManager)
        actionExecutor.execute(cmd)

        // Response update
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateStatus("> READY... awaiting command_")
        }, 3000)
    }

    private fun updateStatus(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            tvStatus.text = msg
        }
    }

    private fun updateResponse(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            tvResponse.text = msg
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Dude Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dude Assistant")
            .setContentText("Active hai — X dabao band karne ke liye")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        try { windowManager.removeView(overlayView) } catch (e: Exception) {}
    }
}
