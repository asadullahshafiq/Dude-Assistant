package com.dude.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var encryptionManager: EncryptionManager

    // Permission buttons
    private lateinit var btnMic: TextView
    private lateinit var btnOverlay: TextView
    private lateinit var btnAccessibility: TextView
    private lateinit var btnDefault: TextView

    // Contact fields
    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnAddContact: TextView
    private lateinit var contactsList: LinearLayout
    private lateinit var btnTest: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        encryptionManager = EncryptionManager(this)

        bindViews()
        setupButtons()
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBadges()
    }

    private fun bindViews() {
        btnMic = findViewById(R.id.btn_mic)
        btnOverlay = findViewById(R.id.btn_overlay)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnDefault = findViewById(R.id.btn_default)
        etName = findViewById(R.id.et_contact_name)
        etPhone = findViewById(R.id.et_contact_phone)
        btnAddContact = findViewById(R.id.btn_add_contact)
        contactsList = findViewById(R.id.contacts_list)
        btnTest = findViewById(R.id.btn_test)
    }

    private fun setupButtons() {
        btnMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE), 100
                )
            } else {
                toast("✓ Microphone already enabled!")
            }
        }

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 101)
            } else {
                toast("✓ Overlay already enabled!")
            }
        }

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            toast("'Dude Assistant' dhundh kar enable karein")
        }

        btnDefault.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (e2: Exception) {
                    toast("Settings > Apps > Default Apps > Digital Assistant > Dude")
                }
            }
        }

        btnAddContact.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isEmpty() || phone.isEmpty()) {
                toast("Naam aur phone number dono bharein!")
                return@setOnClickListener
            }
            encryptionManager.saveContact(name, phone)
            etName.text.clear()
            etPhone.text.clear()
            loadContacts()
            toast("✓ $name ka number save ho gaya (Encrypted)")
        }

        btnTest.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                toast("Pehle Overlay permission enable karein!")
                return@setOnClickListener
            }
            OverlayService.start(this)
        }
    }

    private fun loadContacts() {
        contactsList.removeAllViews()
        val contacts = encryptionManager.getAllContacts()

        if (contacts.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Abhi koi contact nahi. Upar se add karein."
                textSize = 12f
                setTextColor(resources.getColor(R.color.text_hint, null))
                setPadding(0, 8, 0, 8)
            }
            contactsList.addView(empty)
            return
        }

        contacts.forEach { (name, phone) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 6, 0, 6)
            }

            val tv = TextView(this).apply {
                text = "📱  $name  •  $phone"
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val del = TextView(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(resources.getColor(R.color.error, null))
                setPadding(16, 0, 0, 0)
                setOnClickListener {
                    encryptionManager.deleteContact(name)
                    loadContacts()
                    toast("$name delete ho gaya")
                }
            }

            row.addView(tv)
            row.addView(del)
            contactsList.addView(row)

            // Divider
            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.topMargin = 4; it.bottomMargin = 4 }
                setBackgroundColor(resources.getColor(R.color.text_hint, null))
                alpha = 0.3f
            }
            contactsList.addView(divider)
        }
    }

    private fun updatePermissionBadges() {
        val micOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlayOk = Settings.canDrawOverlays(this)
        val accOk = AssistantAccessibilityService.instance != null

        if (micOk) {
            btnMic.text = "✓ Done"
            btnMic.setBackgroundResource(R.drawable.card_bg)
        }
        if (overlayOk) {
            btnOverlay.text = "✓ Done"
            btnOverlay.setBackgroundResource(R.drawable.card_bg)
        }
        if (accOk) {
            btnAccessibility.text = "✓ Done"
            btnAccessibility.setBackgroundResource(R.drawable.card_bg)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionBadges()
    }
}
