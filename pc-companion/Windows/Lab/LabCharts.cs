using System.Drawing.Drawing2D;
using System.Globalization;
using Mobilepad.Companion.Lab;

namespace Mobilepad.Companion;

/// <summary>Hand-drawn charts for the Lab screen (GDI+), in the companion's palette.</summary>
internal static class LabCharts
{
    public static readonly Color Measured = Theme.AccentLight;
    public static readonly Color Estimated = Color.FromArgb(0x5E, 0xE0, 0xD0);
    private static readonly Color Grid = Color.FromArgb(26, Color.White);

    public static Color KindColor(string kind) => kind == "estimated" ? Estimated : Measured;

    public static string Ms(double v) =>
        double.IsNaN(v) ? "—" :
        Math.Abs(v) < 10 ? v.ToString("0.00", CultureInfo.CurrentCulture) :
        Math.Abs(v) < 100 ? v.ToString("0.0", CultureInfo.CurrentCulture) :
        v.ToString("0", CultureInfo.CurrentCulture);

    public static void Text(Graphics g, string text, Font font, Rectangle r, Color color, TextFormatFlags flags = 0) =>
        TextRenderer.DrawText(g, text, font, r, color,
            TextFormatFlags.NoPadding | TextFormatFlags.NoPrefix | TextFormatFlags.PreserveGraphicsClipping | TextFormatFlags.SingleLine | flags);

    /// <summary>Well with a caption; returns the plot area.</summary>
    public static Rectangle Frame(Graphics g, Rectangle r, string title, Font caption, Font small, float scale)
    {
        Draw.Well(g, r, 12 * scale);
        int pad = (int)(12 * scale);
        var h = Draw.Measure(title, caption).Height;
        Text(g, title, caption, new Rectangle(r.X + pad, r.Y + pad, r.Width - 2 * pad, h), Theme.AccentLight, TextFormatFlags.EndEllipsis);
        int labelH = Draw.Measure("0", small).Height;
        int left = (int)(44 * scale);
        return Rectangle.FromLTRB(r.X + left, r.Y + pad + h + (int)(10 * scale), r.Right - pad, r.Bottom - pad - labelH - (int)(4 * scale));
    }

    private static void YLabels(Graphics g, Rectangle plot, Font small, float scale, string top, string bottom)
    {
        int h = Draw.Measure("0", small).Height;
        int w = (int)(40 * scale);
        Text(g, top, small, new Rectangle(plot.X - w - (int)(4 * scale), plot.Y - h / 2, w, h), Theme.Faint, TextFormatFlags.Right);
        Text(g, bottom, small, new Rectangle(plot.X - w - (int)(4 * scale), plot.Bottom - h / 2, w, h), Theme.Faint, TextFormatFlags.Right);
    }

    private static void XLabels(Graphics g, Rectangle plot, Font small, float scale, string left, string right, string? middle = null)
    {
        int h = Draw.Measure("0", small).Height;
        int y = plot.Bottom + (int)(4 * scale);
        Text(g, left, small, new Rectangle(plot.X, y, plot.Width / 2, h), Theme.Faint);
        Text(g, right, small, new Rectangle(plot.X + plot.Width / 2, y, plot.Width / 2, h), Theme.Faint, TextFormatFlags.Right);
        if (middle is not null)
            Text(g, middle, small, new Rectangle(plot.X + plot.Width / 4, y, plot.Width / 2, h), Theme.Faint, TextFormatFlags.HorizontalCenter);
    }

    private static void GridLines(Graphics g, Rectangle plot, int rows)
    {
        using var pen = new Pen(Grid, 1f);
        for (int i = 0; i <= rows; i++)
        {
            float y = plot.Y + plot.Height * i / (float)rows;
            g.DrawLine(pen, plot.X, y, plot.Right, y);
        }
    }

    private static void VMarker(Graphics g, Rectangle plot, float x, Color color, string label, Font small)
    {
        if (x < plot.X || x > plot.Right) return;
        using var pen = new Pen(Color.FromArgb(170, color), 1f) { DashStyle = DashStyle.Dash };
        g.DrawLine(pen, x, plot.Y, x, plot.Bottom);
        var size = Draw.Measure(label, small);
        Text(g, label, small, new Rectangle((int)x + 3, plot.Y, size.Width + 2, size.Height), color);
    }

