package com.example.calculator

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView

/** Инструкция по приложению и информация о разработчике. */
class AboutActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAppearance).setOnClickListener { ThemeManager.showPicker(this) }
        findViewById<View>(R.id.btnPrivacy).setOnClickListener {
            startActivity(android.content.Intent(this, PrivacyActivity::class.java))
        }

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
        findViewById<TextView>(R.id.aboutVersion).text =
            version?.let { getString(R.string.about_version, it) } ?: ""
    }
}
