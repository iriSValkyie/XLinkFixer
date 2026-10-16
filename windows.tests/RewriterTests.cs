using Xunit;

namespace XLinkFixer.Tests;

public class RewriterTests
{
    private static readonly string CasesPath =
        Path.Combine(AppContext.BaseDirectory, "rewrite_cases.tsv");

    public static IEnumerable<object[]> Cases()
    {
        foreach (var line in File.ReadAllLines(CasesPath))
        {
            if (line.Length == 0 || line.StartsWith('#')) continue;
            var parts = line.Split('\t');
            if (parts.Length != 2) throw new InvalidDataException($"不正な行: {line}");
            yield return new object[] { parts[0], parts[1] };
        }
    }

    [Theory]
    [MemberData(nameof(Cases))]
    public void SharedCases(string input, string expected)
    {
        var actual = Rewriter.Rewrite(input, "fixupx.com") ?? input;
        Assert.Equal(expected, actual);
    }

    [Fact]
    public void UnknownTargetIsRejected()
    {
        Assert.Null(Rewriter.Rewrite("https://x.com/a/status/1", "evil.example"));
    }

    [Fact]
    public void HugeInputIsIgnored()
    {
        var huge = new string('a', 100_000) + " https://x.com/a/status/1";
        Assert.Null(Rewriter.Rewrite(huge, "fixupx.com"));
    }

    [Fact]
    public void EveryTargetWorks()
    {
        foreach (var t in Rewriter.Targets)
            Assert.Equal($"https://{t}/a/status/1", Rewriter.Rewrite("https://x.com/a/status/1", t));
    }
}
