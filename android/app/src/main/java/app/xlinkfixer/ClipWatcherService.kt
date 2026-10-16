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

        // 1 分あたりの最大発火回数。悪意あるアプリが「コピー」を含むトーストや
        // announce を連打して、フォーカス奪取を繰り返させる（= 操作妨害）のを防ぐ。
        private const val MAX_PER_MINUTE = 12

        // 「コピーが完了した」ことを示す文言（トースト / announce / ウィンドウ名）
        private val DONE_KEYWORD = Regex(
            "コピーしました|コピー済み|コピーされました|copied|clipboard|クリップボード",
            RegexOption.IGNORE_CASE
        )
        // ユーザーが「コピー」ボタンを押した（クリックイベント限定）
        private val ACTION_KEYWORD = Regex("コピー|copy", RegexOption.IGNORE_CASE)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val appLabel: String by lazy { getString(R.string.app_name) }
    private var lastTrigger = 0L
    private var pending = false
    private var windowStart = 0L
    private var windowCount = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Prefs.log(this, "サービス開始")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!Prefs.enabled(this)) return

        val pkg = event.packageName?.toString() ?: ""
        if (pkg == packageName) return  // 自分自身のトースト等は無視

        // 自分の読み書きに起因するイベントの波が収まるまで何もしない（自己ループ防止）
        if (ClipboardGrabber.isSuppressed) return

        // システムの「XLinkFixer がクリップボードから貼り付けました」トーストは
        // SystemUI から飛んでくるので、自分のアプリ名を含むイベントも除外する
        val text = textOf(event)
        if (text.contains(appLabel)) return

        val aggressive = Prefs.aggressive(this) && pkg == SYSTEMUI
        val hit = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED ->
                ACTION_KEYWORD.containsMatchIn(text)
            AccessibilityEvent.TYPE_ANNOUNCEMENT,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                DONE_KEYWORD.containsMatchIn(text) || aggressive
            AccessibilityEvent.TYPE_WINDOWS_CHANGED ->
                aggressive || hasClipboardWindow()
            else -> false
        }
        if (!hit) return

        val now = SystemClock.elapsedRealtime()
        if (pending || ClipboardGrabber.isBusy || now - lastTrigger < COOLDOWN_MS) return

        // 1 分間のレート制限
        if (now - windowStart > 60_000L) { windowStart = now; windowCount = 0 }
        if (++windowCount > MAX_PER_MINUTE) {
            if (windowCount == MAX_PER_MINUTE + 1) Prefs.log(this, "発火が多すぎるため一時停止（1分）")
            return
        }

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
        // 文言は照合にしか使わず、保存もしない。長すぎるものは先頭だけ見る
        return if (sb.length > 512) sb.substring(0, 512) else sb.toString()
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