    public static void Histogram(Graphics g, Rectangle r, string title, StageStats? stage, Font caption, Font small, float scale)
    {
        var plot = Frame(g, r, title, caption, small, scale);
        if (stage is null || stage.Histogram.Counts.Length == 0 || plot.Width < 10 || plot.Height < 10)
        {
            Empty(g, plot, small);
            return;
        }
        var h = stage.Histogram;
        int max = Math.Max(1, h.Counts.Max());
        GridLines(g, plot, 4);
        double x0 = h.EdgesMs[0], x1 = h.EdgesMs[^1];
        double span = Math.Max(1e-9, x1 - x0);
        float barW = plot.Width / (float)h.Counts.Length;
        var color = KindColor(stage.Kind);
        using (var brush = new LinearGradientBrush(new Rectangle(plot.X, plot.Y - 1, plot.Width, plot.Height + 2),
                   Color.FromArgb(230, color), Color.FromArgb(110, color), 90f))
        {
            for (int i = 0; i < h.Counts.Length; i++)
            {
                if (h.Counts[i] == 0) continue;
                float bh = plot.Height * h.Counts[i] / (float)max;
                using var path = Draw.Rounded(new RectangleF(plot.X + i * barW + 1, plot.Bottom - bh, Math.Max(1, barW - 2), bh), Math.Min(3 * scale, barW / 3));
                g.FillPath(brush, path);
            }
        }
        float X(double v) => plot.X + (float)((v - x0) / span * plot.Width);
        VMarker(g, plot, X(stage.P50Ms), Theme.Text, "P50", small);
        VMarker(g, plot, X(stage.P99Ms), Theme.Warning, "P99", small);
        YLabels(g, plot, small, scale, max.ToString(CultureInfo.CurrentCulture), "0");
        XLabels(g, plot, small, scale, $"{Ms(x0)} ms", $"{Ms(x1)} ms" + (h.Overflow > 0 ? $"  (+{h.Overflow} ›)" : ""));
    }

    public static void Cdf(Graphics g, Rectangle r, string title, StageStats? stage, Font caption, Font small, float scale)
    {
        var plot = Frame(g, r, title, caption, small, scale);
        if (stage is null || stage.Cdf.Length < 2 || plot.Width < 10 || plot.Height < 10)
        {
            Empty(g, plot, small);
            return;
        }
        GridLines(g, plot, 4);
        // Range to P99.5 like the histogram, so the tail does not squash the curve.
        double x0 = stage.Cdf[0][0];
        double x1 = stage.Histogram.EdgesMs.Length > 0 ? Math.Max(stage.Histogram.EdgesMs[^1], x0 + 1e-3) : stage.Cdf[^1][0];
        double span = Math.Max(1e-9, x1 - x0);
        var points = stage.Cdf
            .Select(p => new PointF(plot.X + (float)(Math.Min(p[0], x1) - x0) / (float)span * plot.Width, plot.Bottom - (float)p[1] * plot.Height))
            .ToArray();
        var color = KindColor(stage.Kind);
        using (var fill = new GraphicsPath())
        {
            fill.AddLines(points);
            fill.AddLine(points[^1], new PointF(points[^1].X, plot.Bottom));
            fill.AddLine(new PointF(points[^1].X, plot.Bottom), new PointF(points[0].X, plot.Bottom));
            fill.CloseFigure();
            using var brush = new LinearGradientBrush(new Rectangle(plot.X, plot.Y - 1, plot.Width, plot.Height + 2),
                Color.FromArgb(70, color), Color.FromArgb(5, color), 90f);
            g.FillPath(brush, fill);
        }
        using (var pen = new Pen(color, 2f * scale) { LineJoin = LineJoin.Round })
            g.DrawLines(pen, points);
        float X(double v) => plot.X + (float)((v - x0) / span * plot.Width);
        VMarker(g, plot, X(stage.P50Ms), Theme.Text, "P50", small);
        VMarker(g, plot, X(stage.P99Ms), Theme.Warning, "P99", small);
        YLabels(g, plot, small, scale, "100%", "0%");
        XLabels(g, plot, small, scale, $"{Ms(x0)} ms", $"{Ms(x1)} ms");
    }

