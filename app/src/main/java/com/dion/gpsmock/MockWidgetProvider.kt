package com.dion.gpsmock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews

class MockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_TOGGLE -> {
                handleToggle(context)
                updateAllWidgets(context)
            }
            MockLocationService.ACTION_STATE_CHANGED -> updateAllWidgets(context)
        }
    }

    private fun handleToggle(context: Context) {
        if (MockLocationService.isRunning(context)) {
            MockLocationService.stop(context)
            return
        }

        val helper = MockLocationHelper(context)
        if (!helper.hasLocationPermission() || !helper.isMockLocationAppSelected()) {
            // 권한/모의 위치 앱 설정이 안 된 상태면 앱을 열어 안내한다.
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }

        MockLocationService.start(context)
    }

    companion object {
        const val ACTION_WIDGET_TOGGLE = "com.dion.gpsmock.action.WIDGET_TOGGLE"

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MockWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val isRunning = MockLocationService.isRunning(context)
            val views = RemoteViews(context.packageName, R.layout.widget_mock)

            // 1x1처럼 좁을 때는 상태 텍스트를 숨겨 공간을 확보한다.
            val minWidthDp = manager.getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val showStatusText = minWidthDp >= 100
            views.setViewVisibility(
                R.id.widgetStatus,
                if (showStatusText) View.VISIBLE else View.GONE
            )

            views.setTextViewText(
                R.id.widgetStatus,
                context.getString(if (isRunning) R.string.widget_on else R.string.widget_off)
            )
            views.setInt(
                R.id.widgetRoot,
                "setBackgroundResource",
                if (isRunning) R.drawable.widget_bg_on else R.drawable.widget_bg_off
            )

            // 자동 종료 예약이 있으면 크기와 관계없이 남은 시간을 카운트다운으로 표시한다.
            val endRealtime = AppPreferences(context).autoOffEndRealtime
            val showTimer = isRunning && endRealtime > 0
            if (showTimer) {
                views.setChronometer(R.id.widgetTimer, endRealtime, "%s", true)
                views.setChronometerCountDown(R.id.widgetTimer, true)
                views.setViewVisibility(R.id.widgetTimer, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widgetTimer, View.GONE)
            }

            val toggleIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, MockWidgetProvider::class.java)
                    .setAction(ACTION_WIDGET_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, toggleIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
