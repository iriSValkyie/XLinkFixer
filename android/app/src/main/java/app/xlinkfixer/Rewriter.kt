package app.xlinkfixer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * x.com / twitter.com のツイート URL を fixupx.com 等に書き換える。
 * Windows 版 (Rewriter.cs) と同じ正規表現を使うこと。
 */
object Rewriter {

    val TARGETS = listOf("fixupx.com", "fxtwitter.com", "vxtwitter.com")

    // https://x.com/user/status/123?s=20  →  https://fixupx.com/user/status/123
    // サブドメイン (www, mobile) 許可、/i/status/ と /i/web/status/ も対象、
    // 末尾の ?query や #fragment（トラッキング用）は捨てる。
    private val PATTERN = Regex(
        """https?://(?:[A-Za-z0-9-]+\.)*(?:x|twitter)\.com/((?:i/web|i|[A-Za-z0-9_]{1,15})/status/\d+(?:/(?:photo|video)/\d+)?)(?:[?#][^\s]*)?""",
        RegexOption.IGNORE_CASE
    )

    /** 書き換え後の文字列。変更が無ければ null。 */
    fun rewrite(text: String?, target: String): String? {
        if (text.isNullOrEmpty() || !text.contains(".com", ignoreCase = true)) return null
        val result = PATTERN.replace(text) { m -> "https://$target/${m.groupValues[1]}" }
        return if (result == text) null else result
    }

    @Volatile
    private var lastWritten: String? = null

    /**
     * 現在のクリップボードを読んで、必要なら書き換えて戻す。
     * 呼び出し元がフォーカスを持っている（= クリップボードを読める）状態で呼ぶこと。
     * 戻り値は書き換え後の文字列。何もしなかった場合は null。
     */
    fun processClipboard(ctx: Context): String? {
        val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = try { cm.primaryClip } catch (e: Exception) {
            Prefs.log(ctx, "読み取り失敗: ${e.javaClass.simpleName}")
            return null
        } ?: return null
        if (clip.itemCount == 0) return null

        val text = clip.getItemAt(0).coerceToText(ctx)?.toString() ?: return null
        if (text == lastWritten) return null  // 自分が書いた内容を再処理しない

        val target = Prefs.target(ctx)
        val out = rewrite(text, target) ?: return null

        lastWritten = out
        try {
            cm.setPrimaryClip(ClipData.newPlainText("url", out))
        } catch (e: Exception) {
            Prefs.log(ctx, "書き込み失敗: ${e.javaClass.simpleName}")
            return null
        }
        Prefs.log(ctx, "書き換え → $out")
        return out
    }
}
