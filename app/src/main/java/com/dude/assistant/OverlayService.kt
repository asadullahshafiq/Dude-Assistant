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
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var tts: TTSManager
    private lateinit var voiceManager: VoiceManager
    private lateinit var encryptionManager: EncryptionManager
    private lateinit var actionExecutor: ActionExecutor

    // Views
    private lateinit var orbImage: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvHeard: TextView
    private lateinit var tvResponse: TextView

    companion object {
        const val CHANNEL_ID = "dude_assistant_channel"
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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

        voiceManager = VoiceManager(
            context = this,
            onResult = { text -> handleSpeechResult(text) },
            onError = { msg -> updateStatus(msg) },
            onListening = { setOrbState("listening") }
        )

        // Start listening immediately
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            tts.speak("Ji haan! Bolo...")
            voiceManager.startListening()
        }, 600)
    }

    private fun setupOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_view, null)

        orbImage = overlayView.findViewById(R.id.orb_image)
        tvStatus = overlayView.findViewById(R.id.tv_status)
        tvHeard = overlayView.findViewById(R.id.tv_heard)
        tvResponse = overlayView.findViewById(R.id.tv_response)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(overlayView, params)

        // Start pulse animation
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        orbImage.startAnimation(pulse)

        // Tap orb to restart listening
        overlayView.findViewById<View>(R.id.orb_container).setOnClickListener {
            voiceManager.stopListening()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                voiceManager.startListening()
            }, 300)
        }

        // Tap overlay background to dismiss
        overlayView.setOnLongClickListener {
            stopSelf()
            true
        }
    }

    private fun handleSpeechResult(text: String) {
        tvHeard.text = text
        tvHeard.visibility = View.VISIBLE

        setOrbState("thinking")
        updateStatus("Samajh raha hun...")

        val cmd = CommandParser.parse(text, encryptionManager)
        showResponse("Kaam kar raha hun...")

        actionExecutor.execute(cmd)

        // After action, if not farewell, listen again
        if (cmd.action != CommandParser.ActionType.FAREWELL) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                setOrbState("listening")
                updateStatus("Sun raha hoon...")
                voiceManager.startListening()
            }, 4000)
        }
    }

    private fun setOrbState(state: String) {
        orbImage.clearAnimation()
        when (state) {
            "listening" -> {
                orbImage.setImageResource(R.drawable.orb_listening)
                tvStatus.text = "Sun raha hoon..."
                tvStatus.setTextColor(resources.getColor(R.color.accent, null))
            }
            "thinking" -> {
                orbImage.setImageResource(R.drawable.orb_idle)
                tvStatus.text = "Soch raha hun..."
                tvStatus.setTextColor(resources.getColor(R.color.accent_pink, null))
            }
            "speaking" -> {
                orbImage.setImageResource(R.drawable.orb_idle)
                tvStatus.text = "Bol raha hun..."
                tvStatus.setTextColor(resources.getColor(R.color.success, null))
            }
            else -> {
                orbImage.setImageResource(R.drawable.orb_idle)
            }
        }
        val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
        orbImage.startAnimation(pulse)
    }

    private fun updateStatus(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            tvStatus.text = msg
        }
    }

    private fun showResponse(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            tvResponse.text = msg
            tvResponse.visibility = View.VISIBLE
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Dude Assistant", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Dude Assistant running"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dude Assistant")
            .setContentText("Sun raha hun... \"Bye Dude\" bol kar band karein")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.destroy()
        tts.shutdown()
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) { /* ignore */ }
    }
}
