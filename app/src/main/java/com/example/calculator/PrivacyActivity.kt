package com.example.calculator

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView

/** Политика конфиденциальности (текст — res/raw/privacy.txt, копия — PRIVACY.md в репозитории). */
class PrivacyActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.privacyText).text =
            resources.openRawResource(R.raw.privacy).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