    public static void Series(Graphics g, Rectangle r, string title, SeriesData series, string kind, Font caption, Font small, float scale)
    {
        var plot = Frame(g, r, title, caption, small, scale);
        if (series.Ms.Length == 0 || plot.Width < 10 || plot.Height < 10)
        {
            Empty(g, plot, small);
            return;
        }
        GridLines(g, plot, 4);
        var sorted = series.Ms.OrderBy(v => v).ToArray();
        double y0 = Math.Min(0, sorted[0]);
        double y1 = Math.Max(y0 + 1e-3, LabAnalysis.Percentile(sorted, 0.995) * 1.1);
        double t1 = Math.Max(1e-3, series.TS[^1]);
        var color = KindColor(kind);
        float dot = Math.Max(2f, 3f * scale);
        using var brush = new SolidBrush(Color.FromArgb(series.Ms.Length > 1500 ? 120 : 190, color));
        using var over = new SolidBrush(Theme.Warning);
        for (int i = 0; i < series.Ms.Length; i++)
        {
            float x = plot.X + (float)(series.TS[i] / t1) * plot.Width;
            double v = series.Ms[i];
            if (v > y1)
            {
                g.FillRectangle(over, x - dot / 2, plot.Y - dot / 2, dot, dot);
                continue;
            }
            float y = plot.Bottom - (float)((v - y0) / (y1 - y0)) * plot.Height;
            g.FillEllipse(brush, x - dot / 2, y - dot / 2, dot, dot);
        }
        YLabels(g, plot, small, scale, $"{Ms(y1)} ms", $"{Ms(y0)}");
        XLabels(g, plot, small, scale, "0 s", $"{t1.ToString("0.#", CultureInfo.CurrentCulture)} s");
    }

    /// <summary>One bar per stage: P50 bar, P95 whisker, P99 tick; violet = measured, teal = estimated.</summary>
    public static void StageBars(Graphics g, Rectangle r, string title, IReadOnlyList<StageStats> stages, Font caption, Font small, float scale)
    {
        var plot = Frame(g, r, title, caption, small, scale);
        if (stages.Count == 0 || plot.Width < 10 || plot.Height < 10)
        {
            Empty(g, plot, small);
            return;
        }
        // Wide enough for the longest stage name (e.g. "submit_start→submit").
        int labelW = Math.Max((int)(84 * scale), stages.Max(s => Draw.Measure(s.Name, small).Width) + (int)(10 * scale));
        int valueW = (int)(64 * scale);
        var area = Rectangle.FromLTRB(plot.X - (int)(32 * scale) + labelW, plot.Y, plot.Right - valueW, plot.Bottom + Draw.Measure("0", small).Height);
        double max = Math.Max(1e-3, stages.Max(s => s.P99Ms));
        float rowH = area.Height / (float)stages.Count;
        float barH = Math.Min(rowH * 0.5f, 12 * scale);
        int th = Draw.Measure("0", small).Height;
        for (int i = 0; i < stages.Count; i++)
        {
            var s = stages[i];
            float cy = area.Y + rowH * (i + 0.5f);
            var color = KindColor(s.Kind);
            Text(g, s.Name, small, new Rectangle(area.X - labelW, (int)(cy - th / 2f), labelW - (int)(6 * scale), th), Theme.Muted, TextFormatFlags.Right);
            float W(double v) => (float)(Math.Max(0, v) / max * area.Width);
            using (var track = new SolidBrush(Color.FromArgb(14, Color.White)))
            using (var trackPath = Draw.Rounded(new RectangleF(area.X, cy - barH / 2, area.Width, barH), barH / 2))
                g.FillPath(track, trackPath);
            if (W(s.P50Ms) >= 1)
            {
                using var brush = new LinearGradientBrush(new RectangleF(area.X - 1, cy - barH / 2, W(s.P50Ms) + 2, barH), Color.FromArgb(140, color), color, 0f);
                using var path = Draw.Rounded(new RectangleF(area.X, cy - barH / 2, W(s.P50Ms), barH), barH / 2);
                g.FillPath(brush, path);
            }
            using (var whisker = new Pen(Color.FromArgb(200, color), 1.5f * scale))
            {
                g.DrawLine(whisker, area.X + W(s.P50Ms), cy, area.X + W(s.P95Ms), cy);
                g.DrawLine(whisker, area.X + W(s.P95Ms), cy - barH / 2, area.X + W(s.P95Ms), cy + barH / 2);
            }
            using (var p99 = new Pen(Theme.Warning, 1.5f * scale))
                g.DrawLine(p99, area.X + W(s.P99Ms), cy - barH / 2 - 2, area.X + W(s.P99Ms), cy + barH / 2 + 2);
            Text(g, $"{Ms(s.P50Ms)} ms", small, new Rectangle(area.Right + (int)(6 * scale), (int)(cy - th / 2f), valueW, th), Theme.Text);
        }
    }

    private static void Empty(Graphics g, Rectangle plot, Font small) =>
        Text(g, "—", small, plot, Theme.Faint, TextFormatFlags.HorizontalCenter | TextFormatFlags.VerticalCenter);
}
