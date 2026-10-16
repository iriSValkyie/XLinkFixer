using System.Text.RegularExpressions;

namespace XLinkFixer;

/// <summary>
/// x.com / twitter.com のツイート URL を fixupx.com 等に書き換える。
/// Android 版 (Rewriter.kt) と同じ正規表現を使うこと。
/// </summary>
public static class Rewriter
{
    public static readonly string[] Targets = { "fixupx.com", "fxtwitter.com", "vxtwitter.com" };

    // https://x.com/user/status/123?s=20  →  https://fixupx.com/user/status/123
    // サブドメイン (www, mobile) 許可、/i/status/ と /i/web/status/ も対象、
    // 末尾ドット (x.com.) とポート (:443) も許容、?query / #fragment（トラッキング用）は捨てる。
    //
    // 注意: \s \d \w は .NET と Java/Kotlin で意味が違う（.NET は Unicode、Java は ASCII）ので使わない。
    // クエリ部は URL に使える ASCII 文字だけを許す allowlist。「…?s=20を貼ってね」の「を貼ってね」や
    // 閉じ括弧「」」「)」を URL の一部として飲み込まないため。テストは tests/rewrite_cases.tsv。
    private static readonly Regex Pattern = new(
        @"https?://(?:[A-Za-z0-9-]+\.)*(?:x|twitter)\.com\.?(?::[0-9]+)?/((?:i/web|i|[A-Za-z0-9_]{1,15})/status/[0-9]+(?:/(?:photo|video)/[0-9]+)?)(?:[?#][A-Za-z0-9._~&=/%+?#-]*)?",
        RegexOptions.IgnoreCase | RegexOptions.Compiled,
        TimeSpan.FromMilliseconds(250));   // クリップボードの中身は信用しない。万一の ReDoS 保険

    /// <summary>これより長いテキストは触らない（巨大なコピーで CPU を食わないため）</summary>
    private const int MaxLength = 64 * 1024;

    /// <summary>書き換え後の文字列を返す。変更が無ければ null。</summary>
    public static string? Rewrite(string text, string target)
    {
        if (string.IsNullOrEmpty(text) || text.Length > MaxLength)
            return null;
        if (!text.Contains(".com", StringComparison.OrdinalIgnoreCase))
            return null;
        if (Array.IndexOf(Targets, target) < 0)   // 置き換え先は固定リスト以外を許さない
            return null;

        try
        {
            var result = Pattern.Replace(text, m => $"https://{target}/{m.Groups[1].Value}");
            return result == text ? null : result;
        }
        catch (RegexMatchTimeoutException)
        {
            return null;
        }
    }
}
