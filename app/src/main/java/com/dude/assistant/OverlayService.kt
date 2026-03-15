package com.dude.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
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
    private lateinit var params: WindowManager.LayoutParams

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

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(overlayView, params)

        setupDrag()
        setupInput()

        // Close button
        overlayView.findViewById<TextView>(R.id.btn_close).setOnClickListener {
            stopSelf()
        }
    }

    // ── Drag Support ──────────────────────────────────────────
    private fun setupDrag() {
        val dragHandle = overlayView.findViewById<View>(R.id.drag_handle)
        var startX = 0f; var startY = 0f
        var startParamX = 0; var startParamY = 0
        var isDragging = false

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startParamX = params.x
                    startParamY = params.y
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                        isDragging = true
                        params.x = (startParamX + dx).toInt()
                        params.y = (startParamY + dy).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> isDragging
                else -> false
            }
        }
    }

    // ── Text Input ────────────────────────────────────────────
    private fun setupInput() {
        overlayView.findViewById<TextView>(R.id.btn_send).setOnClickListener {
            runCommand()
        }

        etCommand.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE) {
                runCommand(); true
            } else false
        }
    }

    private fun runCommand() {
        val text = etCommand.text.toString().trim()
        if (text.isEmpty()) return

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etCommand.windowToken, 0)
        etCommand.text.clear()

        executeCommand(text)
    }

    private fun executeCommand(text: String) {
        updateStatus("running_")
        updateResponse("> $text")

        val cmd = CommandParser.parse(text, encryptionManager)
        actionExecutor.execute(cmd)

        Handler(Looper.getMainLooper()).postDelayed({
            updateStatus("ready_")
        }, 3000)
    }

    fun updateResponse(msg: String) {
        Handler(Looper.getMainLooper()).post {
            tvResponse.text = msg
        }
    }

    private fun updateStatus(msg: String) {
        Handler(Looper.getMainLooper()).post {
            tvStatus.text = msg
        }
    }

    // ── App Open Fix ──────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Dude Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dude Assistant Active")
            .setContentText("X dabao band karne ke liye")
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
