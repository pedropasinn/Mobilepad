"""
End-to-end input latency of Mobilepad, from the moment the app sends a press to the moment
Windows reports it to a game (read the way games do, through SDL).

How it works
  1. The phone's and the PC's clocks are compared over ADB many times; the exchanges with the
     shortest round trip give the offset (before and after the run, to follow any drift).
  2. The app is told to run its latency test (it presses and releases A by itself, N times, at
     irregular intervals, and writes when each press was sent).
  3. Meanwhile this script polls the controller as fast as it can and notes when each press
     arrives.
  4. Each press is matched to its arrival; latency = arrival - sent (in PC time).

Usage (phone plugged in for ADB in every case, for the clock sync and the trigger):
  .venv\\Scripts\\python run_latency.py --label usb      # app connected over USB or Wi-Fi
  .venv\\Scripts\\python run_latency.py --label bt       # app connected over Bluetooth
  .venv\\Scripts\\python run_latency.py --compare        # table of every saved run

Pick the controller with --pad "name part" if more than one is connected (see --list).
Results go to results/<label>-<date>.json and .csv.
"""
import argparse, csv, glob, json, os, statistics, subprocess, sys, time
from datetime import datetime

PKG_DEV = "io.github.pedropasinn.mobilepad.dev"
PKG_REL = "io.github.pedropasinn.mobilepad"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "results")


def adb(*args, timeout=10):
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout).stdout.strip()


