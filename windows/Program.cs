namespace XLinkFixer;

static class Program
{
    [STAThread]
    static void Main()
    {
        // 二重起動防止
        using var mutex = new Mutex(true, @"Local\XLinkFixer-SingleInstance", out bool createdNew);
        if (!createdNew) return;

        ApplicationConfiguration.Initialize();
        Application.Run(new TrayContext());
    }
}
