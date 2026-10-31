package com.example.calculator

import android.os.Bundle
import android.view.View

/** Инструкция по приложению. */
class AboutActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }
}
