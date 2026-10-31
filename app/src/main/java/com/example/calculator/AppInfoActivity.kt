package com.example.calculator

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** О приложении: версия, разработчик и рекламный блок с кнопкой «Поделиться». */
class AppInfoActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_info)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        findViewById<TextView>(R.id.appVersion).text = version?.let { getString(R.string.about_version, it) } ?: ""

        // Жирные названия возможностей в рекламном блоке.
        renderBold(findViewById(android.R.id.content))

        findViewById<View>(R.id.btnShareApp).setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app_text))
            startActivity(Intent.createChooser(send, getString(R.string.share_app)))
        }
    }

    private fun renderBold(view: View) {
        if (view is TextView && view.text.contains("<b>")) {
            view.text = Html.fromHtml(view.text.toString(), Html.FROM_HTML_MODE_COMPACT)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) renderBold(view.getChildAt(i))
        }
    }
}
