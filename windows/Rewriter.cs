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
    // 末尾の ?query や #fragment（トラッキング用）は捨てる。
    private static readonly Regex Pattern = new(
        @"https?://(?:[A-Za-z0-9-]+\.)*(?:x|twitter)\.com/((?:i/web|i|[A-Za-z0-9_]{1,15})/status/\d+(?:/(?:photo|video)/\d+)?)(?:[?#][^\s]*)?",
        RegexOptions.IgnoreCase | RegexOptions.Compiled);

    /// <summary>書き換え後の文字列を返す。変更が無ければ null。</summary>
    public static string? Rewrite(string text, string target)
    {
        if (string.IsNullOrEmpty(text) || !text.Contains(".com", StringComparison.OrdinalIgnoreCase))
            return null;

        var result = Pattern.Replace(text, m => $"https://{target}/{m.Groups[1].Value}");
        return result == text ? null : result;
    }
}
