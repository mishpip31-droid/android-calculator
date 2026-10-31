package com.example.calculator

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Плитка в шторке уведомлений: включить или выключить калькулятор поверх окон. */
class FloatingTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        if (FloatingCalculatorService.running) {
            FloatingCalculatorService.stop(this)
            qsTile?.let { it.state = Tile.STATE_INACTIVE; it.updateTile() }
            return
        }
        // Запуск через невидимый экран: так Android разрешает старт из шторки
        // и, если нужно, сразу открывает настройку разрешения.
        val intent = Intent(this, FloatingLauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun update() {
        val tile = qsTile ?: return
        tile.state = if (FloatingCalculatorService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    companion object {
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, FloatingTileService::class.java)) }
        }
    }
}

/** Невидимый экран-посредник: запускает окно или просит разрешение «Поверх других приложений». */
class FloatingLauncherActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (!FloatingCalculatorService.start(this)) {
            android.widget.Toast.makeText(this, R.string.floating_permission_toast, android.widget.Toast.LENGTH_LONG).show()
            startActivity(FloatingCalculatorService.permissionIntent(this))
        }
        finish()
    }
}
