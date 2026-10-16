package app.xlinkfixer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class RewriterTest {

    // Gradle のユニットテストはモジュールディレクトリ (android/app) で実行される
    private val cases: List<Pair<String, String>> =
        File("../../tests/rewrite_cases.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val parts = line.split('\t')
                require(parts.size == 2) { "不正な行: $line" }
                parts[0] to parts[1]
            }

    @Test
    fun sharedCases() {
        require(cases.isNotEmpty())
        for ((input, expected) in cases) {
            val actual = Rewriter.rewrite(input, "fixupx.com") ?: input
            assertEquals("入力: $input", expected, actual)
        }
    }

    @Test
    fun unknownTargetIsRejected() {
        assertNull(Rewriter.rewrite("https://x.com/a/status/1", "evil.example"))
    }

    @Test
    fun hugeInputIsIgnored() {
        val huge = "a".repeat(100_000) + " https://x.com/a/status/1"
        assertNull(Rewriter.rewrite(huge, "fixupx.com"))
    }

    @Test
    fun everyTargetWorks() {
        for (t in Rewriter.TARGETS) {
            assertEquals("https://$t/a/status/1", Rewriter.rewrite("https://x.com/a/status/1", t))
        }
    }

    @Test
    fun extractRewrittenKeepsOnlyUrls() {
        val out = Rewriter.extractRewritten("振込先はこちら https://x.com/a/status/1?s=20 急いで", "fixupx.com")
        assertEquals("https://fixupx.com/a/status/1", out)
        assertNull(Rewriter.extractRewritten("URL なし", "fixupx.com"))
    }
}
