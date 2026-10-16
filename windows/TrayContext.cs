using System.Runtime.InteropServices;
using Microsoft.Win32;

namespace XLinkFixer;

public sealed class TrayContext : ApplicationContext
{
    private const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";
    private const string RunValue = "XLinkFixer";

    private readonly Settings _settings = Settings.Load();
    private readonly NotifyIcon _icon;
    private readonly ClipboardWatcher _watcher;
    private readonly ToolStripMenuItem _enabledItem;
    private readonly ToolStripMenuItem _notifyItem;
    private readonly ToolStripMenuItem _startupItem;
    private readonly List<ToolStripMenuItem> _targetItems = new();

    private string? _lastSet;

    public TrayContext()
    {
        var menu = new ContextMenuStrip();

        _enabledItem = new ToolStripMenuItem("有効") { CheckOnClick = true, Checked = _settings.Enabled };
        _enabledItem.CheckedChanged += (_, _) => { _settings.Enabled = _enabledItem.Checked; _settings.Save(); UpdateIcon(); };
        menu.Items.Add(_enabledItem);

        var targetMenu = new ToolStripMenuItem("置き換え先");
        foreach (var t in Rewriter.Targets)
        {
            var item = new ToolStripMenuItem(t) { Checked = t == _settings.Target, Tag = t };
            item.Click += (_, _) => SelectTarget(t);
            targetMenu.DropDownItems.Add(item);
            _targetItems.Add(item);
        }
        menu.Items.Add(targetMenu);

        _notifyItem = new ToolStripMenuItem("書き換え時に通知") { CheckOnClick = true, Checked = _settings.ShowNotification };
        _notifyItem.CheckedChanged += (_, _) => { _settings.ShowNotification = _notifyItem.Checked; _settings.Save(); };
        menu.Items.Add(_notifyItem);

        _startupItem = new ToolStripMenuItem("Windows 起動時に実行") { CheckOnClick = true, Checked = IsStartupEnabled() };
        _startupItem.CheckedChanged += (_, _) => SetStartup(_startupItem.Checked);
        menu.Items.Add(_startupItem);

        menu.Items.Add(new ToolStripSeparator());

        var quit = new ToolStripMenuItem("終了");
        quit.Click += (_, _) => ExitThread();
        menu.Items.Add(quit);

        _icon = new NotifyIcon
        {
            Icon = CreateIcon(),
            Visible = true,
            ContextMenuStrip = menu,
        };
        _icon.DoubleClick += (_, _) => _enabledItem.Checked = !_enabledItem.Checked;
        UpdateIcon();

        _watcher = new ClipboardWatcher();
        _watcher.ClipboardUpdated += OnClipboardUpdated;
    }

    private void SelectTarget(string target)
    {
        _settings.Target = target;
        _settings.Save();
        foreach (var item in _targetItems) item.Checked = (string)item.Tag! == target;
        UpdateIcon();
    }

    private void UpdateIcon()
    {
        _icon.Text = _settings.Enabled ? $"XLinkFixer - 有効 (→ {_settings.Target})" : "XLinkFixer - 無効";
    }

    private void OnClipboardUpdated()
    {
        if (!_settings.Enabled) return;

        var text = TryGetText();
        if (text == null) return;
        if (text == _lastSet) return;              // 自分が書いた直後の通知は無視

        var rewritten = Rewriter.Rewrite(text, _settings.Target);
        if (rewritten == null) return;

        _lastSet = rewritten;
        if (!TrySetText(rewritten)) return;

        if (_settings.ShowNotification)
        {
            _icon.ShowBalloonTip(1500, "XLinkFixer", $"→ {_settings.Target} に書き換えました", ToolTipIcon.Info);
        }
    }

    // コピー直後は元アプリがクリップボードを掴んでいることがあるので少しリトライする
    private static string? TryGetText()
    {
        for (int i = 0; i < 6; i++)
        {
            try
            {
                return Clipboard.ContainsText() ? Clipboard.GetText() : null;
            }
            catch (ExternalException)
            {
                Thread.Sleep(40);
            }
        }
        return null;
    }

    private static bool TrySetText(string text)
    {
        try
        {
            Clipboard.SetDataObject(text, true, 8, 60);
            return true;
        }
        catch (ExternalException)
        {
            return false;
        }
    }

    private static bool IsStartupEnabled()
    {
        using var key = Registry.CurrentUser.OpenSubKey(RunKey, false);
        return key?.GetValue(RunValue) != null;
    }

    private static void SetStartup(bool on)
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(RunKey, true);
            if (key == null) return;
            if (on)
            {
                var exe = Environment.ProcessPath ?? Application.ExecutablePath;
                key.SetValue(RunValue, $"\"{exe}\"");
            }
            else
            {
                key.DeleteValue(RunValue, false);
            }
        }
        catch { }
    }

    // 外部アイコンファイル無しで済ませるため、簡単なアイコンを描画で作る
    private static Icon CreateIcon()
    {
        using var bmp = new Bitmap(32, 32);
        using (var g = Graphics.FromImage(bmp))
        {
            g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            g.TextRenderingHint = System.Drawing.Text.TextRenderingHint.AntiAlias;
            g.Clear(Color.Transparent);
            using var bg = new SolidBrush(Color.FromArgb(0x1D, 0x9B, 0xF0));
            g.FillEllipse(bg, 1, 1, 30, 30);
            using var font = new Font("Segoe UI", 13, FontStyle.Bold, GraphicsUnit.Pixel);
            var size = g.MeasureString("fx", font);
            g.DrawString("fx", font, Brushes.White, (32 - size.Width) / 2, (32 - size.Height) / 2);
        }
        return Icon.FromHandle(bmp.GetHicon());
    }

    protected override void ExitThreadCore()
    {
        _watcher.Dispose();
        _icon.Visible = false;
        _icon.Dispose();
        base.ExitThreadCore();
    }
}
