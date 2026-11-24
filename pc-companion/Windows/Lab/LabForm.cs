using System.Diagnostics;
using System.Globalization;
using Mobilepad.Companion.Lab;

namespace Mobilepad.Companion;

/// <summary>
/// Mobilepad Lab (labs/SPEC.md §5): pick a test, a link and its parameters, run it on the phone,
/// watch it live, then read the results (histogram, CDF, time series, per-stage bars, metrics
/// marked measured/estimated), browse the history and compare two runs. Painted like MainForm;
/// only the parameter boxes and the pill buttons are controls.
/// </summary>
public sealed class LabForm : Form
{
    private enum View { Results, History }

    private readonly CompanionServer _server;
    private readonly AdbBridge _adb;
    private readonly LabEngine _engine;
    private readonly string _version = Application.ProductVersion.Split('+')[0];

    private string _test = LabTests.Latency;
    private string _link = LabLinks.Usb;
    private View _view = View.Results;
    private CancellationTokenSource? _stopCts;
    private string _message = "";
    private bool _messageIsError;

    private LabSummary? _summary;
    private LabRunEntry? _summaryEntry;
    private string? _chartStage;
    private List<LabRunEntry> _history = [];
    private readonly List<string> _selected = [];
    private List<CompareRow>? _compare;
    private string _compareTitle = "";

    private int _scroll;
    private int _contentHeight;

    private readonly TextBox _count = new(), _seed = new(), _rate = new(), _seconds = new();
    private readonly PillButton _run = new(), _stop = new(), _open = new(), _copyJson = new(), _export = new(),
        _show = new(), _compareButton = new(), _refresh = new();
    private readonly System.Windows.Forms.Timer _timer = new() { Interval = 250 };

    private float _scale = 1f;
    private Font _titleFont = null!, _subtitleFont = null!, _cardTitleFont = null!, _bodyFont = null!, _strongFont = null!,
        _captionFont = null!, _smallFont = null!, _monoFont = null!, _bigFont = null!, _pillFont = null!, _iconFont = null!, _smallIconFont = null!;

    private Bitmap? _backdrop;
    private Rectangle _headerTile, _titleRect, _subtitleRect, _pill;
    private Rectangle _leftCard, _testCaption, _testDesc, _linkCaption, _paramCaption, _hintRect, _progressRect, _liveRect, _messageRect;
    private readonly Rectangle[] _testChips = new Rectangle[6];
    private readonly Rectangle[] _linkChips = new Rectangle[3];
    private readonly Rectangle[] _paramLabels = new Rectangle[4];
    private readonly Rectangle[] _paramWells = new Rectangle[4];
    private Rectangle _rightCard, _tabResults, _tabHistory, _content;

    private readonly record struct Hit(Rectangle Rect, Action? Click, Action? DoubleClick, bool InContent);
    private readonly List<Hit> _hits = [];

    private static string T(string it, string en, string zhs, string zht, string ja) => L.T(it, en, zhs, zht, ja);

    public LabForm(CompanionServer server, AdbBridge adb)
    {
        _server = server;
        _adb = adb;
        _engine = new LabEngine(new LabHost
        {
            AdbPath = adb.AdbPath,
            CompanionVersion = _version,
            CreateObserver = CreateObserver,
            CheckLink = CheckLink,
        });

        AutoScaleMode = AutoScaleMode.None;
        SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint | ControlStyles.ResizeRedraw, true);
        Text = $"Mobilepad Lab {_version}";
        try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch (ArgumentException) { }
        BackColor = Theme.Background;
        ForeColor = Theme.Text;
        KeyPreview = true;

        foreach (var box in new[] { _count, _seed, _rate, _seconds })
        {
            box.BorderStyle = BorderStyle.None;
            box.BackColor = Theme.Well;
            box.ForeColor = Theme.Text;
        }

        Setup(_run, T("Avvia", "Run", "运行", "執行", "実行"), "", primary: true, StartRun);
        Setup(_stop, T("Ferma", "Stop", "停止", "停止", "停止"), "", primary: false, () => _stopCts?.Cancel());
        Setup(_open, T("Cartella", "Folder", "文件夹", "資料夾", "フォルダー"), "", primary: false, OpenFolder);
        Setup(_copyJson, "JSON", Glyph.Copy, primary: false, CopyJson);
        Setup(_export, T("Esporta…", "Export…", "导出…", "匯出…", "エクスポート…"), "", primary: false, Export);
        Setup(_show, T("Apri", "Show", "查看", "檢視", "表示"), "", primary: false, ShowSelected);
        Setup(_compareButton, T("Confronta", "Compare", "比较", "比較", "比較"), "", primary: true, CompareSelected);
        Setup(_refresh, "", "", primary: false, ReloadHistory);

        Controls.AddRange([_count, _seed, _rate, _seconds, _run, _stop, _open, _copyJson, _export, _show, _compareButton, _refresh]);

        ApplyScale();
        ClientSize = new Size(S(1240), S(820));
        StartPosition = FormStartPosition.CenterScreen;

        _link = _server.Sessions.Any(s => s.IsUsb) ? LabLinks.Usb : _server.Sessions.Any() ? LabLinks.Wifi : LabLinks.Usb;
        ApplyTestParams();

