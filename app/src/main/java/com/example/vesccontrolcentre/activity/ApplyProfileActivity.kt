package com.example.vesccontrolcentre.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.vesccontrolcentre.R
import com.example.vesccontrolcentre.model.ProfileType
import com.example.vesccontrolcentre.service.VescService
import com.example.vesccontrolcentre.widget.BaseProfileWidgetProvider

class ApplyProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileKey = intent.getStringExtra("PROFILE")

        if (profileKey != null) {
            val profileType = ProfileType.fromKey(profileKey)

            if (profileType != null) {
                VescService.applyProfile(applicationContext, profileType)

                val prefs = getSharedPreferences("vesc_prefs", MODE_PRIVATE)
                prefs.edit().putString("active_profile", profileType.key).apply()

                BaseProfileWidgetProvider.updateAllWidgets(applicationContext)
                updateActiveShortcutIcon(this, profileType.key)

                Toast.makeText(this, "${profileType.defaultTitle} Profile Applied", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Error: Unknown profile $profileKey", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Error: No profile specified", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun updateActiveShortcutIcon(context: Context, activeProfileKey: String) {
        val allProfiles = ProfileType.entries

        val updatedShortcuts = allProfiles.map { profile ->
            val isActive = (profile.key == activeProfileKey)

            val iconResource = if (isActive) {
                R.mipmap.ic_launcher_round
            } else {
                R.mipmap.ic_launcher
            }

            val intent = Intent(context, ApplyProfileActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("PROFILE", profile.key)
            }

            ShortcutInfoCompat.Builder(context, profile.key)
                .setShortLabel(profile.defaultTitle)
                .setLongLabel("Apply ${profile.defaultTitle} profile to VESC")
                .setIcon(IconCompat.createWithResource(context, iconResource))
                .setIntent(intent)
                .build()
        }

        ShortcutManagerCompat.updateShortcuts(context, updatedShortcuts)
    }
}
