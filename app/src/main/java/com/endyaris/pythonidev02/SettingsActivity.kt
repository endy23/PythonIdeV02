package com.endyaris.pythonidev02


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import android.widget.EditText
import android.widget.Button

class SettingsActivity : AppCompatActivity() {
    private lateinit var securePrefs: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        securePrefs = SecurePreferences(this)
        
        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val saveButton = findViewById<Button>(R.id.saveButton)

        // Load existing values
        securePrefs.getApiKey()?.let { apiKeyInput.setText(it) }
        securePrefs.getUsername()?.let { usernameInput.setText(it) }

        saveButton.setOnClickListener {
            val apiKey = apiKeyInput.text.toString()
            val username = usernameInput.text.toString()
            
            if (apiKey.isBlank() || username.isBlank()) {
                Toast.makeText(this, "Both fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            securePrefs.saveApiKey(apiKey)
            securePrefs.saveUsername(username)
            Toast.makeText(this, "Credentials saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}