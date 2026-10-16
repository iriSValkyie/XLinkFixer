package app.xlinkfixer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * 共有シートの「XLinkFixer でリンクを書き換えてコピー」。
 * アクセシビリティが使えない機種向けの保険。前面に出るのでクリップボードに普通に書ける。
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
        val out = Rewriter.rewrite(text, Prefs.target(this))

        if (out == null) {
            Toast.makeText(this, R.string.toast_nothing, Toast.LENGTH_SHORT).show()
        } else {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("url", out))
            Prefs.log(this, "共有から書き換え → $out")
            Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
