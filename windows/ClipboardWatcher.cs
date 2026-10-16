using System.Runtime.InteropServices;

namespace XLinkFixer;

/// <summary>
/// メッセージ専用ウィンドウを作り、WM_CLIPBOARDUPDATE を受け取る。
/// </summary>
public sealed class ClipboardWatcher : NativeWindow, IDisposable
{
    private const int WM_CLIPBOARDUPDATE = 0x031D;
    private static readonly IntPtr HWND_MESSAGE = new(-3);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool AddClipboardFormatListener(IntPtr hwnd);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool RemoveClipboardFormatListener(IntPtr hwnd);

    public event Action? ClipboardUpdated;

    public ClipboardWatcher()
    {
        CreateHandle(new CreateParams { Parent = HWND_MESSAGE });
        AddClipboardFormatListener(Handle);
    }

    protected override void WndProc(ref Message m)
    {
        if (m.Msg == WM_CLIPBOARDUPDATE)
        {
            ClipboardUpdated?.Invoke();
        }
        base.WndProc(ref m);
    }

    public void Dispose()
    {
        if (Handle != IntPtr.Zero)
        {
            RemoveClipboardFormatListener(Handle);
            DestroyHandle();
        }
    }
}
