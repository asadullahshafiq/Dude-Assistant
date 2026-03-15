package com.dude.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/**
 * Yeh activity Home button long press pe launch hoti hai.
 * Seedha OverlayService start karti hai aur apne aap finish ho jaati hai.
 */
class AssistActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Transparent – koi UI nahi dikhani
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)

        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            // Redirect to main setup
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
            return
        }

        // Check mic permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), 101
            )
            return
        }

        launchOverlay()
    }

    private fun launchOverlay() {
        OverlayService.start(this)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchOverlay()
        } else {
            finish()
        }
    }
}
