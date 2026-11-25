using QRCoder;

namespace Mobilepad.Companion;

/// <summary>
/// Companion window in the style of the Android app: indigo backdrop with glows, glass cards, icon tiles.
/// Everything except the log box and the buttons is painted here; <see cref="DoLayout"/> places it.
/// </summary>
public sealed class MainForm : Form
{
    private readonly CompanionServer _server;
    private readonly AdbBridge _adb;
    private readonly List<string> _addresses;
    private readonly Image _qr;
    private readonly string _version = Application.ProductVersion.Split('+')[0];

    private readonly TextBox _log = new();
    private readonly PillButton _copy = new();
    private readonly PillButton _clear = new();
    private readonly PillButton _labButton = new();
    private LabForm? _labForm;
    private readonly System.Windows.Forms.Timer _copiedTimer = new() { Interval = 1600 };

    // Strings.
    private readonly string _connectTitle = L.T("Collega il telefono", "Connect your phone", "连接手机", "連接手機", "スマホを接続");
    private readonly string _instructions = L.T("Nell'app: \"Scansiona QR\" o \"Cerca\", oppure inserisci",
        "In the app: \"Scan QR\" or \"Search\", or enter", "在应用中：点“扫描二维码”或“搜索”，或输入", "在 App 中：點「掃描 QR 碼」或「搜尋」，或輸入", "アプリで「QR を読み取る」か「検索」、または次を入力");
    private readonly string _devicesTitle = L.T("Dispositivi collegati", "Connected devices", "已连接的设备", "已連接的裝置", "接続中の機器");
    private readonly string _logTitle = L.T("REGISTRO", "LOG", "日志", "記錄", "ログ");
    private readonly string _copyText = L.T("Copia", "Copy", "复制", "複製", "コピー");
    private readonly string _copiedText = L.T("Copiato", "Copied", "已复制", "已複製", "コピーしました");

    // Fonts, rebuilt when the window moves to a monitor with another DPI.
    private float _scale = 1f;
    private Font _titleFont = null!, _subtitleFont = null!, _cardTitleFont = null!, _bodyFont = null!, _strongFont = null!,
        _captionFont = null!, _monoFont = null!, _addressFont = null!, _pillFont = null!, _iconFont = null!, _smallIconFont = null!;

    // Layout, in client pixels.
    private Bitmap? _backdrop;
    private Rectangle _headerTile, _titleRect, _subtitleRect, _pill;
    private Rectangle _connectCard, _connectTile, _connectTitleRect, _qrRect, _wifiCaption, _instructionsRect, _addressesRect;
    private Rectangle _usbCard, _usbTile, _usbCaption, _usbText;
    private Rectangle _devicesCard, _devicesTile, _devicesTitleRect, _countRect;
    private readonly List<Rectangle> _rows = [];
    private Rectangle _logCard, _logTile, _logCaption, _logWell;

    public MainForm(CompanionServer server, AdbBridge adb, string[]? args = null)
    {
        _server = server;
        _adb = adb;
        if (args is not null) AutoLab(args);
        _addresses = CompanionServer.LocalAddresses();
        _qr = QrImage($"mobilepad://connect?c={string.Join("_", _addresses)}_{server.Port}");

        // Layout is computed in code from DeviceDpi, so no WinForms auto-scaling on top of it.
        AutoScaleMode = AutoScaleMode.None;
        SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint | ControlStyles.ResizeRedraw, true);
        Text = $"Mobilepad Companion {_version}";
        Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
        BackColor = Theme.Background;
        ForeColor = Theme.Text;

        _log.Multiline = true;
        _log.ReadOnly = true;
        _log.ScrollBars = ScrollBars.Vertical;
        _log.BackColor = Theme.Well;
        _log.ForeColor = Theme.Muted;
        _log.BorderStyle = BorderStyle.None;
        _log.TabStop = false;
        _log.HandleCreated += (_, _) => Native.DarkScrollBars(_log.Handle);

