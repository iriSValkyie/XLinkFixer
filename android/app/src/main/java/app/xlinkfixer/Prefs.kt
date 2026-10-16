package app.xlinkfixer

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Prefs {
    private const val NAME = "xlinkfixer"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TOAST = "toast"
    private const val KEY_AGGRESSIVE = "aggressive"
    private const val KEY_TARGET = "target"
    private const val KEY_LOG = "log"
    private const val LOG_LINES = 40

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = sp(ctx).getBoolean(KEY_ENABLED, true)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_ENABLED, v).apply()

    fun toast(ctx: Context) = sp(ctx).getBoolean(KEY_TOAST, true)
    fun setToast(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_TOAST, v).apply()

    fun aggressive(ctx: Context) = sp(ctx).getBoolean(KEY_AGGRESSIVE, false)
    fun setAggressive(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean(KEY_AGGRESSIVE, v).apply()

    fun target(ctx: Context): String {
        val t = sp(ctx).getString(KEY_TARGET, null)
        return if (t != null && t in Rewriter.TARGETS) t else Rewriter.TARGETS[0]
    }
    fun setTarget(ctx: Context, v: String) = sp(ctx).edit().putString(KEY_TARGET, v).apply()

    fun log(ctx: Context, line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val lines = (sp(ctx).getString(KEY_LOG, "") ?: "")
            .split('\n').filter { it.isNotBlank() }
            .toMutableList()
        lines.add(0, "$time  $line")
        while (lines.size > LOG_LINES) lines.removeAt(lines.size - 1)
        sp(ctx).edit().putString(KEY_LOG, lines.joinToString("\n")).apply()
    }

    fun logText(ctx: Context): String = sp(ctx).getString(KEY_LOG, "") ?: ""
    fun clearLog(ctx: Context) = sp(ctx).edit().remove(KEY_LOG).apply()
}
