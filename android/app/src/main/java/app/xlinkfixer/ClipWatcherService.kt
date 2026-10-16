package app.xlinkfixer

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * 「コピーが起きた瞬間」をアクセシビリティイベントから推定する。
 *
 *  - Android 13+: コピーすると SystemUI がクリップボードのプレビューを出し、
 *    「クリップボードにコピーしました」を announce する → TYPE_ANNOUNCEMENT
 *  - Android 12 以前 / 各アプリ独自: 「リンクをコピーしました」等のトースト
 *    → TYPE_NOTIFICATION_STATE_CHANGED
 *  - 共有シート内の「リンクをコピー」ボタンのタップ → TYPE_VIEW_CLICKED
 *  - 保険: SystemUI のウィンドウにタイトル "clipboard" を含むものが現れた
 *
 * どれかに当たったら少し待ってから ClipboardGrabber で読みに行く。
 */
class ClipWatcherService : AccessibilityService() {

    companion object {
        private const val DELAY_MS = 350L
        private const val COOLDOWN_MS = 600L
        private const val SYSTEMUI = "com.android.systemui"
        private val KEYWORD = Regex("コピー|copied|copy|clipboard|クリップボード", RegexOption.IGNORE_CASE)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastTrigger = 0L
    private var pending = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Prefs.log(this, "サービス開始")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!Prefs.enabled(this)) return

        val pkg = event.packageName?.toString() ?: ""
        if (pkg == packageName) return  // 自分自身のトースト等は無視

        val hit = when (event.eventType) {
            AccessibilityEvent.TYPE_ANNOUNCEMENT,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                KEYWORD.containsMatchIn(textOf(event)) ||
                    (Prefs.aggressive(this) && pkg == SYSTEMUI)
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                (Prefs.aggressive(this) && pkg == SYSTEMUI) || hasClipboardWindow()
            }
            else -> false
        }
        if (!hit) return

        val now = SystemClock.elapsedRealtime()
        if (pending || ClipboardGrabber.isBusy || now - lastTrigger < COOLDOWN_MS) return
        lastTrigger = now
        pending = true

        handler.postDelayed({
            pending = false
            ClipboardGrabber.grabAndRewrite(this)
        }, DELAY_MS)
    }

    private fun textOf(event: AccessibilityEvent): String {
        val sb = StringBuilder()
        event.text?.forEach { if (it != null) sb.append(it).append(' ') }
        event.contentDescription?.let { sb.append(it) }
        return sb.toString()
    }

    private fun hasClipboardWindow(): Boolean {
        return try {
            windows.any { w ->
                val title = w.title?.toString() ?: return@any false
                title.contains("clipboard", ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        Prefs.log(this, "サービス停止")
        super.onDestroy()
    }
}
