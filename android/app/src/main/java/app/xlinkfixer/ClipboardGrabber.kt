package app.xlinkfixer

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast

/**
 * Android 10 以降、クリップボードは「フォーカスを持つウィンドウのアプリ」しか読めない。
 * そこで 1px の透明なフォーカス可能オーバーレイを一瞬出してフォーカスを取り、
 * その瞬間に読み書きしてすぐ消す。
 */
object ClipboardGrabber {

    private const val TIMEOUT_MS = 1500L
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var busy = false

    val isBusy: Boolean get() = busy

    fun grabAndRewrite(ctx: Context) {
        if (busy) return
        busy = true

        val wm = ctx.getSystemService(WindowManager::class.java)
        if (wm == null) { busy = false; return }

        // オーバーレイ権限があれば通常のオーバーレイ、無ければアクセシビリティ用の
        // オーバーレイ（サービスの Context からのみ追加可能）で試す。
        val type = if (Settings.canDrawOverlays(ctx)) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else if (ctx is AccessibilityService) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            Prefs.log(ctx, "オーバーレイ権限が無いため読めません")
            busy = false
            return
        }

        val lp = WindowManager.LayoutParams(
            1, 1, type,
            // FLAG_NOT_FOCUSABLE を付けない = フォーカスを取りに行く
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        var finished = false
        lateinit var view: View

        fun finish() {
            if (finished) return
            finished = true
            try { wm.removeViewImmediate(view) } catch (_: Exception) {}
            busy = false
        }

        view = FocusView(ctx) {
            if (finished) return@FocusView
            val out = try {
                Rewriter.processClipboard(ctx)
            } catch (e: Exception) {
                Prefs.log(ctx, "エラー: ${e.javaClass.simpleName}")
                null
            }
            finish()
            if (out != null && Prefs.toast(ctx)) {
                Toast.makeText(ctx, ctx.getString(R.string.toast_rewritten, Prefs.target(ctx)), Toast.LENGTH_SHORT).show()
            }
        }

        handler.postDelayed({
            if (!finished) {
                Prefs.log(ctx, "フォーカスを取れませんでした（タイムアウト）")
                finish()
            }
        }, TIMEOUT_MS)

        try {
            wm.addView(view, lp)
        } catch (e: Exception) {
            Prefs.log(ctx, "オーバーレイ表示失敗: ${e.javaClass.simpleName}")
            finish()
        }
    }

    private class FocusView(ctx: Context, private val onFocus: () -> Unit) : View(ctx) {
        init {
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (hasWindowFocus) onFocus()
        }
    }
}
