using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Runtime.InteropServices;

namespace Mobilepad.Companion;

/// <summary>Palette of the redesigned Android app (deep indigo, glass panels, violet accent).</summary>
internal static class Theme
{
    public static readonly Color Background = Color.FromArgb(0x0C, 0x0A, 0x18);
    public static readonly Color BackgroundLow = Color.FromArgb(0x09, 0x07, 0x13);
    public static readonly Color GlowViolet = Color.FromArgb(0x7B, 0x3F, 0xF2);
    public static readonly Color GlowTeal = Color.FromArgb(0x00, 0xA3, 0xB8);

    public static readonly Color Text = Color.FromArgb(0xF4, 0xF2, 0xFA);
    public static readonly Color Muted = Color.FromArgb(0xB4, 0xAF, 0xC6);
    public static readonly Color Faint = Color.FromArgb(0x7D, 0x78, 0x92);

    public static readonly Color Accent = Color.FromArgb(0x8B, 0x5C, 0xF6);
    public static readonly Color AccentLight = Color.FromArgb(0xB9, 0x9C, 0xFF);
    public static readonly Color PrimaryTop = Color.FromArgb(0x9B, 0x6B, 0xFF);
    public static readonly Color PrimaryBottom = Color.FromArgb(0x6A, 0x2B, 0xE8);
    public static readonly Color Success = Color.FromArgb(0x34, 0xD3, 0x99);
    public static readonly Color Warning = Color.FromArgb(0xF5, 0xB9, 0x4A);
    public static readonly Color Danger = Color.FromArgb(0xF2, 0x6D, 0x6D);

    /// <summary>Opaque well behind the log, so the native text box blends in exactly.</summary>
    public static readonly Color Well = Color.FromArgb(0x0A, 0x08, 0x15);

    // Icon tiles: vertical gradients, white glyph on top.
    public static readonly (Color Top, Color Bottom) Violet = (Color.FromArgb(0xC8, 0xB1, 0xFF), Color.FromArgb(0x5B, 0x1B, 0xE0));
    public static readonly (Color Top, Color Bottom) Teal = (Color.FromArgb(0x8F, 0xFF, 0xC8), Color.FromArgb(0x00, 0x80, 0x9A));
    public static readonly (Color Top, Color Bottom) Blue = (Color.FromArgb(0x9C, 0xC8, 0xFF), Color.FromArgb(0x2A, 0x55, 0xE5));
    public static readonly (Color Top, Color Bottom) Orange = (Color.FromArgb(0xFF, 0xC0, 0x8A), Color.FromArgb(0xE2, 0x57, 0x1C));
}

/// <summary>Segoe Fluent Icons / Segoe MDL2 Assets code points (same in both fonts).</summary>
internal static class Glyph
{
    public const string Game = "";
    public const string Phone = "";
    public const string Usb = "";
    public const string Devices = "";
    public const string Document = "";
    public const string Copy = "";
    public const string Check = "";
    public const string Clear = "";
}

internal static class Fonts
{
    private static readonly HashSet<string> Installed =
        new(new InstalledFontCollection().Families.Select(f => f.Name), StringComparer.OrdinalIgnoreCase);

    public static readonly string Display = First("Segoe UI Variable Display", "Segoe UI");
    public static readonly string Body = First("Segoe UI Variable Text", "Segoe UI");
    public static readonly string Mono = First("Cascadia Mono", "Consolas");
    public static readonly string Icons = First("Segoe Fluent Icons", "Segoe MDL2 Assets");

    private static string First(params string[] names) => names.FirstOrDefault(Installed.Contains) ?? names[^1];

    /// <summary>Font sized in device pixels, so it follows the scale we compute per monitor.</summary>
    public static Font Px(string family, float px, FontStyle style = FontStyle.Regular) =>
        new(family, px, style, GraphicsUnit.Pixel);
}

internal static class Draw
{
    public static GraphicsPath Rounded(RectangleF r, float radius)
    {
        var path = new GraphicsPath();
        var d = Math.Min(radius * 2, Math.Min(r.Width, r.Height));
        if (d <= 1)
        {
            path.AddRectangle(r);
            return path;
        }
        path.AddArc(r.X, r.Y, d, d, 180, 90);
        path.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        path.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        path.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        path.CloseFigure();
        return path;
    }

    /// <summary>Indigo backdrop with a violet glow at the top left and a teal one at the bottom right.</summary>
    public static void Backdrop(Graphics g, Size size)
    {
        var all = new Rectangle(Point.Empty, size);
        using (var baseBrush = new LinearGradientBrush(all, Theme.Background, Theme.BackgroundLow, 90f))
            g.FillRectangle(baseBrush, all);
        Glow(g, new PointF(size.Width * 0.08f, size.Height * 0.02f), Math.Max(size.Width, size.Height) * 0.75f, Theme.GlowViolet, 84);
        Glow(g, new PointF(size.Width * 0.98f, size.Height * 1.02f), Math.Max(size.Width, size.Height) * 0.6f, Theme.GlowTeal, 51);
    }

