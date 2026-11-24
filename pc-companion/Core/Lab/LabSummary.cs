using System.Text.Json;
using System.Text.Json.Serialization;

namespace Mobilepad.Companion.Lab;

/// <summary>summary.json (SPEC §3): everything the Lab screen draws and the history compares.</summary>
public sealed class LabSummary
{
    public int Format { get; set; } = 1;
    public string RunId { get; set; } = "";
    public string Test { get; set; } = "";
    public string Link { get; set; } = "";
    public string Mark { get; set; } = "rx";
    public SyncInfo Sync { get; set; } = new();
    public List<StageStats> Stages { get; set; } = [];
    public LossStats Loss { get; set; } = new();
    public RateStats Rates { get; set; } = new();
    public List<AxisStats> Axes { get; set; } = [];
    public IntegrityStats Integrity { get; set; } = new();
    public ReconnectStats? Reconnect { get; set; }
    public SeriesData Series { get; set; } = new();
    public Headline Headline { get; set; } = new();
    public List<string> Notes { get; set; } = [];

    public StageStats? Stage(string name) => Stages.FirstOrDefault(s => s.Name == name);
}

public sealed class SyncInfo
{
    /// <summary>False when no clock sync was available: cross-device stages are then not computed.</summary>
    public bool Available { get; set; }
    public double OffsetMs { get; set; }
    public double RttMinMs { get; set; }
    public double DriftMs { get; set; }
    public double DriftPpm { get; set; }
    /// <summary>SPEC §1/§6: min RTT/2 + |drift|; carried by every "estimated" stage.</summary>
    public double UncertaintyMs { get; set; }
}

public sealed class StageStats
{
    /// <summary>"cmd→obs" style name.</summary>
    public string Name { get; set; } = "";
    public string From { get; set; } = "";
    public string To { get; set; } = "";
    /// <summary>"measured" (same clock) or "estimated" (crosses phone→PC, depends on the sync) — SPEC §6.</summary>
    public string Kind { get; set; } = "measured";
    public double UncertaintyMs { get; set; }
    public int N { get; set; }
    public double MeanMs { get; set; }
    public double StdevMs { get; set; }
    public double MinMs { get; set; }
    public double MaxMs { get; set; }
    public double P50Ms { get; set; }
    public double P90Ms { get; set; }
    public double P95Ms { get; set; }
    public double P99Ms { get; set; }
    /// <summary>Bootstrap 95 % confidence interval of the median: [low, high].</summary>
    public double[] MedianCi95Ms { get; set; } = [];
    public int Negative { get; set; }
    public HistogramData Histogram { get; set; } = new();
    /// <summary>[value_ms, cumulative fraction] pairs (at most 200).</summary>
    public double[][] Cdf { get; set; } = [];
}

public sealed class HistogramData
{
    public double[] EdgesMs { get; set; } = [];
    public int[] Counts { get; set; } = [];
    /// <summary>Samples above the last edge (the range stops at P99.5 so outliers do not flatten the chart).</summary>
    public int Overflow { get; set; }
}

public sealed class LossStats
{
    /// <summary>Steps the phone commanded (distinct seq with a cmd row; state/send when cmd is absent).</summary>
    public int Expected { get; set; }
    public int Sent { get; set; }
    public int Received { get; set; }
    public int Submitted { get; set; }
    public int Observed { get; set; }
    public bool ObserverRan { get; set; }
    public int LostBeforeRecv { get; set; }
    public int LostBeforeObs { get; set; }
    public double LostPct { get; set; }
    public int DuplicatesSend { get; set; }
    public int DuplicatesRecv { get; set; }
    public int OutOfOrder { get; set; }
    /// <summary>recv rows whose mark is not a fingerprint (neutral states, other phones).</summary>
    public int UnmarkedRecv { get; set; }
    public int UnmarkedObs { get; set; }
    public string FinalSubmitButtons { get; set; } = "";
    public string FinalObsButtons { get; set; } = "";
    /// <summary>Buttons or triggers still on at the end (the run ends in neutral).</summary>
    public bool Stuck { get; set; }
}

public sealed class RateStats
{
    public double SendHz { get; set; }
    public double RecvHz { get; set; }
    public double SubmitHz { get; set; }
    public double ObsHz { get; set; }
    public double ObserverPollHz { get; set; }
}

public sealed class AxisStats
{
    public string Axis { get; set; } = "";
    /// <summary>Smallest step between distinct values the observer saw (1 = full resolution).</summary>
    public int ResolutionObserved { get; set; }
    public int DistinctValues { get; set; }
    public int Compared { get; set; }
    public double MeanAbsError { get; set; }
    public double P95AbsError { get; set; }
    public double MaxAbsError { get; set; }
}

public sealed class IntegrityStats
{
    public int ComparedRecv { get; set; }
    public int IntactRecv { get; set; }
    public double IntactRecvPct { get; set; }
    public int ComparedObs { get; set; }
    public int IntactObs { get; set; }
    public double IntactObsPct { get; set; }
    public Dictionary<string, int> MismatchRecv { get; set; } = [];
    public Dictionary<string, int> MismatchObs { get; set; } = [];
}

public sealed class ReconnectStats
{
    /// <summary>Companion sessions: closed → next accepted (USB/Wi-Fi), ms.</summary>
    public List<double> LinkDownToUpMs { get; set; } = [];
    /// <summary>Observer: controller gone → back (any link), ms.</summary>
    public List<double> ObsGoneToBackMs { get; set; } = [];
    /// <summary>Phone-reported reconnect times (done.json reconnect[].reconnect_ns; or phone.csv link_down → link_up), ms.</summary>
    public List<double> PhoneDownToUpMs { get; set; } = [];
    public int Cycles { get; set; }
    public int Reconnected { get; set; }
    /// <summary>done.json: the first state sent after the link came back had the button released.</summary>
    public int ReleasedAfterReconnectPhone { get; set; }
    public int Drops { get; set; }
    /// <summary>Drops after which the observer saw the buttons released (or the pad gone) before the link came back.</summary>
    public int ReleasedAfterDrop { get; set; }
    public double MeanMs { get; set; }
    public double MaxMs { get; set; }
}

public sealed class SeriesData
{
    public string Stage { get; set; } = "";
    /// <summary>Seconds since the first command.</summary>
    public double[] TS { get; set; } = [];
    public double[] Ms { get; set; } = [];
}

public sealed class Headline
{
    public string Stage { get; set; } = "";
    public string Kind { get; set; } = "";
    public double P50Ms { get; set; }
    public double P95Ms { get; set; }
    public double P99Ms { get; set; }
    public int N { get; set; }
    public int Expected { get; set; }
    public int Lost { get; set; }
    public double UncertaintyMs { get; set; }
}

public static class LabJson
{
    public static readonly JsonSerializerOptions Options = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        DictionaryKeyPolicy = null,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        NumberHandling = JsonNumberHandling.AllowNamedFloatingPointLiterals,
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };

    public static string Serialize<T>(T value) => JsonSerializer.Serialize(value, Options);

    public static T? Deserialize<T>(string json) => JsonSerializer.Deserialize<T>(json, Options);
}
