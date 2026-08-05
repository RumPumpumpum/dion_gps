package com.dion.gpsmock

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoOffMinutes: Int
        get() = prefs.getInt(KEY_AUTO_OFF_MINUTES, AUTO_OFF_DISABLED)
        set(value) = prefs.edit().putInt(KEY_AUTO_OFF_MINUTES, value).apply()

    /**
     * 자동 종료 예정 시각 (SystemClock.elapsedRealtime 기준, 밀리초).
     * 0이면 자동 종료 예약이 없다는 뜻.
     */
    var autoOffEndRealtime: Long
        get() = prefs.getLong(KEY_AUTO_OFF_END, 0L)
        set(value) = prefs.edit().putLong(KEY_AUTO_OFF_END, value).apply()

    companion object {
        private const val PREFS_NAME = "dion_gps_mock_prefs"
        private const val KEY_AUTO_OFF_MINUTES = "auto_off_minutes"
        private const val KEY_AUTO_OFF_END = "auto_off_end_realtime"

        const val AUTO_OFF_DISABLED = 0
        val AUTO_OFF_OPTIONS = listOf(
            AUTO_OFF_DISABLED to "사용 안 함",
            5 to "5분",
            10 to "10분",
            15 to "15분",
            30 to "30분",
            60 to "1시간"
        )

        /** 프리셋에 없는 값(사용자 정의 포함)까지 사람이 읽을 수 있는 라벨로 변환한다. */
        fun labelForMinutes(minutes: Int): String {
            if (minutes <= 0) return "사용 안 함"
            AUTO_OFF_OPTIONS.firstOrNull { it.first == minutes }?.let { return it.second }
            val hours = minutes / 60
            val mins = minutes % 60
            return when {
                hours > 0 && mins > 0 -> "${hours}시간 ${mins}분"
                hours > 0 -> "${hours}시간"
                else -> "${mins}분"
            }
        }
    }
}