    private static void Glow(Graphics g, PointF center, float radius, Color color, int alpha)
    {
        using var path = new GraphicsPath();
        path.AddEllipse(center.X - radius, center.Y - radius, radius * 2, radius * 2);
        using var brush = new PathGradientBrush(path)
        {
            CenterPoint = center,
            CenterColor = Color.FromArgb(alpha, color),
            // Same RGB at alpha 0, otherwise GDI+ fades through grey.
            SurroundColors = [Color.FromArgb(0, color)],
        };
        // Soft falloff instead of a linear cone.
        brush.Blend = new Blend
        {
            Positions = [0f, 0.25f, 0.5f, 0.75f, 1f],
            Factors = [0f, 0.12f, 0.38f, 0.75f, 1f],
        };
        g.FillPath(brush, path);
    }

    /// <summary>Glass panel: translucent white fill, 1 px border that is lighter on top.</summary>
    public static void Glass(Graphics g, Rectangle r, float radius, int fillAlpha = 23)
    {
        using var path = Rounded(new RectangleF(r.X + 0.5f, r.Y + 0.5f, r.Width - 1, r.Height - 1), radius);
        using (var fill = new SolidBrush(Color.FromArgb(fillAlpha, Color.White)))
            g.FillPath(fill, path);
        using var border = new LinearGradientBrush(new Rectangle(r.X, r.Y - 1, r.Width, r.Height + 2),
            Color.FromArgb(56, Color.White), Color.FromArgb(22, Color.White), 90f);
        using var pen = new Pen(border, 1f);
        g.DrawPath(pen, path);
    }

    public static void Well(Graphics g, Rectangle r, float radius)
    {
        using var path = Rounded(new RectangleF(r.X + 0.5f, r.Y + 0.5f, r.Width - 1, r.Height - 1), radius);
        using (var fill = new SolidBrush(Theme.Well))
            g.FillPath(fill, path);
        using var pen = new Pen(Color.FromArgb(28, Color.White), 1f);
        g.DrawPath(pen, path);
    }

    /// <summary>Rounded square with a vertical gradient and a white glyph.</summary>
    public static void IconTile(Graphics g, Rectangle r, (Color Top, Color Bottom) hue, string glyph, Font iconFont)
    {
        var radius = r.Width * 0.3f;
        using (var path = Rounded(r, radius))
        using (var brush = new LinearGradientBrush(new Rectangle(r.X, r.Y - 1, r.Width, r.Height + 2), hue.Top, hue.Bottom, 90f))
        {
            g.FillPath(brush, path);
            using var shine = new Pen(Color.FromArgb(70, Color.White), 1f);
            using var inner = Rounded(new RectangleF(r.X + 0.5f, r.Y + 0.5f, r.Width - 1, r.Height - 1), radius);
            g.DrawPath(shine, inner);
        }
        TextRenderer.DrawText(g, glyph, iconFont, r, Color.White,
            TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter | TextFormatFlags.NoPadding | TextFormatFlags.SingleLine);
    }

    public static void Dot(Graphics g, PointF center, float diameter, Color color)
    {
        using (var halo = new SolidBrush(Color.FromArgb(60, color)))
            g.FillEllipse(halo, center.X - diameter, center.Y - diameter, diameter * 2, diameter * 2);
        using var brush = new SolidBrush(color);
        g.FillEllipse(brush, center.X - diameter / 2, center.Y - diameter / 2, diameter, diameter);
    }

    public static void Text(Graphics g, string text, Font font, Rectangle r, Color color, TextFormatFlags extra = 0) =>
        TextRenderer.DrawText(g, text, font, r, color, TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix | extra);

    public static Size Measure(string text, Font font, int width = int.MaxValue, TextFormatFlags extra = 0) =>
        TextRenderer.MeasureText(text, font, new Size(width, int.MaxValue), TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix | extra);

    public static void HighQuality(Graphics g)
    {
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
    }
}

/// <summary>Owner-drawn pill button: glass (default) or violet gradient (primary), with hover and pressed states.</summary>
internal sealed class PillButton : Button
{
    private bool _hover;
    private bool _pressed;

    public string Glyph { get; set; } = "";
    public bool Primary { get; set; }
    public Font? IconFont { get; set; }

    public PillButton()
    {
        SetStyle(ControlStyles.UserPaint | ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer |
                 ControlStyles.ResizeRedraw | ControlStyles.SupportsTransparentBackColor, true);
        // ButtonBase is opaque by default, which skips painting the parent behind the rounded corners.
        SetStyle(ControlStyles.Opaque, false);
        BackColor = Color.Transparent;
        ForeColor = Theme.Text;
        FlatStyle = FlatStyle.Flat;
        FlatAppearance.BorderSize = 0;
        Cursor = Cursors.Hand;
        UseVisualStyleBackColor = false;
    }

