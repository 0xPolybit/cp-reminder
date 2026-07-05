package com.example.cpreminder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Invisible entry point. Decides where the user lands on launch:
 *  - If a username is already stored locally -> [MainActivity].
 *  - Otherwise -> [UsernameActivity] to capture and verify one.
 */
class LauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = if (UserPrefs.hasUsername(this)) {
            MainActivity::class.java
        } else {
            UsernameActivity::class.java
        }

        startActivity(Intent(this, target))
        finish()
    }
}