        _copy.Text = _copyText;
        _copy.Glyph = Glyph.Copy;
        _copy.Primary = true;
        _copy.Click += (_, _) => CopyAddresses();
        _copy.Enabled = _addresses.Count > 0;
        _copiedTimer.Tick += (_, _) =>
        {
            _copiedTimer.Stop();
            _copy.Text = _copyText;
            _copy.Glyph = Glyph.Copy;
            DoLayout();
        };

        _clear.Text = L.T("Pulisci", "Clear", "清除", "清除", "クリア");
        _clear.Glyph = Glyph.Clear;
        _clear.Click += (_, _) => _log.Clear();

        // Mobilepad Lab (labs/SPEC.md): measurements in a window of its own.
        _labButton.Text = "Lab";
        _labButton.Glyph = "\uE9D9";
        _labButton.Click += (_, _) => OpenLab();

        Controls.AddRange([_log, _copy, _clear, _labButton]);

        ApplyScale();
        ClientSize = new Size(S(940), S(640));

        server.SessionsChanged += () => UiThread(DoLayout);
        adb.StatusChanged += () => UiThread(DoLayout);
        Log.Written += line => UiThread(() => _log.AppendText(line + Environment.NewLine));
        Log.Write(L.T($"In ascolto sulla porta {server.Port} ({string.Join(", ", _addresses)})",
                      $"Listening on port {server.Port} ({string.Join(", ", _addresses)})", $"正在监听端口 {server.Port}（{string.Join(", ", _addresses)}）", $"正在監聽連接埠 {server.Port}（{string.Join(", ", _addresses)}）", $"ポート {server.Port} で待機中（{string.Join(", ", _addresses)}）"));
    }

    private int S(float value) => (int)Math.Round(value * _scale);

    private void ApplyScale()
    {
        _scale = DeviceDpi / 96f;
        foreach (var font in new[] { _titleFont, _subtitleFont, _cardTitleFont, _bodyFont, _strongFont, _captionFont, _monoFont, _addressFont, _pillFont, _iconFont, _smallIconFont })
            font?.Dispose();
        _titleFont = Fonts.Px(Fonts.Display, S(30), FontStyle.Bold);
        _subtitleFont = Fonts.Px(Fonts.Body, S(13));
        _cardTitleFont = Fonts.Px(Fonts.Display, S(17), FontStyle.Bold);
        _bodyFont = Fonts.Px(Fonts.Body, S(13.5f));
        _strongFont = Fonts.Px(Fonts.Body, S(13.5f), FontStyle.Bold);
        _captionFont = Fonts.Px(Fonts.Body, S(11.5f), FontStyle.Bold);
        _monoFont = Fonts.Px(Fonts.Mono, S(12.5f));
        _addressFont = Fonts.Px(Fonts.Mono, S(15), FontStyle.Bold);
        _pillFont = Fonts.Px(Fonts.Body, S(13), FontStyle.Bold);
        _iconFont = Fonts.Px(Fonts.Icons, S(20));
        _smallIconFont = Fonts.Px(Fonts.Icons, S(13));

        _log.Font = _monoFont;
        foreach (var button in new[] { _copy, _clear, _labButton })
        {
            button.Font = _pillFont;
            button.IconFont = _smallIconFont;
        }
        MinimumSize = SizeFromClientSize(new Size(S(820), S(580)));
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

    private void DoLayout()
    {
        // Controls.AddRange in the constructor lays out before the fonts exist.
        if (_pillFont is null || ClientSize.Width <= 0 || ClientSize.Height <= 0) return;
        var margin = S(24);
        var gap = S(16);
        var pad = S(20);
        var client = ClientSize;

        // Header: app tile, title and version, status pill on the right.
        _headerTile = new Rectangle(margin, S(20), S(48), S(48));
        var titleSize = Draw.Measure("Mobilepad", _titleFont);
        var subtitleSize = Draw.Measure("Companion", _subtitleFont);
        var headerTextTop = _headerTile.Top + (_headerTile.Height - titleSize.Height - subtitleSize.Height) / 2;
        _titleRect = new Rectangle(_headerTile.Right + S(14), headerTextTop, titleSize.Width + S(4), titleSize.Height);
        _subtitleRect = new Rectangle(_titleRect.X + S(1), _titleRect.Bottom, S(300), subtitleSize.Height);
        var pillHeight = S(38);
        var pillWidth = S(42) + Draw.Measure(StatusText(), _pillFont).Width + S(16);
        _pill = new Rectangle(client.Width - margin - pillWidth, _headerTile.Top + (_headerTile.Height - pillHeight) / 2, pillWidth, pillHeight);
        _labButton.Height = pillHeight;
        _labButton.Width = _labButton.PreferredWidth(pillHeight);
        _labButton.Location = new Point(_pill.X - S(10) - _labButton.Width, _pill.Y);

        var top = _headerTile.Bottom + S(22);
        var bottom = client.Height - margin;

        // Left card: QR code and Wi-Fi addresses.
        _connectCard = new Rectangle(margin, top, S(316), bottom - top);
        var inner = _connectCard.Width - 2 * pad;
        _connectTile = new Rectangle(_connectCard.X + pad, _connectCard.Y + pad, S(34), S(34));
        _connectTitleRect = new Rectangle(_connectTile.Right + S(12), _connectTile.Y, inner - _connectTile.Width - S(12), _connectTile.Height);
        var buttonHeight = S(30);
        var instructionsHeight = Draw.Measure(_instructions, _bodyFont, inner, TextFormatFlags.WordBreak).Height;
        var addressLine = Draw.Measure("0", _addressFont).Height + S(4);
        // The QR code gives way first when the window is short, so every address stays visible.
        var below = S(18) + buttonHeight + S(10) + instructionsHeight + S(8) + Math.Max(1, _addresses.Count) * addressLine + pad;
        var qrSize = Math.Max(S(150), Math.Min(Math.Min(inner, S(250)), _connectCard.Bottom - below - _connectTile.Bottom - S(16)));
        _qrRect = new Rectangle(_connectCard.X + (_connectCard.Width - qrSize) / 2, _connectTile.Bottom + S(16), qrSize, qrSize);
        var y = _qrRect.Bottom + S(18);
        _copy.Height = buttonHeight;
        _copy.Width = _copy.PreferredWidth(buttonHeight);
        _copy.Location = new Point(_connectCard.Right - pad - _copy.Width, y);
        var captionHeight = Draw.Measure("WI‑FI", _captionFont).Height;
        _wifiCaption = new Rectangle(_connectCard.X + pad, y + (buttonHeight - captionHeight) / 2, S(120), captionHeight);
        y += buttonHeight + S(10);
        _instructionsRect = new Rectangle(_connectCard.X + pad, y, inner, instructionsHeight);
        y = _instructionsRect.Bottom + S(8);
        _addressesRect = new Rectangle(_connectCard.X + pad, y, inner, Math.Max(0, _connectCard.Bottom - pad - y));

        // Right column: USB, connected phones, log.
        var x = _connectCard.Right + gap;
        var width = client.Width - margin - x;
        var tile = S(40);
        var textX = x + pad + tile + S(14);
        var textWidth = x + width - pad - textX;

        var usbTextHeight = Draw.Measure(UsbText(), _bodyFont, textWidth, TextFormatFlags.WordBreak).Height;
        captionHeight = Draw.Measure("USB", _captionFont).Height;
        var usbContent = Math.Max(tile, captionHeight + S(4) + usbTextHeight);
        _usbCard = new Rectangle(x, top, width, usbContent + 2 * pad);
        _usbTile = new Rectangle(x + pad, top + pad + (usbContent - tile) / 2, tile, tile);
        var usbTextTop = top + pad + (usbContent - captionHeight - S(4) - usbTextHeight) / 2;
        _usbCaption = new Rectangle(textX, usbTextTop, textWidth, captionHeight);
        _usbText = new Rectangle(textX, _usbCaption.Bottom + S(4), textWidth, usbTextHeight);

        var sessions = _server.Sessions.Count;
        var rowHeight = S(34);
        var rowGap = S(6);
        var devicesTop = _usbCard.Bottom + gap;
        var headerHeight = tile;
        var minLog = S(150);
        var maxRows = Math.Max(1, (bottom - minLog - gap - devicesTop - 2 * pad - headerHeight - S(14) + rowGap) / (rowHeight + rowGap));
        var rows = Math.Min(sessions, maxRows);
        var rowsHeight = rows == 0 ? 0 : S(14) + rows * rowHeight + (rows - 1) * rowGap;
        _devicesCard = new Rectangle(x, devicesTop, width, 2 * pad + headerHeight + rowsHeight);
        _devicesTile = new Rectangle(x + pad, devicesTop + pad, tile, tile);
        var devicesTitleHeight = Draw.Measure(_devicesTitle, _cardTitleFont).Height;
        var countHeight = Draw.Measure(CountText(), _bodyFont).Height;
        var devicesTextTop = _devicesTile.Y + (tile - devicesTitleHeight - countHeight - S(2)) / 2;
        _devicesTitleRect = new Rectangle(textX, devicesTextTop, textWidth, devicesTitleHeight);
        _countRect = new Rectangle(textX, _devicesTitleRect.Bottom + S(2), textWidth, countHeight);
        _rows.Clear();
        for (var i = 0; i < rows; i++)
            _rows.Add(new Rectangle(x + pad, _devicesTile.Bottom + S(14) + i * (rowHeight + rowGap), width - 2 * pad, rowHeight));

        _logCard = new Rectangle(x, _devicesCard.Bottom + gap, width, Math.Max(S(80), bottom - _devicesCard.Bottom - gap));
        _logTile = new Rectangle(x + pad, _logCard.Y + pad, S(30), S(30));
        _clear.Height = buttonHeight;
        _clear.Width = _clear.PreferredWidth(buttonHeight);
        _clear.Location = new Point(_logCard.Right - pad - _clear.Width, _logTile.Y + (_logTile.Height - buttonHeight) / 2);
        captionHeight = Draw.Measure(_logTitle, _captionFont).Height;
        _logCaption = new Rectangle(_logTile.Right + S(12), _logTile.Y + (_logTile.Height - captionHeight) / 2, S(200), captionHeight);
        _logWell = Rectangle.FromLTRB(x + S(12), _logTile.Bottom + S(12), _logCard.Right - S(12), _logCard.Bottom - S(12));
        _log.Bounds = Rectangle.FromLTRB(_logWell.X + S(14), _logWell.Y + S(10), _logWell.Right - S(4), _logWell.Bottom - S(10));

        Invalidate();
    }

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
        var radius = S(20);
        var single = TextFormatFlags.SingleLine | TextFormatFlags.EndEllipsis;

        // Header.
        Draw.IconTile(g, _headerTile, Theme.Violet, Glyph.Game, _iconFont);
        Draw.Text(g, "Mobilepad", _titleFont, _titleRect, Theme.Text, TextFormatFlags.SingleLine);
        Draw.Text(g, $"Companion · {_version}", _subtitleFont, _subtitleRect, Theme.Faint, TextFormatFlags.SingleLine);
        Draw.Glass(g, _pill, _pill.Height / 2f);
        var connected = _server.Sessions.Count > 0;
        Draw.Dot(g, new PointF(_pill.X + S(22), _pill.Y + _pill.Height / 2f), S(9), connected ? Theme.Success : Theme.Warning);
        Draw.Text(g, StatusText(), _pillFont, Rectangle.FromLTRB(_pill.X + S(40), _pill.Y, _pill.Right, _pill.Bottom), Theme.Text,
            TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine);

        // Connect card.
        Draw.Glass(g, _connectCard, radius);
        Draw.IconTile(g, _connectTile, Theme.Violet, Glyph.Phone, _smallIconFont);
        Draw.Text(g, _connectTitle, _cardTitleFont, _connectTitleRect, Theme.Text, single | TextFormatFlags.VerticalCenter);
        // White rounded square: phone cameras read the code better with a quiet zone around it.
        using (var path = Draw.Rounded(_qrRect, S(16)))
        using (var white = new SolidBrush(Color.White))
            g.FillPath(white, path);
        var quiet = S(12);
        var previous = g.InterpolationMode;
        g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.NearestNeighbor;
        g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.Half;
        // Whole pixels per module, so every module has the same size.
        var cell = Math.Max(1, (_qrRect.Width - 2 * quiet) / _qr.Width);
        var side = cell * _qr.Width;
        g.DrawImage(_qr, new Rectangle(_qrRect.X + (_qrRect.Width - side) / 2, _qrRect.Y + (_qrRect.Height - side) / 2, side, side));
        g.InterpolationMode = previous;
        g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.HighQuality;
        Draw.Text(g, "WI‑FI", _captionFont, _wifiCaption, Theme.AccentLight, TextFormatFlags.SingleLine);
        Draw.Text(g, _instructions, _bodyFont, _instructionsRect, Theme.Muted, TextFormatFlags.WordBreak);
        var line = Draw.Measure("0", _addressFont).Height + S(4);
        var ay = _addressesRect.Y;
        foreach (var address in _addresses)
        {
            if (ay + line > _addressesRect.Bottom + S(4)) break;
            Draw.Text(g, $"{address}:{_server.Port}", _addressFont, new Rectangle(_addressesRect.X, ay, _addressesRect.Width, line), Theme.Text, single);
            ay += line;
        }
        if (_addresses.Count == 0)
            Draw.Text(g, "—", _addressFont, new Rectangle(_addressesRect.X, ay, _addressesRect.Width, line), Theme.Faint, single);

        // USB card.
        Draw.Glass(g, _usbCard, radius);
        Draw.IconTile(g, _usbTile, Theme.Teal, Glyph.Usb, _iconFont);
        Draw.Text(g, "USB", _captionFont, _usbCaption, Theme.AccentLight, TextFormatFlags.SingleLine);
        Draw.Text(g, UsbText(), _bodyFont, _usbText, UsbColor(), TextFormatFlags.WordBreak);

        // Connected phones.
        Draw.Glass(g, _devicesCard, radius);
        Draw.IconTile(g, _devicesTile, Theme.Blue, Glyph.Devices, _iconFont);
        Draw.Text(g, _devicesTitle, _cardTitleFont, _devicesTitleRect, Theme.Text, single);
        Draw.Text(g, CountText(), _bodyFont, _countRect, connected ? Theme.Success : Theme.Muted, single);
        var sessions = _server.Sessions.ToList();
        for (var i = 0; i < _rows.Count && i < sessions.Count; i++)
        {
            var row = _rows[i];
            Draw.Glass(g, row, row.Height / 2f, 14);
            Draw.Dot(g, new PointF(row.X + S(18), row.Y + row.Height / 2f), S(8), Theme.Success);
            var text = sessions[i].Describe();
            // Rows that do not fit: the last one says how many more there are.
            if (i == _rows.Count - 1 && sessions.Count > _rows.Count) text = $"+{sessions.Count - _rows.Count + 1} …";
            Draw.Text(g, text, _bodyFont, Rectangle.FromLTRB(row.X + S(34), row.Y, row.Right - S(14), row.Bottom), Theme.Text,
                single | TextFormatFlags.VerticalCenter);
        }

        // Log.
        Draw.Glass(g, _logCard, radius);
        Draw.IconTile(g, _logTile, Theme.Orange, Glyph.Document, _smallIconFont);
        Draw.Text(g, _logTitle, _captionFont, _logCaption, Theme.AccentLight, TextFormatFlags.SingleLine);
        Draw.Well(g, _logWell, S(14));
    }

    private string StatusText()
    {
        var count = _server.Sessions.Count;
        return count switch
        {
            0 => L.T("In attesa del telefono", "Waiting for a phone", "等待手机连接", "等待手機連接", "スマホを待機中"),
            1 => L.T("1 telefono collegato", "1 phone connected", "已连接 1 部手机", "已連接 1 支手機", "スマホ 1 台接続中"),
            _ => L.T($"{count} telefoni collegati", $"{count} phones connected", $"已连接 {count} 部手机", $"已連接 {count} 支手機", $"スマホ {count} 台接続中"),
        };
    }

    private string CountText()
    {
        var count = _server.Sessions.Count;
        return count switch
        {
            0 => L.T("Nessun dispositivo: collega il telefono dall'app", "No devices: connect your phone from the app", "没有设备：请在应用中连接手机", "沒有裝置：請在 App 中連接手機", "機器なし：アプリからスマホを接続してください"),
            1 => L.T("1 telefono", "1 phone", "1 部手机", "1 支手機", "スマホ 1 台"),
            _ => L.T($"{count} telefoni", $"{count} phones", $"{count} 部手机", $"{count} 支手機", $"スマホ {count} 台"),
        };
    }

    /// <summary>ADB status without the "USB:" prefix, which the card caption already shows.</summary>
    private string UsbText()
    {
        var status = _adb.Status;
        return status.StartsWith("USB:") || status.StartsWith("USB：") ? status[4..].TrimStart() : status;
    }

    // AdbBridge only exposes text; its "ready" and "not found" messages are the ones worth colouring.
    private Color UsbColor() =>
        _adb.Status.Contains('→') ? Theme.Success :
        _adb.AdbPath is null ? Theme.Warning : Theme.Muted;

    private void CopyAddresses()
    {
        try
        {
            Clipboard.SetText(string.Join(Environment.NewLine, _addresses.Select(a => $"{a}:{_server.Port}")));
        }
        catch (System.Runtime.InteropServices.ExternalException)
        {
            return; // clipboard busy in another app
        }
        _copy.Text = _copiedText;
        _copy.Glyph = Glyph.Check;
        DoLayout();
        _copiedTimer.Stop();
        _copiedTimer.Start();
    }

    // --lab <test> <link> [count]: opens the Lab and runs one test (used for scripted batteries).
    private void AutoLab(string[] args)
    {
        var i = Array.IndexOf(args, "--lab");
        if (i < 0 || i + 2 >= args.Length) return;
        var request = new Mobilepad.Companion.Lab.LabRequest { Test = args[i + 1], Link = args[i + 2] };
        if (i + 3 < args.Length && int.TryParse(args[i + 3], out var n)) request.Count = n;
        Shown += async (_, _) =>
        {
            await Task.Delay(4000); // let the phone reconnect to this companion
            OpenLab();
            _labForm!.AutoRun(request);
        };
    }

    private void OpenLab()
    {
        if (_labForm is null || _labForm.IsDisposed) _labForm = new LabForm(_server, _adb);
        _labForm.Show();
        if (_labForm.WindowState == FormWindowState.Minimized) _labForm.WindowState = FormWindowState.Normal;
        _labForm.Activate();
    }

    private void UiThread(Action action)
    {
        if (IsDisposed) return;
        if (InvokeRequired) BeginInvoke(action);
        else action();
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _copiedTimer.Dispose();
            _backdrop?.Dispose();
            _qr.Dispose();
        }
        base.Dispose(disposing);
    }

    private static Image QrImage(string text)
    {
        using var generator = new QRCodeGenerator();
        using var data = generator.CreateQrCode(text, QRCodeGenerator.ECCLevel.M);
        // One pixel per module and no built-in quiet zone: the form scales it up crisply and adds its own margin.
        var png = new PngByteQRCode(data).GetGraphic(1, drawQuietZones: false);
        return Image.FromStream(new MemoryStream(png));
    }
}