        _timer.Tick += (_, _) => Invalidate(_leftCard);
        WarmUpGamingInput();
        ReloadHistory();
        if (_history.FirstOrDefault(h => h.Headline is not null) is { } latest) LoadRun(latest);
        UpdateButtons();
    }

    private void Setup(PillButton b, string text, string glyph, bool primary, Action click)
    {
        b.Text = text;
        b.Glyph = glyph;
        b.Primary = primary;
        b.Click += (_, _) => click();
    }

    private int S(float v) => (int)Math.Round(v * _scale);

    private void ApplyScale()
    {
        _scale = DeviceDpi / 96f;
        foreach (var f in new[] { _titleFont, _subtitleFont, _cardTitleFont, _bodyFont, _strongFont, _captionFont, _smallFont, _monoFont, _bigFont, _pillFont, _iconFont, _smallIconFont })
            f?.Dispose();
        _titleFont = Fonts.Px(Fonts.Display, S(30), FontStyle.Bold);
        _subtitleFont = Fonts.Px(Fonts.Body, S(13));
        _cardTitleFont = Fonts.Px(Fonts.Display, S(17), FontStyle.Bold);
        _bodyFont = Fonts.Px(Fonts.Body, S(13));
        _strongFont = Fonts.Px(Fonts.Body, S(13), FontStyle.Bold);
        _captionFont = Fonts.Px(Fonts.Body, S(11.5f), FontStyle.Bold);
        _smallFont = Fonts.Px(Fonts.Body, S(11.5f));
        _monoFont = Fonts.Px(Fonts.Mono, S(12.5f));
        _bigFont = Fonts.Px(Fonts.Display, S(24), FontStyle.Bold);
        _pillFont = Fonts.Px(Fonts.Body, S(13), FontStyle.Bold);
        _iconFont = Fonts.Px(Fonts.Icons, S(20));
        _smallIconFont = Fonts.Px(Fonts.Icons, S(13));
        foreach (var box in new[] { _count, _seed, _rate, _seconds }) box.Font = _monoFont;
        foreach (var b in new[] { _run, _stop, _open, _copyJson, _export, _show, _compareButton, _refresh })
        {
            b.Font = _pillFont;
            b.IconFont = _smallIconFont;
        }
        MinimumSize = SizeFromClientSize(new Size(S(1000), S(680)));
    }

    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        Native.DarkTitleBar(Handle, Theme.Background);
    }

    protected override void OnDpiChanged(DpiChangedEventArgs e)
    {
        base.OnDpiChanged(e);
        ApplyScale();
        DoLayout();
    }

    protected override void OnLayout(LayoutEventArgs e)
    {
        base.OnLayout(e);
        DoLayout();
    }

    protected override void OnResize(EventArgs e)
    {
        _backdrop?.Dispose();
        _backdrop = null;
        base.OnResize(e);
    }

    /// <summary>A run keeps going when the window is closed: it only hides, and reopening shows it.</summary>
    protected override void OnFormClosing(FormClosingEventArgs e)
    {
        if (_engine.Running && e.CloseReason == CloseReason.UserClosing)
        {
            e.Cancel = true;
            Hide();
            return;
        }
        base.OnFormClosing(e);
    }

    // ------------------------------------------------------------------ layout

    private void DoLayout()
    {
        if (_pillFont is null || ClientSize.Width <= 0 || ClientSize.Height <= 0) return;
        int margin = S(24), gap = S(16), pad = S(20);
        var client = ClientSize;

        _headerTile = new Rectangle(margin, S(20), S(48), S(48));
        var titleSize = Draw.Measure("Lab", _titleFont);
        var subtitleSize = Draw.Measure("Companion", _subtitleFont);
        int headerTextTop = _headerTile.Top + (_headerTile.Height - titleSize.Height - subtitleSize.Height) / 2;
        _titleRect = new Rectangle(_headerTile.Right + S(14), headerTextTop, titleSize.Width + S(4), titleSize.Height);
        _subtitleRect = new Rectangle(_titleRect.X + S(1), _titleRect.Bottom, S(520), subtitleSize.Height);
        int pillHeight = S(38);
        int pillWidth = S(42) + Draw.Measure(StatusText(), _pillFont).Width + S(16);
        _pill = new Rectangle(client.Width - margin - pillWidth, _headerTile.Top + (_headerTile.Height - pillHeight) / 2, pillWidth, pillHeight);

        int top = _headerTile.Bottom + S(22);
        int bottom = client.Height - margin;

        // ---- Left card: test, link, parameters, run, live ----
        _leftCard = new Rectangle(margin, top, S(340), bottom - top);
        int x = _leftCard.X + pad, inner = _leftCard.Width - 2 * pad, y = _leftCard.Y + pad;
        int capH = Draw.Measure("TEST", _captionFont).Height;
        int chipH = S(32), chipGap = S(8);
        _testCaption = new Rectangle(x, y, inner, capH);
        y += capH + S(10);
        int chipW = (inner - chipGap) / 2;
        for (int i = 0; i < 6; i++)
            _testChips[i] = new Rectangle(x + (i % 2) * (chipW + chipGap), y + (i / 2) * (chipH + chipGap), chipW, chipH);
        y += 3 * chipH + 2 * chipGap + S(10);
        int descH = Draw.Measure("X\nX\nX", _smallFont).Height;
        _testDesc = new Rectangle(x, y, inner, descH);
        y += descH + S(14);
        _linkCaption = new Rectangle(x, y, inner, capH);
        y += capH + S(10);
        int linkW = (inner - 2 * chipGap) / 3;
        for (int i = 0; i < 3; i++) _linkChips[i] = new Rectangle(x + i * (linkW + chipGap), y, linkW, chipH);
        y += chipH + S(18);
        _paramCaption = new Rectangle(x, y, inner, capH);
        y += capH + S(10);
        int rowH = S(30), labelW = inner * 45 / 100;
        var boxes = new[] { _count, _seed, _rate, _seconds };
        for (int i = 0; i < 4; i++)
        {
            _paramLabels[i] = new Rectangle(x, y, labelW, rowH);
            _paramWells[i] = new Rectangle(x + labelW, y, inner - labelW, rowH);
            int boxH = boxes[i].PreferredHeight;
            boxes[i].Bounds = new Rectangle(_paramWells[i].X + S(10), _paramWells[i].Y + (rowH - boxH) / 2, _paramWells[i].Width - S(20), boxH);
            y += rowH + S(8);
        }
        int hintH = Draw.Measure("X", _smallFont).Height;
        _hintRect = new Rectangle(x, y, inner, hintH);
        y += hintH + S(14);
        int buttonH = S(34);
        foreach (var b in new[] { _run, _stop }) b.Height = buttonH;
        _stop.Width = _stop.PreferredWidth(buttonH);
        _run.Width = inner - _stop.Width - chipGap;
        _run.Location = new Point(x, y);
        _stop.Location = new Point(_run.Right + chipGap, y);
        y += buttonH + S(16);
        _progressRect = new Rectangle(x, y, inner, S(8));
        y += S(8) + S(10);
        int lineH = Draw.Measure("X", _bodyFont).Height + S(5);
        _liveRect = new Rectangle(x, y, inner, lineH * 6);
        y += lineH * 6 + S(8);
        _messageRect = Rectangle.FromLTRB(x, y, x + inner, _leftCard.Bottom - pad);

        // ---- Right card: tabs + actions, content ----
        int rx = _leftCard.Right + gap;
        _rightCard = new Rectangle(rx, top, client.Width - margin - rx, bottom - top);
        int tabH = S(34);
        int tabW1 = Draw.Measure(ResultsTab, _pillFont).Width + S(36);
        int tabW2 = Draw.Measure(HistoryTab, _pillFont).Width + S(36);
        _tabResults = new Rectangle(_rightCard.X + pad, _rightCard.Y + pad, tabW1, tabH);
        _tabHistory = new Rectangle(_tabResults.Right + S(6), _tabResults.Y, tabW2, tabH);
        int bx = _rightCard.Right - pad;
        var actions = _view == View.Results ? new[] { _export, _copyJson, _open } : new[] { _refresh, _show, _compareButton };
        foreach (var b in new[] { _open, _copyJson, _export, _show, _compareButton, _refresh }) b.Visible = actions.Contains(b);
        foreach (var b in actions)
        {
            b.Height = S(30);
            b.Width = b.Text.Length == 0 ? S(30) : b.PreferredWidth(S(30));
            bx -= b.Width;
            b.Location = new Point(bx, _tabResults.Y + (tabH - b.Height) / 2);
            bx -= S(8);
        }
        _content = Rectangle.FromLTRB(_rightCard.X + pad, _tabResults.Bottom + S(16), _rightCard.Right - pad, _rightCard.Bottom - S(12));
        ClampScroll();
        Invalidate();
    }

    private string ResultsTab => T("Risultati", "Results", "结果", "結果", "結果");
    private string HistoryTab => T("Cronologia", "History", "历史", "歷史", "履歴");

    // ------------------------------------------------------------------ paint

    protected override void OnPaintBackground(PaintEventArgs e)
    {
        if (_backdrop is null || _backdrop.Size != ClientSize)
        {
            _backdrop?.Dispose();
            _backdrop = new Bitmap(Math.Max(1, ClientSize.Width), Math.Max(1, ClientSize.Height));
            using var bg = Graphics.FromImage(_backdrop);
            Draw.HighQuality(bg);
            Draw.Backdrop(bg, ClientSize);
        }
        e.Graphics.DrawImageUnscaled(_backdrop, 0, 0);
    }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        Draw.HighQuality(g);
        _hits.Clear();

        Draw.IconTile(g, _headerTile, Theme.Violet, "", _iconFont);
        Draw.Text(g, "Lab", _titleFont, _titleRect, Theme.Text, TextFormatFlags.SingleLine);
        Draw.Text(g, T("Companion · misure riproducibili", "Companion · reproducible measurements", "伴侣程序 · 可复现的测量", "伴侶程式 · 可重現的量測", "コンパニオン · 再現可能な計測") + $" · {_version}",
            _subtitleFont, _subtitleRect, Theme.Faint, TextFormatFlags.SingleLine);
        Draw.Glass(g, _pill, _pill.Height / 2f);
        Draw.Dot(g, new PointF(_pill.X + S(22), _pill.Y + _pill.Height / 2f), S(9), _engine.Running ? Theme.Accent : _adb.AdbPath is null ? Theme.Danger : Theme.Success);
        Draw.Text(g, StatusText(), _pillFont, Rectangle.FromLTRB(_pill.X + S(40), _pill.Y, _pill.Right, _pill.Bottom), Theme.Text,
            TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine);

        PaintLeft(g);
        PaintRight(g);
    }

    private string StatusText() =>
        _engine.Running ? T("Test in corso", "Test running", "测试进行中", "測試進行中", "テスト実行中") :
        _adb.AdbPath is null ? T("ADB non trovato", "ADB not found", "未找到 ADB", "找不到 ADB", "ADB が見つかりません") :
        T("Pronto", "Ready", "就绪", "就緒", "準備完了");

    private void Chip(Graphics g, Rectangle r, string text, bool selected, bool enabled, Action click, Font? font = null)
    {
        using var path = Draw.Rounded(new RectangleF(r.X + 0.5f, r.Y + 0.5f, r.Width - 1, r.Height - 1), r.Height / 2f);
        if (selected)
        {
            using var fill = new System.Drawing.Drawing2D.LinearGradientBrush(new Rectangle(r.X, r.Y - 1, r.Width, r.Height + 2), Theme.PrimaryTop, Theme.PrimaryBottom, 90f);
            g.FillPath(fill, path);
        }
        else
        {
            using var fill = new SolidBrush(Color.FromArgb(18, Color.White));
            g.FillPath(fill, path);
        }
        using (var pen = new Pen(Color.FromArgb(selected ? 70 : 40, Color.White)))
            g.DrawPath(pen, path);
        LabCharts.Text(g, text, font ?? _pillFont, r, selected ? Color.White : enabled ? Theme.Muted : Theme.Faint,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
        if (enabled) _hits.Add(new Hit(r, click, null, false));
    }

    private static readonly string[] TestLabels = ["Latency", "Multi", "Analog", "Throughput", "Reliability", "Reconnect"];

    private string TestDescription(string test) => test switch
    {
        LabTests.Latency => T("Impulsi di un pulsante a intervalli casuali: latenza per fase, perdite, duplicati.",
            "Pulses of one button at random intervals: latency per stage, losses, duplicates.",
            "以随机间隔按下一个按钮：各阶段延迟、丢失、重复。", "以隨機間隔按下一個按鈕：各階段延遲、遺失、重複。", "ランダムな間隔でボタンを押す：段階ごとの遅延、欠落、重複。"),
        LabTests.Multi => T("Combinazioni casuali di 1–6 pulsanti, grilletti e stick sinistro: lo stato arriva intero?",
            "Random combos of 1–6 buttons, triggers and left stick: does the whole state arrive?",
            "随机组合 1–6 个按钮、扳机和左摇杆：状态是否完整到达？", "隨機組合 1–6 個按鈕、扳機和左搖桿：狀態是否完整送達？", "1～6 個のボタン・トリガー・左スティックをランダムに組み合わせ：状態は完全に届くか？"),
        LabTests.Analog => T("Rotazione dello stick sinistro (cerchio + rampe): frequenza effettiva, risoluzione, errore.",
            "Left-stick sweep (circle + ramps): effective rate, resolution, value error.",
            "左摇杆扫动（圆 + 斜坡）：有效频率、分辨率、数值误差。", "左搖桿掃動（圓 + 斜坡）：有效頻率、解析度、數值誤差。", "左スティックのスイープ（円＋ランプ）：実効レート、分解能、値の誤差。"),
        LabTests.Throughput => T("Il più veloce possibile per N secondi: stati/s consegnati, perdite sotto carico.",
            "As fast as possible for N seconds: states/s delivered, losses under load.",
            "在 N 秒内尽可能快：每秒送达状态数、负载下的丢失。", "在 N 秒內盡可能快：每秒送達狀態數、負載下的遺失。", "N 秒間できるだけ速く：毎秒の到達状態数、負荷時の欠落。"),
        LabTests.Reliability => T("N stati casuali rapidi, fine in neutro: persi, duplicati, fuori ordine, pulsanti bloccati.",
            "N fast random states ending in neutral: lost, duplicated, out of order, stuck buttons.",
            "N 个快速随机状态，以中立结束：丢失、重复、乱序、卡住的按钮。", "N 個快速隨機狀態，以中立結束：遺失、重複、亂序、卡住的按鈕。", "N 個の高速ランダム状態、最後はニュートラル：欠落、重複、順序違い、押しっぱなし。"),
        _ => T("Interrompe e ripristina il collegamento N volte con un pulsante premuto: tempo di riconnessione, rilascio.",
            "Drops and restores the link N times with a button held: reconnect time, release after the drop.",
            "按住按钮时断开并恢复连接 N 次：重连时间、断开后是否释放。", "按住按鈕時中斷並恢復連線 N 次：重新連線時間、中斷後是否放開。", "ボタンを押したまま接続を N 回切断・復帰：再接続時間、切断後の解放。"),
    };

    private void PaintLeft(Graphics g)
    {
        Draw.Glass(g, _leftCard, S(20));
        bool idle = !_engine.Running;
        Draw.Text(g, T("TEST", "TEST", "测试", "測試", "テスト"), _captionFont, _testCaption, Theme.AccentLight, TextFormatFlags.SingleLine);
        for (int i = 0; i < 6; i++)
        {
            var test = LabTests.All[i];
            Chip(g, _testChips[i], TestLabels[i], _test == test, idle, () => { _test = test; ApplyTestParams(); Invalidate(); });
        }
        Draw.Text(g, TestDescription(_test), _smallFont, _testDesc, Theme.Muted, TextFormatFlags.WordBreak | TextFormatFlags.EndEllipsis);

        Draw.Text(g, T("COLLEGAMENTO", "LINK", "连接", "連線", "接続"), _captionFont, _linkCaption, Theme.AccentLight, TextFormatFlags.SingleLine);
        string[] linkLabels = ["USB", "Wi‑Fi", "Bluetooth"];
        for (int i = 0; i < 3; i++)
        {
            var link = LabLinks.All[i];
            Chip(g, _linkChips[i], linkLabels[i], _link == link, idle, () => { _link = link; Invalidate(); });
        }

        Draw.Text(g, T("PARAMETRI", "PARAMETERS", "参数", "參數", "パラメーター"), _captionFont, _paramCaption, Theme.AccentLight, TextFormatFlags.SingleLine);
        string[] labels =
        [
            _test == LabTests.Latency ? T("Impulsi", "Pulses", "次数", "次數", "回数") : T("Passi / cicli", "Steps / cycles", "步数 / 循环", "步數 / 循環", "ステップ / 回"),
            "Seed",
            T("Frequenza (Hz)", "Rate (Hz)", "频率 (Hz)", "頻率 (Hz)", "レート (Hz)"),
            T("Secondi", "Seconds", "秒", "秒", "秒"),
        ];
        var boxes = new[] { _count, _seed, _rate, _seconds };
        for (int i = 0; i < 4; i++)
        {
            Draw.Text(g, labels[i], _bodyFont, _paramLabels[i], boxes[i].Enabled ? Theme.Text : Theme.Faint, TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine);
            Draw.Well(g, _paramWells[i], S(10));
        }
        Draw.Text(g, T("Vuoto = predefinito dell'app (seed: casuale, salvato)", "Empty = app default (seed: random, saved)", "留空 = 应用默认值（种子：随机并保存）", "留空 = App 預設值（種子：隨機並儲存）", "空欄 = アプリの既定値（シード：ランダム、保存）"),
            _smallFont, _hintRect, Theme.Faint, TextFormatFlags.SingleLine | TextFormatFlags.EndEllipsis);

        // Progress.
        var p = _engine.Progress;
        double fraction = _engine.Running ? p.Fraction : p.Phase == "done" ? 1 : 0;
        using (var track = Draw.Rounded(_progressRect, _progressRect.Height / 2f))
        using (var trackBrush = new SolidBrush(Color.FromArgb(22, Color.White)))
            g.FillPath(trackBrush, track);
        if (fraction > 0)
        {
            var bar = new RectangleF(_progressRect.X, _progressRect.Y, Math.Max(_progressRect.Height, (float)(_progressRect.Width * fraction)), _progressRect.Height);
            using var path = Draw.Rounded(bar, bar.Height / 2);
            using var brush = new System.Drawing.Drawing2D.LinearGradientBrush(new RectangleF(bar.X - 1, bar.Y, bar.Width + 2, bar.Height), Theme.PrimaryTop, Theme.GlowTeal, 0f);
            g.FillPath(brush, path);
        }

        int lineH = _liveRect.Height / 6;
        int half = _liveRect.Width / 2;
        void Line(int row, string key, string value, Color? color = null)
        {
            var r = new Rectangle(_liveRect.X, _liveRect.Y + row * lineH, _liveRect.Width, lineH);
            Draw.Text(g, key, _bodyFont, new Rectangle(r.X, r.Y, half, r.Height), Theme.Muted, TextFormatFlags.SingleLine);
            Draw.Text(g, value, _strongFont, new Rectangle(r.X + half, r.Y, half, r.Height), color ?? Theme.Text, TextFormatFlags.SingleLine | TextFormatFlags.Right | TextFormatFlags.EndEllipsis);
        }
        Line(0, T("Fase", "Phase", "阶段", "階段", "段階"), PhaseText(p.Phase), _engine.Running ? Theme.AccentLight : Theme.Text);
        Line(1, T("Tempo", "Time", "时间", "時間", "時間"),
            p.StartedTicks == 0 ? "—" : $"{p.ElapsedSeconds:0} s / ~{p.EstimatedSeconds:0} s");
        Line(2, T("Passo (recv)", "Step (recv)", "步 (recv)", "步 (recv)", "ステップ (recv)"),
            p.LastSeq < 0 ? "—" : p.ExpectedSteps > 0 ? $"{p.LastSeq + 1} / {p.ExpectedSteps}" : $"{p.LastSeq + 1}");
        Line(3, "recv / submit", $"{p.Recv:N0} / {p.Submit:N0}" + (p.Dropped > 0 ? $"  (−{p.Dropped})" : ""));
        Line(4, "obs", p.PollHz > 0 ? $"{p.Obs:N0} · {p.PollHz / 1000:0.#} kHz poll" : "—");
        Line(5, "recv→submit P50", double.IsNaN(p.RecvToSubmitUs) ? "—" : $"{p.RecvToSubmitUs:0} µs");

        if (_message.Length > 0)
            Draw.Text(g, _message, _smallFont, _messageRect, _messageIsError ? Theme.Danger : Theme.Muted, TextFormatFlags.WordBreak | TextFormatFlags.EndEllipsis);
    }

    private string PhaseText(string phase) => phase switch
    {
        "" => T("In attesa", "Idle", "空闲", "閒置", "待機中"),
        "adb" => T("Controllo del telefono…", "Checking the phone…", "检查手机…", "檢查手機…", "スマホを確認中…"),
        "sync" => T("Sincronizzo gli orologi…", "Syncing clocks…", "同步时钟…", "同步時鐘…", "時計を同期中…"),
        "running" => T("In esecuzione sul telefono…", "Running on the phone…", "手机上运行中…", "手機上執行中…", "スマホで実行中…"),
        "finishing" => T("Ultimi stati…", "Last states…", "最后的状态…", "最後的狀態…", "最後の状態…"),
        "collect" => T("Raccolgo i file…", "Collecting files…", "收集文件…", "收集檔案…", "ファイルを収集中…"),
        "analyse" => T("Analizzo…", "Analysing…", "分析中…", "分析中…", "分析中…"),
        "done" => T("Fatto", "Done", "完成", "完成", "完了"),
        "stopped" => T("Fermato", "Stopped", "已停止", "已停止", "停止しました"),
        "error" => T("Errore", "Error", "错误", "錯誤", "エラー"),
        _ => phase,
    };

    private void PaintRight(Graphics g)
    {
        Draw.Glass(g, _rightCard, S(20));
        Chip(g, _tabResults, ResultsTab, _view == View.Results, true, () => SetView(View.Results));
        Chip(g, _tabHistory, HistoryTab, _view == View.History, true, () => SetView(View.History));

        var oldClip = g.Clip;
        g.SetClip(_content);
        int y0 = _content.Y - _scroll;
        int end = _view == View.Results ? PaintResults(g, y0) : PaintHistory(g, y0);
        _contentHeight = end - y0;
        g.Clip = oldClip;

        // Scroll hint: a thin thumb on the right edge of the content.
        if (_contentHeight > _content.Height)
        {
            float frac = _content.Height / (float)_contentHeight;
            float h = Math.Max(S(24), _content.Height * frac);
            float t = _content.Y + (_content.Height - h) * (_scroll / (float)Math.Max(1, _contentHeight - _content.Height));
            using var thumb = Draw.Rounded(new RectangleF(_rightCard.Right - S(10), t, S(4), h), S(2));
            using var brush = new SolidBrush(Color.FromArgb(60, Color.White));
            g.FillPath(brush, thumb);
        }
    }

    private void SetView(View view)
    {
        _view = view;
        _scroll = 0;
        if (view == View.History) ReloadHistory();
        DoLayout();
    }

    // ------------------------------------------------------------------ results

    private int PaintResults(Graphics g, int y)
    {
        var c = _content;
        var s = _summary;
        if (s is null)
        {
            LabCharts.Text(g, T("Avvia un test o aprine uno dalla cronologia.", "Run a test or open one from the history.", "运行测试，或从历史中打开一个。", "執行測試，或從歷史中開啟一個。", "テストを実行するか、履歴から開いてください。"),
                _bodyFont, new Rectangle(c.X, y + S(40), c.Width, S(30)), Theme.Faint, TextFormatFlags.HorizontalCenter);
            return y + S(80);
        }
        int bodyH = Draw.Measure("X", _bodyFont).Height;
        int smallH = Draw.Measure("X", _smallFont).Height;

        // Title lines.
        LabCharts.Text(g, s.RunId, _cardTitleFont, new Rectangle(c.X, y, c.Width, S(24)), Theme.Text, TextFormatFlags.EndEllipsis);
        y += S(26);
        var e = _summaryEntry;
        var info = new List<string>();
        if (e is not null)
        {
            if (e.AppVersion.Length > 0) info.Add($"app {e.AppVersion}" + (e.AppCommit.Length > 0 ? $" ({e.AppCommit})" : ""));
            if (e.Device.Length > 0) info.Add(e.Device);
            if (e.Status.Length > 0 && e.Status != "ok") info.Add(e.Status);
        }
        info.Add(s.Sync.Available ? $"sync ±{LabCharts.Ms(s.Sync.UncertaintyMs)} ms (RTT min {LabCharts.Ms(s.Sync.RttMinMs)}, drift {LabCharts.Ms(s.Sync.DriftMs)} ms)" : "no sync");
        if (s.Rates.ObserverPollHz > 0) info.Add($"observer {s.Rates.ObserverPollHz / 1000:0.#} kHz");
        LabCharts.Text(g, string.Join("  ·  ", info), _smallFont, new Rectangle(c.X, y, c.Width, smallH), Theme.Muted, TextFormatFlags.EndEllipsis);
        y += smallH + S(6);
        foreach (var note in s.Notes)
        {
            LabCharts.Text(g, "• " + note, _smallFont, new Rectangle(c.X, y, c.Width, smallH), Theme.Warning, TextFormatFlags.EndEllipsis);
            y += smallH + S(2);
        }
        y += S(10);

        // Headline tiles.
        var h = s.Headline;
        var tiles = new (string Label, string Value, string Tag, Color Color)[]
        {
            ($"{h.Stage} P50", h.N > 0 ? $"{LabCharts.Ms(h.P50Ms)} ms" : "—", h.Kind, LabCharts.KindColor(h.Kind)),
            ($"{h.Stage} P99", h.N > 0 ? $"{LabCharts.Ms(h.P99Ms)} ms" : "—", h.Kind, LabCharts.KindColor(h.Kind)),
            (T("Persi", "Lost", "丢失", "遺失", "欠落"), $"{h.Lost} / {h.Expected}", "measured", h.Lost > 0 ? Theme.Warning : Theme.Success),
            (T("Incertezza sync", "Sync uncertainty", "同步不确定度", "同步不確定度", "同期の不確かさ"), s.Sync.Available ? $"±{LabCharts.Ms(s.Sync.UncertaintyMs)} ms" : "—", "estimated", LabCharts.Estimated),
        };
        int tileGap = S(10);
        int tileW = (c.Width - 3 * tileGap) / 4, tileH = S(78);
        for (int i = 0; i < tiles.Length; i++)
        {
            var r = new Rectangle(c.X + i * (tileW + tileGap), y, tileW, tileH);
            Draw.Glass(g, r, S(14), 16);
            LabCharts.Text(g, tiles[i].Label, _smallFont, new Rectangle(r.X + S(14), r.Y + S(10), r.Width - S(28), smallH), Theme.Muted, TextFormatFlags.EndEllipsis);
            LabCharts.Text(g, tiles[i].Value, _bigFont, new Rectangle(r.X + S(14), r.Y + S(10) + smallH + S(4), r.Width - S(28), S(32)), tiles[i].Color, TextFormatFlags.EndEllipsis);
            if (tiles[i].Tag.Length > 0) DrawTag(g, new Point(r.Right - S(12), r.Y + S(10)), tiles[i].Tag, alignRight: true);
        }
        y += tileH + S(16);

        // Stage chips (choose what the histogram and CDF show).
        if (s.Stages.Count > 0)
        {
            var stage = s.Stage(_chartStage ?? "") ?? s.Stage(h.Stage) ?? s.Stages[0];
            int cx = c.X;
            foreach (var st in s.Stages)
            {
                int w = Draw.Measure(st.Name, _bodyFont).Width + S(28);
                var r = new Rectangle(cx, y, w, S(28));
                var name = st.Name;
                Chip(g, r, st.Name, st.Name == stage.Name, true, () => { _chartStage = name; Invalidate(); }, _bodyFont);
                _hits[^1] = _hits[^1] with { InContent = true };
                cx += w + S(6);
            }
            y += S(28) + S(12);

            int chartGap = S(12);
            int chartW = (c.Width - chartGap) / 2, chartH = S(210);
            string tag = stage.Kind == "estimated" ? $" · estimated ±{LabCharts.Ms(stage.UncertaintyMs)} ms" : " · measured";
            LabCharts.Histogram(g, new Rectangle(c.X, y, chartW, chartH), $"{T("ISTOGRAMMA", "HISTOGRAM", "直方图", "直方圖", "ヒストグラム")} · {stage.Name}{tag}", stage, _captionFont, _smallFont, _scale);
            LabCharts.Cdf(g, new Rectangle(c.X + chartW + chartGap, y, chartW, chartH), $"CDF · {stage.Name}{tag}", stage, _captionFont, _smallFont, _scale);
            y += chartH + chartGap;
            var seriesKind = s.Stage(s.Series.Stage)?.Kind ?? "measured";
            LabCharts.Series(g, new Rectangle(c.X, y, chartW, chartH), $"{T("NEL TEMPO", "OVER TIME", "随时间", "隨時間", "時系列")} · {s.Series.Stage}", s.Series, seriesKind, _captionFont, _smallFont, _scale);
            LabCharts.StageBars(g, new Rectangle(c.X + chartW + chartGap, y, chartW, chartH), T("PER FASE · P50, P95, P99", "PER STAGE · P50, P95, P99", "各阶段 · P50、P95、P99", "各階段 · P50、P95、P99", "段階別 · P50・P95・P99"), s.Stages, _captionFont, _smallFont, _scale);
            y += chartH + S(18);

            y = PaintStageTable(g, s, y);
        }
        y = PaintOtherMetrics(g, s, y + S(14));
        return y + S(10);
    }

    /// <summary>Small "measured"/"estimated" tag.</summary>
    private void DrawTag(Graphics g, Point at, string kind, bool alignRight = false)
    {
        var text = kind == "estimated" ? T("stimato", "estimated", "估计", "估計", "推定") : T("misurato", "measured", "测量", "量測", "実測");
        var size = Draw.Measure(text, _smallFont);
        var r = new Rectangle(alignRight ? at.X - size.Width - S(14) : at.X, at.Y, size.Width + S(14), size.Height + S(4));
        var color = LabCharts.KindColor(kind);
        using (var path = Draw.Rounded(r, r.Height / 2f))
        using (var fill = new SolidBrush(Color.FromArgb(36, color)))
            g.FillPath(fill, path);
        LabCharts.Text(g, text, _smallFont, r, color, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
    }

    private int PaintStageTable(Graphics g, LabSummary s, int y)
    {
        var c = _content;
        string[] head = ["", "", "n", "P50", "P90", "P95", "P99", T("media", "mean", "均值", "平均", "平均"), "σ", "min", "max", T("IC 95% mediana", "median 95% CI", "中位数 95% CI", "中位數 95% CI", "中央値 95% CI")];
        float[] weights = [1.4f, 1.2f, 0.7f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 1.6f];
        float total = weights.Sum();
        var xs = new int[weights.Length + 1];
        xs[0] = c.X + S(12);
        for (int i = 0; i < weights.Length; i++) xs[i + 1] = xs[i] + (int)((c.Width - S(24)) * weights[i] / total);
        int rowH = S(30);
        Draw.Well(g, new Rectangle(c.X, y, c.Width, rowH * (s.Stages.Count + 1) + S(8)), S(12));
        y += S(4);
        for (int i = 0; i < head.Length; i++)
            LabCharts.Text(g, head[i], _captionFont, new Rectangle(xs[i], y, xs[i + 1] - xs[i] - S(6), rowH), Theme.Faint,
                TextFormatFlags.VerticalCenter | (i >= 2 ? TextFormatFlags.Right : 0));
        y += rowH;
        foreach (var st in s.Stages)
        {
            string ci = st.MedianCi95Ms.Length == 2 ? $"{LabCharts.Ms(st.MedianCi95Ms[0])} – {LabCharts.Ms(st.MedianCi95Ms[1])}" : "—";
            string[] cells = ["", "", st.N.ToString("N0", CultureInfo.CurrentCulture), LabCharts.Ms(st.P50Ms), LabCharts.Ms(st.P90Ms), LabCharts.Ms(st.P95Ms),
                LabCharts.Ms(st.P99Ms), LabCharts.Ms(st.MeanMs), LabCharts.Ms(st.StdevMs), LabCharts.Ms(st.MinMs), LabCharts.Ms(st.MaxMs), ci];
            LabCharts.Text(g, st.Name, _strongFont, new Rectangle(xs[0], y, xs[1] - xs[0], rowH), Theme.Text, TextFormatFlags.VerticalCenter);
            DrawTag(g, new Point(xs[1], y + (rowH - Draw.Measure("X", _smallFont).Height - S(4)) / 2), st.Kind);
            for (int i = 2; i < cells.Length; i++)
                LabCharts.Text(g, cells[i], _monoFont, new Rectangle(xs[i], y, xs[i + 1] - xs[i] - S(6), rowH), i == 3 ? Theme.Text : Theme.Muted,
                    TextFormatFlags.VerticalCenter | TextFormatFlags.Right);
            y += rowH;
        }
        var estimated = s.Stages.FirstOrDefault(x => x.Kind == "estimated");
        y += S(10);
        if (estimated is not null)
        {
            LabCharts.Text(g, T($"Le fasi stimate attraversano telefono→PC: incertezza ±{LabCharts.Ms(estimated.UncertaintyMs)} ms (RTT min/2 + deriva). Tutti i valori in ms.",
                    $"Estimated stages cross phone→PC: uncertainty ±{LabCharts.Ms(estimated.UncertaintyMs)} ms (min RTT/2 + drift). All values in ms.",
                    $"估计阶段跨越手机→电脑：不确定度 ±{LabCharts.Ms(estimated.UncertaintyMs)} ms（最小 RTT/2 + 漂移）。单位均为 ms。",
                    $"估計階段跨越手機→電腦：不確定度 ±{LabCharts.Ms(estimated.UncertaintyMs)} ms（最小 RTT/2 + 漂移）。單位皆為 ms。",
                    $"推定の段階はスマホ→PC をまたぐ：不確かさ ±{LabCharts.Ms(estimated.UncertaintyMs)} ms（最小 RTT/2 + ドリフト）。単位はすべて ms。"),
                _smallFont, new Rectangle(c.X, y, c.Width, S(18)), Theme.Faint, TextFormatFlags.EndEllipsis);
            y += S(18);
        }
        return y;
    }

    private int PaintOtherMetrics(Graphics g, LabSummary s, int y)
    {
        var c = _content;
        var items = new List<(string Key, string Value, bool Warn)>();
        var l = s.Loss;
        items.Add((T("Passi comandati", "Steps commanded", "指令步数", "指令步數", "指令ステップ"), l.Expected.ToString("N0"), false));
        items.Add(("send / recv / submit / obs", $"{l.Sent:N0} / {l.Received:N0} / {l.Submitted:N0} / {(l.ObserverRan ? l.Observed.ToString("N0") : "—")}", false));
        items.Add((T("Persi prima di recv", "Lost before recv", "recv 前丢失", "recv 前遺失", "recv 前の欠落"), l.LostBeforeRecv.ToString("N0"), l.LostBeforeRecv > 0));
        if (l.ObserverRan) items.Add((T("Persi prima di obs", "Lost before obs", "obs 前丢失", "obs 前遺失", "obs 前の欠落"), $"{l.LostBeforeObs:N0} ({l.LostPct:0.##}%)", l.LostBeforeObs > 0));
        items.Add((T("Duplicati (send / recv)", "Duplicates (send / recv)", "重复 (send / recv)", "重複 (send / recv)", "重複 (send / recv)"), $"{l.DuplicatesSend} / {l.DuplicatesRecv}", l.DuplicatesRecv > 0));
        items.Add((T("Fuori ordine", "Out of order", "乱序", "亂序", "順序違い"), l.OutOfOrder.ToString("N0"), l.OutOfOrder > 0));
        items.Add((T("Senza marca (recv / obs)", "Unmarked (recv / obs)", "无标记 (recv / obs)", "無標記 (recv / obs)", "マークなし (recv / obs)"), $"{l.UnmarkedRecv} / {l.UnmarkedObs}", false));
        items.Add((T("Bloccati alla fine", "Stuck at the end", "结束时卡住", "結束時卡住", "終了時の押しっぱなし"),
            (l.Stuck ? T("sì", "yes", "是", "是", "はい") : T("no", "no", "否", "否", "いいえ")) + $" (submit {Dash(l.FinalSubmitButtons)}, obs {Dash(l.FinalObsButtons)})", l.Stuck));
        var r = s.Rates;
        items.Add((T("Frequenza send / recv", "Rate send / recv", "频率 send / recv", "頻率 send / recv", "レート send / recv"), $"{r.SendHz:0.#} / {r.RecvHz:0.#} Hz", false));
        items.Add((T("Frequenza submit / obs", "Rate submit / obs", "频率 submit / obs", "頻率 submit / obs", "レート submit / obs"), $"{r.SubmitHz:0.#} / {r.ObsHz:0.#} Hz", false));
        items.Add((T("Polling osservatore", "Observer polling", "观察器轮询", "觀察器輪詢", "オブザーバーのポーリング"), r.ObserverPollHz > 0 ? $"{r.ObserverPollHz:N0} Hz" : "—", false));
        var i = s.Integrity;
        if (i.ComparedRecv > 0)
            items.Add((T("Integri a recv", "Intact at recv", "recv 完整", "recv 完整", "recv で完全"), $"{i.IntactRecv:N0} / {i.ComparedRecv:N0} ({i.IntactRecvPct:0.##}%)" + Mismatch(i.MismatchRecv), i.IntactRecv < i.ComparedRecv));
        if (i.ComparedObs > 0)
            items.Add((T("Integri a obs", "Intact at obs", "obs 完整", "obs 完整", "obs で完全"), $"{i.IntactObs:N0} / {i.ComparedObs:N0} ({i.IntactObsPct:0.##}%)" + Mismatch(i.MismatchObs), i.IntactObs < i.ComparedObs));
        foreach (var a in s.Axes)
        {
            var text = $"{T("passo", "step", "步长", "步長", "刻み")} {a.ResolutionObserved}, {a.DistinctValues:N0} {T("valori", "values", "个值", "個值", "値")}";
            if (a.Compared > 0) text += $", |err| {a.MeanAbsError:0.#} (P95 {a.P95AbsError:0.#}, max {a.MaxAbsError:0})";
            items.Add(($"{T("Asse", "Axis", "轴", "軸", "軸")} {a.Axis}", text, a.ResolutionObserved > 1));
        }
        if (s.Reconnect is { } rc)
        {
            var times = rc.PhoneDownToUpMs.Count > 0 ? rc.PhoneDownToUpMs : rc.LinkDownToUpMs.Count > 0 ? rc.LinkDownToUpMs : rc.ObsGoneToBackMs;
            items.Add((T("Riconnessione (media / max)", "Reconnect (mean / max)", "重连（平均 / 最大）", "重新連線（平均 / 最大）", "再接続（平均 / 最大）"),
                times.Count > 0 ? $"{LabCharts.Ms(rc.MeanMs)} / {LabCharts.Ms(rc.MaxMs)} ms ({times.Count})" : "—", false));
            if (rc.Cycles > 0)
                items.Add((T("Cicli riconnessi", "Cycles reconnected", "已重连的循环", "已重新連線的循環", "再接続したサイクル"), $"{rc.Reconnected} / {rc.Cycles}", rc.Reconnected < rc.Cycles));
            items.Add((T("Rilasciato dopo la caduta", "Released after the drop", "断开后已释放", "中斷後已放開", "切断後に解放"),
                $"obs {rc.ReleasedAfterDrop} / {rc.Drops}" + (rc.Cycles > 0 ? $", app {rc.ReleasedAfterReconnectPhone} / {rc.Cycles}" : ""), rc.ReleasedAfterDrop < rc.Drops));
        }

        int cols = c.Width > S(760) ? 2 : 1;
        int colW = (c.Width - (cols - 1) * S(16)) / cols;
        int rowH = Draw.Measure("X", _bodyFont).Height + S(10);
        int rows = (items.Count + cols - 1) / cols;
        LabCharts.Text(g, T("ALTRE MISURE", "OTHER MEASUREMENTS", "其他测量", "其他量測", "その他の計測"), _captionFont, new Rectangle(c.X, y, c.Width, S(18)), Theme.AccentLight);
        LabCharts.Text(g, T("tutte misurate", "all measured", "均为测量值", "皆為量測值", "すべて実測"), _smallFont, new Rectangle(c.X, y, c.Width, S(18)), Theme.Faint, TextFormatFlags.Right);
        y += S(24);
        for (int k = 0; k < items.Count; k++)
        {
            int col = k / rows, row = k % rows;
            var rect = new Rectangle(c.X + col * (colW + S(16)), y + row * rowH, colW, rowH);
            using (var line = new Pen(Color.FromArgb(18, Color.White)))
                g.DrawLine(line, rect.X, rect.Bottom - 1, rect.Right, rect.Bottom - 1);
            int keyW = colW * 42 / 100;
            LabCharts.Text(g, items[k].Key, _bodyFont, new Rectangle(rect.X, rect.Y, keyW, rect.Height), Theme.Muted, TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
            LabCharts.Text(g, items[k].Value, _bodyFont, new Rectangle(rect.X + keyW, rect.Y, colW - keyW, rect.Height), items[k].Warn ? Theme.Warning : Theme.Text,
                TextFormatFlags.VerticalCenter | TextFormatFlags.Right | TextFormatFlags.EndEllipsis);
        }
        return y + rows * rowH;
    }

    private static string Dash(string s) => s.Length == 0 ? "—" : s;

    private static string Mismatch(Dictionary<string, int> m) =>
        m.Count == 0 ? "" : " · " + string.Join(", ", m.OrderByDescending(kv => kv.Value).Take(3).Select(kv => $"{kv.Key} {kv.Value}"));

    // ------------------------------------------------------------------ history

    private int PaintHistory(Graphics g, int y)
    {
        var c = _content;
        if (_history.Count == 0)
        {
            LabCharts.Text(g, T("Nessuna esecuzione salvata.", "No saved runs yet.", "还没有保存的运行。", "尚無已儲存的執行。", "保存された実行はまだありません。"),
                _bodyFont, new Rectangle(c.X, y + S(40), c.Width, S(30)), Theme.Faint, TextFormatFlags.HorizontalCenter);
            return y + S(80);
        }
        LabCharts.Text(g, T("Seleziona due esecuzioni per confrontarle; doppio clic per aprirne una.", "Select two runs to compare them; double-click to open one.",
                "选择两次运行进行比较；双击打开。", "選擇兩次執行進行比較；按兩下開啟。", "2 つ選んで比較、ダブルクリックで開く。"),
            _smallFont, new Rectangle(c.X, y, c.Width, S(18)), Theme.Faint);
        y += S(24);

        string[] head = ["", T("Quando", "When", "时间", "時間", "日時"), T("Test", "Test", "测试", "測試", "テスト"), T("Link", "Link", "连接", "連線", "接続"),
            "App", T("Fase", "Stage", "阶段", "階段", "段階"), "P50", "P99", T("Persi", "Lost", "丢失", "遺失", "欠落"), "± sync", T("Stato", "Status", "状态", "狀態", "状態")];
        float[] weights = [0.4f, 1.5f, 1.1f, 0.6f, 1.6f, 1.1f, 0.8f, 0.8f, 0.9f, 0.8f, 0.9f];
        float total = weights.Sum();
        var xs = new int[weights.Length + 1];
        xs[0] = c.X + S(10);
        for (int i = 0; i < weights.Length; i++) xs[i + 1] = xs[i] + (int)((c.Width - S(20)) * weights[i] / total);
        int rowH = S(32);
        for (int i = 1; i < head.Length; i++)
            LabCharts.Text(g, head[i], _captionFont, new Rectangle(xs[i], y, xs[i + 1] - xs[i] - S(6), rowH), Theme.Faint,
                TextFormatFlags.VerticalCenter | (i >= 6 && i <= 9 ? TextFormatFlags.Right : 0));
        y += rowH;
        foreach (var e in _history)
        {
            var row = new Rectangle(c.X, y, c.Width, rowH - S(4));
            int sel = _selected.IndexOf(e.Id);
            bool current = _summaryEntry?.Id == e.Id;
            using (var path = Draw.Rounded(row, S(10)))
            using (var fill = new SolidBrush(sel >= 0 ? Color.FromArgb(70, Theme.Accent) : current ? Color.FromArgb(22, Color.White) : Color.FromArgb(10, Color.White)))
                g.FillPath(fill, path);
            // Checkbox with A/B letter.
            var box = new Rectangle(xs[0] + S(2), row.Y + (row.Height - S(18)) / 2, S(18), S(18));
            using (var bp = Draw.Rounded(box, S(5)))
            {
                using var bf = new SolidBrush(sel >= 0 ? Theme.Accent : Color.FromArgb(20, Color.White));
                g.FillPath(bf, bp);
                using var pen = new Pen(Color.FromArgb(80, Color.White));
                g.DrawPath(pen, bp);
            }
            if (sel >= 0) LabCharts.Text(g, sel == 0 ? "A" : "B", _captionFont, box, Color.White, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
            var hl = e.Headline;
            string[] cells =
            [
                "", e.When.ToString("g", CultureInfo.CurrentCulture), e.Test, e.Link,
                e.AppVersion.Length > 0 ? e.AppVersion + (e.AppCommit.Length > 0 ? $" · {e.AppCommit}" : "") : "—",
                hl?.Stage ?? "—",
                hl is { N: > 0 } ? LabCharts.Ms(hl.P50Ms) : "—",
                hl is { N: > 0 } ? LabCharts.Ms(hl.P99Ms) : "—",
                hl is null ? "—" : $"{hl.Lost}/{hl.Expected}",
                e.SyncUncertaintyMs > 0 ? LabCharts.Ms(e.SyncUncertaintyMs) : "—",
                e.Status,
            ];
            for (int i = 1; i < cells.Length; i++)
            {
                var color = i == 10 && e.Status != "ok" ? Theme.Warning : i == 6 ? Theme.Text : Theme.Muted;
                LabCharts.Text(g, cells[i], i >= 6 && i <= 9 ? _monoFont : _bodyFont, new Rectangle(xs[i], row.Y, xs[i + 1] - xs[i] - S(6), row.Height), color,
                    TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis | (i >= 6 && i <= 9 ? TextFormatFlags.Right : 0));
            }
            var entry = e;
            _hits.Add(new Hit(row, () => ToggleSelect(entry.Id), () => { LoadRun(entry); SetView(View.Results); }, true));
            y += rowH;
        }

        if (_compare is { Count: > 0 })
        {
            y += S(18);
            LabCharts.Text(g, T("CONFRONTO", "COMPARISON", "比较", "比較", "比較"), _captionFont, new Rectangle(c.X, y, c.Width, S(18)), Theme.AccentLight);
            y += S(20);
            LabCharts.Text(g, _compareTitle, _smallFont, new Rectangle(c.X, y, c.Width, S(18)), Theme.Muted, TextFormatFlags.EndEllipsis);
            y += S(24);
            string[] ch = [T("Misura", "Metric", "指标", "指標", "指標"), "", "A", "B", "Δ (B − A)", "Δ %"];
            float[] cw = [2.6f, 1.1f, 1f, 1f, 1f, 0.9f];
            float ct = cw.Sum();
            var cx = new int[cw.Length + 1];
            cx[0] = c.X + S(12);
            for (int i = 0; i < cw.Length; i++) cx[i + 1] = cx[i] + (int)((c.Width - S(24)) * cw[i] / ct);
            int crow = S(28);
            Draw.Well(g, new Rectangle(c.X, y, c.Width, crow * (_compare.Count + 1) + S(8)), S(12));
            y += S(4);
            for (int i = 0; i < ch.Length; i++)
                LabCharts.Text(g, ch[i], _captionFont, new Rectangle(cx[i], y, cx[i + 1] - cx[i] - S(6), crow), Theme.Faint,
                    TextFormatFlags.VerticalCenter | (i >= 2 ? TextFormatFlags.Right : 0));
            y += crow;
            foreach (var row in _compare)
            {
                // Lower is better, except rates and intact %.
                bool higherBetter = row.Metric.Contains("Hz") || row.Metric.Contains("intact");
                var delta = row.Delta;
                var deltaColor = delta is null || Math.Abs(delta.Value) < 1e-9 ? Theme.Muted
                    : (delta.Value < 0) ^ higherBetter ? Theme.Success : Theme.Danger;
                LabCharts.Text(g, row.Metric, _bodyFont, new Rectangle(cx[0], y, cx[1] - cx[0], crow), Theme.Text, TextFormatFlags.VerticalCenter | TextFormatFlags.EndEllipsis);
                DrawTag(g, new Point(cx[1], y + (crow - Draw.Measure("X", _smallFont).Height - S(4)) / 2), row.Kind);
                string[] vals = [Num(row.A), Num(row.B), delta is { } d ? (d > 0 ? "+" : "") + Num(d) : "—", row.DeltaPct is { } dp ? $"{(dp > 0 ? "+" : "")}{dp:0.#}%" : "—"];
                for (int i = 0; i < vals.Length; i++)
                    LabCharts.Text(g, vals[i], _monoFont, new Rectangle(cx[i + 2], y, cx[i + 3] - cx[i + 2] - S(6), crow), i >= 2 ? deltaColor : Theme.Muted,
                        TextFormatFlags.VerticalCenter | TextFormatFlags.Right);
                y += crow;
            }
        }
        return y + S(10);
    }

    private static string Num(double? v) => v is { } x ? LabCharts.Ms(x) : "—";

    private void ToggleSelect(string id)
    {
        if (!_selected.Remove(id))
        {
            _selected.Add(id);
            if (_selected.Count > 2) _selected.RemoveAt(0);
        }
        _compare = null;
        UpdateButtons();
        Invalidate();
    }

    // ------------------------------------------------------------------ input

    protected override void OnMouseClick(MouseEventArgs e)
    {
        base.OnMouseClick(e);
        if (e.Button != MouseButtons.Left) return;
        FindHit(e.Location)?.Click?.Invoke();
    }

    protected override void OnMouseDoubleClick(MouseEventArgs e)
    {
        base.OnMouseDoubleClick(e);
        if (e.Button != MouseButtons.Left) return;
        FindHit(e.Location)?.DoubleClick?.Invoke();
    }

    protected override void OnMouseMove(MouseEventArgs e)
    {
        base.OnMouseMove(e);
        Cursor = FindHit(e.Location) is { Click: not null } ? Cursors.Hand : Cursors.Default;
    }

    private Hit? FindHit(Point p)
    {
        for (int i = _hits.Count - 1; i >= 0; i--)
        {
            var h = _hits[i];
            if (h.InContent && !_content.Contains(p)) continue;
            if (h.Rect.Contains(p)) return h;
        }
        return null;
    }

    protected override void OnMouseWheel(MouseEventArgs e)
    {
        base.OnMouseWheel(e);
        if (!_rightCard.Contains(e.Location)) return;
        _scroll -= e.Delta * S(60) / 120;
        ClampScroll();
        Invalidate(_rightCard);
    }

    private void ClampScroll() => _scroll = Math.Clamp(_scroll, 0, Math.Max(0, _contentHeight - _content.Height));

    // ------------------------------------------------------------------ actions

    private void ApplyTestParams()
    {
        // Which parameters each test reads (SPEC §4); the others are greyed out.
        bool count = _test is LabTests.Latency or LabTests.Multi or LabTests.Reliability or LabTests.Reconnect;
        bool rate = _test is LabTests.Analog or LabTests.Throughput or LabTests.Reliability;
        bool seconds = _test is LabTests.Analog or LabTests.Throughput;
        _count.Enabled = count;
        _rate.Enabled = rate;
        _seconds.Enabled = seconds;
        _count.PlaceholderText = count ? LabEngine.DefaultCount(_test).ToString(CultureInfo.InvariantCulture) : "";
        _rate.PlaceholderText = _test switch { LabTests.Analog => "250", LabTests.Reliability => "500", LabTests.Throughput => T("ciclo chiuso", "closed loop", "闭环", "閉環", "クローズドループ"), _ => "" };
        _seconds.PlaceholderText = _test switch { LabTests.Analog => "8", LabTests.Throughput => "10", _ => "" };
        _seed.PlaceholderText = T("casuale", "random", "随机", "隨機", "ランダム");
        foreach (var box in new[] { _count, _rate, _seconds }) box.BackColor = box.Enabled ? Theme.Well : Theme.BackgroundLow;
    }

    private bool TryReadRequest(out LabRequest request, out string error)
    {
        request = new LabRequest { Test = _test, Link = _link };
        error = "";
        bool Int(TextBox box, string name, int min, int max, out int? value)
        {
            value = null;
            var text = box.Text.Trim();
            if (!box.Enabled || text.Length == 0) return true;
            if (int.TryParse(text, NumberStyles.Integer, CultureInfo.InvariantCulture, out var v) && v >= min && v <= max)
            {
                value = v;
                return true;
            }
            return false;
        }
        if (!Int(_count, "count", 1, 1_000_000, out var count)) { error = T("Numero non valido", "Invalid count", "次数无效", "次數無效", "回数が無効です"); return false; }
        if (!Int(_rate, "rate", 1, 10_000, out var rate)) { error = T("Frequenza non valida", "Invalid rate", "频率无效", "頻率無效", "レートが無効です"); return false; }
        if (!Int(_seconds, "seconds", 1, 3600, out var seconds)) { error = T("Secondi non validi", "Invalid seconds", "秒数无效", "秒數無效", "秒数が無効です"); return false; }
        long? seed = null;
        if (_seed.Text.Trim().Length > 0)
        {
            if (!long.TryParse(_seed.Text.Trim(), NumberStyles.Integer, CultureInfo.InvariantCulture, out var s)) { error = T("Seed non valido", "Invalid seed", "种子无效", "種子無效", "シードが無効です"); return false; }
            seed = s;
        }
        request.Count = count;
        request.RateHz = rate;
        request.Seconds = seconds;
        request.Seed = seed;
        return true;
    }

    private async void StartRun()
    {
        if (_engine.Running) return;
        if (!TryReadRequest(out var request, out var error))
        {
            SetMessage(error, true);
            return;
        }
        _stopCts = new CancellationTokenSource();
        SetMessage("", false);
        _timer.Start();
        UpdateButtons();
        Invalidate();
        LabRunResult result;
        try
        {
            result = await Task.Run(() => _engine.RunAsync(request, _stopCts.Token));
        }
        catch (Exception e)
        {
            result = new LabRunResult { Status = "error", Error = e.Message };
        }
        _timer.Stop();
        UpdateButtons();
        if (result.Summary is not null && result.Folder.Length > 0)
        {
            ReloadHistory();
            LoadRun(LabHistory.ReadEntry(result.Folder), result.Summary);
            _view = View.Results;
            _scroll = 0;
        }
        SetMessage(result.Error is { Length: > 0 } err ? err : result.Status == "ok" ? "" : $"status: {result.Status}", result.Status != "ok");
        DoLayout();
    }

    private void SetMessage(string message, bool error)
    {
        _message = message;
        _messageIsError = error;
        Invalidate();
    }

    private void UpdateButtons()
    {
        bool running = _engine.Running;
        _run.Enabled = !running && _adb.AdbPath is not null;
        _stop.Enabled = running;
        foreach (var box in new[] { _count, _seed, _rate, _seconds }) box.ReadOnly = running;
        bool haveRun = _summaryEntry is not null;
        _open.Enabled = _copyJson.Enabled = _export.Enabled = haveRun;
        _show.Enabled = _selected.Count >= 1;
        _compareButton.Enabled = _selected.Count == 2;
    }

    private void ReloadHistory()
    {
        _history = LabHistory.Load();
        _selected.RemoveAll(id => _history.All(h => h.Id != id));
        UpdateButtons();
        Invalidate();
    }

    private void LoadRun(LabRunEntry? entry, LabSummary? summary = null)
    {
        if (entry is null) return;
        _summaryEntry = entry;
        _summary = summary ?? LabHistory.LoadSummary(entry.Folder);
        _chartStage = _summary?.Headline.Stage;
        UpdateButtons();
        Invalidate();
    }

    private void ShowSelected()
    {
        var id = _selected.LastOrDefault();
        if (_history.FirstOrDefault(h => h.Id == id) is { } e)
        {
            LoadRun(e);
            SetView(View.Results);
        }
    }

    private void CompareSelected()
    {
        if (_selected.Count != 2) return;
        // A = the older run, B = the newer one.
        var pair = _selected.Select(id => _history.First(h => h.Id == id)).OrderBy(h => h.Id).ToList();
        var a = LabHistory.LoadSummary(pair[0].Folder);
        var b = LabHistory.LoadSummary(pair[1].Folder);
        if (a is null || b is null)
        {
            SetMessage(T("Una delle due esecuzioni non ha summary.json", "One of the runs has no summary.json", "其中一次运行没有 summary.json", "其中一次執行沒有 summary.json", "どちらかの実行に summary.json がありません"), true);
            return;
        }
        _compare = LabHistory.Compare(a, b);
        _compareTitle = $"A: {pair[0].Id} ({Version(pair[0])})   ·   B: {pair[1].Id} ({Version(pair[1])})";
        Invalidate();
    }

    private static string Version(LabRunEntry e) => e.AppVersion.Length == 0 ? "?" : e.AppVersion + (e.AppCommit.Length > 0 ? " " + e.AppCommit : "");

    private void OpenFolder()
    {
        if (_summaryEntry is null) return;
        try { Process.Start(new ProcessStartInfo("explorer.exe", $"\"{_summaryEntry.Folder}\"") { UseShellExecute = true }); }
        catch (Exception e) when (e is System.ComponentModel.Win32Exception or InvalidOperationException) { SetMessage(e.Message, true); }
    }

    private void CopyJson()
    {
        if (_summaryEntry is null) return;
        var path = Path.Combine(_summaryEntry.Folder, "summary.json");
        try
        {
            Clipboard.SetText(File.ReadAllText(path));
            SetMessage(T("summary.json copiato", "summary.json copied", "已复制 summary.json", "已複製 summary.json", "summary.json をコピーしました"), false);
        }
        catch (Exception e) when (e is IOException or System.Runtime.InteropServices.ExternalException) { SetMessage(e.Message, true); }
    }

    private void Export()
    {
        if (_summaryEntry is null) return;
        using var dialog = new FolderBrowserDialog { Description = "Mobilepad Lab", UseDescriptionForTitle = true };
        if (dialog.ShowDialog(this) != DialogResult.OK) return;
        try
        {
            var target = Path.Combine(dialog.SelectedPath, Path.GetFileName(_summaryEntry.Folder));
            Directory.CreateDirectory(target);
            foreach (var file in Directory.GetFiles(_summaryEntry.Folder))
                File.Copy(file, Path.Combine(target, Path.GetFileName(file)), overwrite: true);
            SetMessage(target, false);
        }
        catch (Exception e) when (e is IOException or UnauthorizedAccessException) { SetMessage(e.Message, true); }
    }

    // ------------------------------------------------------------------ engine hooks

    private ILabObserver? CreateObserver(string link, string mark)
    {
        if (link == LabLinks.Bluetooth)
            return OperatingSystem.IsWindowsVersionAtLeast(10, 0, 16299) ? new WgiObserver() : null;
        // The ViGEm pad of the session on this link; its slot comes from the LED feedback (−1 = find it by the mark).
        var session = _server.Sessions.FirstOrDefault(s => link == LabLinks.Usb ? s.IsUsb : !s.IsUsb);
        return new XInputObserver(session?.PlayerIndex ?? -1, mark);
    }

    private string? CheckLink(string link)
    {
        switch (link)
        {
            case LabLinks.Usb when !_server.Sessions.Any(s => s.IsUsb):
                return T("Collega prima il telefono via USB nell'app.", "Connect the phone over USB in the app first.", "请先在应用中通过 USB 连接手机。", "請先在 App 中透過 USB 連接手機。", "まずアプリで USB 接続してください。");
            case LabLinks.Wifi when !_server.Sessions.Any(s => !s.IsUsb):
                return T("Collega prima il telefono via Wi‑Fi nell'app (ADB serve comunque, anche via cavo).", "Connect the phone over Wi‑Fi in the app first (ADB is still needed, by cable is fine).",
                    "请先在应用中通过 Wi‑Fi 连接手机（仍需要 ADB，可用数据线）。", "請先在 App 中透過 Wi‑Fi 連接手機（仍需要 ADB，可用傳輸線）。", "まずアプリで Wi‑Fi 接続してください（ADB は必要、ケーブルで可）。");
            case LabLinks.Bluetooth:
                if (!OperatingSystem.IsWindowsVersionAtLeast(10, 0, 16299)) return "Windows.Gaming.Input needs Windows 10";
                // Windows enumerates game controllers lazily: give it a moment.
                for (int i = 0; i < 20; i++)
                {
                    if (WgiObserver.FindName() is not null) return null;
                    Thread.Sleep(100);
                }
                return T("Windows non vede il gamepad Bluetooth: associa Mobilepad via Bluetooth e attivalo nell'app.",
                    "Windows does not see the Bluetooth gamepad: pair Mobilepad over Bluetooth and turn it on in the app.",
                    "Windows 未检测到蓝牙手柄：请通过蓝牙配对 Mobilepad 并在应用中开启。", "Windows 未偵測到藍牙手把：請透過藍牙配對 Mobilepad 並在 App 中開啟。",
                    "Windows が Bluetooth ゲームパッドを認識していません：Mobilepad を Bluetooth でペアリングし、アプリで有効にしてください。");
        }
        return null;
    }

    /// <summary>The first access to Windows.Gaming.Input starts the enumeration; do it before a run needs it.</summary>
    private static void WarmUpGamingInput()
    {
        if (!OperatingSystem.IsWindowsVersionAtLeast(10, 0, 16299)) return;
        Task.Run(() =>
        {
            try { _ = WgiObserver.FindName(); } catch (Exception) { /* no WinRT gaming input on this system */ }
        });
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _timer.Dispose();
            _backdrop?.Dispose();
            _stopCts?.Dispose();
        }
        base.Dispose(disposing);
    }
}