    /// <summary>Width that fits glyph and text at the given height.</summary>
    public int PreferredWidth(int height)
    {
        var text = Draw.Measure(Text, Font).Width;
        var icon = Glyph.Length > 0 && IconFont is not null ? Draw.Measure(Glyph, IconFont).Width + height / 4 : 0;
        return text + icon + height;
    }

    protected override void OnMouseEnter(EventArgs e) { _hover = true; Invalidate(); base.OnMouseEnter(e); }
    protected override void OnMouseLeave(EventArgs e) { _hover = false; _pressed = false; Invalidate(); base.OnMouseLeave(e); }
    protected override void OnMouseDown(MouseEventArgs e) { if (e.Button == MouseButtons.Left) { _pressed = true; Invalidate(); } base.OnMouseDown(e); }
    protected override void OnMouseUp(MouseEventArgs e) { _pressed = false; Invalidate(); base.OnMouseUp(e); }
    protected override void OnGotFocus(EventArgs e) { Invalidate(); base.OnGotFocus(e); }
    protected override void OnLostFocus(EventArgs e) { Invalidate(); base.OnLostFocus(e); }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics;
        Draw.HighQuality(g);
        var r = new RectangleF(0.5f, 0.5f, Width - 1, Height - 1);
        if (_pressed) r.Inflate(-1, -1);
        using var path = Draw.Rounded(r, r.Height / 2);

        if (Primary)
        {
            var top = _hover ? ControlPaint.Light(Theme.PrimaryTop, 0.2f) : Theme.PrimaryTop;
            var bottom = _pressed ? ControlPaint.Dark(Theme.PrimaryBottom, 0.1f) : Theme.PrimaryBottom;
            using var fill = new LinearGradientBrush(new Rectangle(0, -1, Width, Height + 2), top, bottom, 90f);
            g.FillPath(fill, path);
            using var pen = new Pen(Color.FromArgb(60, Color.White));
            g.DrawPath(pen, path);
        }
        else
        {
            var alpha = _pressed ? 14 : _hover ? 38 : 23;
            using var fill = new SolidBrush(Color.FromArgb(alpha, Color.White));
            g.FillPath(fill, path);
            using var pen = new Pen(Color.FromArgb(_hover ? 70 : 44, Color.White));
            g.DrawPath(pen, path);
        }

        if (Focused && ShowFocusCues)
        {
            using var ring = Draw.Rounded(new RectangleF(2, 2, Width - 5, Height - 5), (Height - 5) / 2f);
            using var pen = new Pen(Theme.AccentLight, 1.5f);
            g.DrawPath(pen, ring);
        }

        // Glyph + text, centred as one group.
        var color = Enabled ? ForeColor : Theme.Faint;
        var textSize = Draw.Measure(Text, Font);
        var hasIcon = Glyph.Length > 0 && IconFont is not null;
        var iconSize = hasIcon ? Draw.Measure(Glyph, IconFont!) : Size.Empty;
        var gap = hasIcon ? Height / 4 : 0;
        var x = (Width - (textSize.Width + iconSize.Width + gap)) / 2;
        if (hasIcon)
        {
            Draw.Text(g, Glyph, IconFont!, new Rectangle(x, 0, iconSize.Width, Height), Primary ? Color.White : Theme.AccentLight,
                TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine);
            x += iconSize.Width + gap;
        }
        Draw.Text(g, Text, Font, new Rectangle(x, 0, textSize.Width + 2, Height), color,
            TextFormatFlags.VerticalCenter | TextFormatFlags.SingleLine);
    }
}

internal static class Native
{
    private const int DwmwaUseImmersiveDarkMode = 20;
    private const int DwmwaCaptionColor = 35;

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

    [DllImport("uxtheme.dll", CharSet = CharSet.Unicode)]
    private static extern int SetWindowTheme(IntPtr hwnd, string? appName, string? idList);

    /// <summary>Dark title bar on Windows 10 2004+ and 11; on 11 also tints it with our background. Ignored elsewhere.</summary>
    public static void DarkTitleBar(IntPtr hwnd, Color caption)
    {
        var on = 1;
        _ = DwmSetWindowAttribute(hwnd, DwmwaUseImmersiveDarkMode, ref on, sizeof(int));
        var colorRef = caption.R | caption.G << 8 | caption.B << 16;
        _ = DwmSetWindowAttribute(hwnd, DwmwaCaptionColor, ref colorRef, sizeof(int));
    }

    /// <summary>Dark scroll bars for a native control (Windows 10 1809+).</summary>
    public static void DarkScrollBars(IntPtr hwnd) => _ = SetWindowTheme(hwnd, "DarkMode_Explorer", null);
}