def clock_offset(samples=200):
    """
    Phone wall clock minus PC wall clock (ns), from the exchanges with the shortest round trip.
    One shell stays open and answers each line with the time, so an exchange costs a few ms
    (a new `adb shell date` per sample costs ~50 ms and is lopsided: the phone reads its clock
    late in the round trip).
    """
    sh = subprocess.Popen(["adb", "shell", "while read l; do date +%s%N; done"],
                          stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
    best = []
    try:
        for _ in range(samples):
            t0 = time.time_ns()
            sh.stdin.write("x\n"); sh.stdin.flush()
            out = sh.stdout.readline()
            t1 = time.time_ns()
            try:
                phone = int(out.split()[0])
            except (ValueError, IndexError):
                continue
            best.append((t1 - t0, phone - (t0 + t1) // 2, (t0 + t1) // 2))
    finally:
        sh.kill()
    best.sort()
    keep = best[: max(3, len(best) // 6)]
    rtt = statistics.median(k[0] for k in keep)
    return statistics.median(k[1] for k in keep), statistics.median(k[2] for k in keep), rtt


def installed_pkg():
    pkgs = adb("shell", "pm", "list", "packages", "io.github.pedropasinn.mobilepad")
    return PKG_DEV if PKG_DEV in pkgs else PKG_REL


def open_pad(hint):
    import pygame
    pygame.init()
    pygame.joystick.init()
    pads = [pygame.joystick.Joystick(i) for i in range(pygame.joystick.get_count())]
    for p in pads:
        if not p.get_init(): p.init()
    if hint:
        pads = [p for p in pads if hint.lower() in p.get_name().lower()]
    if not pads:
        sys.exit("No controller found (use --list to see them, --pad to choose).")
    return pygame, pads[0]


def record(pygame, pad, seconds):
    """Every button-down edge, with the PC wall clock, polling as fast as SDL allows."""
    downs = []
    prev = [False] * pad.get_numbuttons()
    end = time.monotonic() + seconds
    polls = 0
    while time.monotonic() < end:
        pygame.event.pump()
        now = time.time_ns()
        polls += 1
        for i in range(len(prev)):
            b = bool(pad.get_button(i))
            if b and not prev[i]:
                downs.append((now, i))
            prev[i] = b
    return downs, polls / seconds


def pct(values, q):
    s = sorted(values)
    return s[min(len(s) - 1, int(round(q / 100 * (len(s) - 1))))]


def run(args):
    os.makedirs(OUT, exist_ok=True)
    pygame, pad = open_pad(args.pad)
    pkg = installed_pkg()
    print(f"Controller: {pad.get_name()}  |  app: {pkg}")
    print("Syncing clocks…")
    off_a, at_a, rtt_a = clock_offset()
    remote = f"/sdcard/Android/data/{pkg}/files/latency.csv"
    adb("shell", "rm", "-f", remote)
    adb("shell", "am", "start", "-f", "0x20000000", "-n", f"{pkg}/io.github.pedropasinn.mobilepad.MainActivity",
        "--ez", "latency", "true", "--ei", "latencyCount", str(args.count))
    seconds = 1.5 + args.count * 0.19 + 2
    print(f"Recording {args.count} presses (~{seconds:.0f} s)… keep the app open, do not touch the screen.")
    downs, rate = record(pygame, pad, seconds)
    off_b, at_b, rtt_b = clock_offset()
    raw = adb("exec-out", "cat", remote, timeout=20)
    sent = [int(l.split(",")[1]) for l in raw.splitlines()[1:] if l.count(",") == 2]
    if not sent:
        sys.exit("The app wrote no log: is it open and on the controller screen?")

    def to_pc(phone_ns):
        # Offset interpolated between the two syncs (clock drift over the run).
        k = (phone_ns - off_a - at_a) / max(1, at_b - at_a)
        return phone_ns - (off_a + (off_b - off_a) * min(max(k, 0), 1))

    # Each arrival belongs to the last press sent before it (a lost press must not shift every
    # later match by one press); a press keeps its first arrival only.
    sent_pc = [to_pc(s) for s in sent]
    first = {}
    k = 0
    for t in sorted(t for t, _ in downs):
        while k + 1 < len(sent_pc) and sent_pc[k + 1] <= t + 2e6:
            k += 1
        if sent_pc[k] <= t + 2e6 and k not in first:
            first[k] = t
    lat, rows = [], []
    for i, s_pc in enumerate(sent_pc):
        if i in first:
            ms = (first[i] - s_pc) / 1e6
            lat.append(ms)
            rows.append((i, round(ms, 3)))
        else:
            rows.append((i, ""))

    lost = len(sent) - len(lat)
    summary = {
        "label": args.label, "controller": pad.get_name(), "when": datetime.now().isoformat(timespec="seconds"),
        "presses": len(sent), "received": len(lat), "lost": lost,
        "poll_hz": round(rate), "sync_rtt_ms": round(max(rtt_a, rtt_b) / 1e6, 2),
        "clock_drift_ms": round((off_b - off_a) / 1e6, 3),
    }
    if lat:
        summary.update({
            "mean_ms": round(statistics.mean(lat), 2), "p50_ms": round(pct(lat, 50), 2), "p90_ms": round(pct(lat, 90), 2),
            "p99_ms": round(pct(lat, 99), 2), "min_ms": round(min(lat), 2), "max_ms": round(max(lat), 2),
            "jitter_ms": round(statistics.pstdev(lat), 2),
        })
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    base = os.path.join(OUT, f"{args.label}-{stamp}")
    json.dump(summary, open(base + ".json", "w"), indent=2)
    with open(base + ".csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["press", "latency_ms"])
        w.writerows(rows)
    print(json.dumps(summary, indent=2))
    print("Note: the clock sync is good to about ±(sync_rtt/2); compare runs made the same way.")


def compare():
    runs = [json.load(open(p)) for p in sorted(glob.glob(os.path.join(OUT, "*.json")))]
    if not runs:
        sys.exit("No results yet.")
    cols = ["label", "when", "received", "lost", "p50_ms", "p90_ms", "p99_ms", "max_ms", "jitter_ms", "sync_rtt_ms"]
    print(" | ".join(f"{c:>10}" for c in cols))
    for r in runs:
        print(" | ".join(f"{str(r.get(c, '')):>10}" for c in cols))


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--label", default="run", help="name of this run, e.g. usb or bt")
    ap.add_argument("--count", type=int, default=200)
    ap.add_argument("--pad", help="part of the controller's name to use")
    ap.add_argument("--list", action="store_true", help="list controllers and exit")
    ap.add_argument("--compare", action="store_true", help="table of all saved runs")
    a = ap.parse_args()
    if a.compare:
        compare()
    elif a.list:
        pg, _ = None, None
        import pygame
        pygame.init(); pygame.joystick.init()
        for i in range(pygame.joystick.get_count()):
            print(i, pygame.joystick.Joystick(i).get_name())
    else:
        run(a)
