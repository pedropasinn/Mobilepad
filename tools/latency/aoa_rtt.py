"""Round trip PC -> phone -> PC over adb (TCP via `adb forward`) and over the USB accessory (AOA).

The phone side is lab/AccessoryEchoActivity (echoes every packet). Same packets, same pacing on
both paths, so the comparison is fair: one-way latency ~ RTT / 2.

    python aoa_rtt.py adb  [--count 3000]
    python aoa_rtt.py aoa  [--count 3000]
    python aoa_rtt.py switch            # put the phone in accessory mode (needs `adb kill-server` first)
"""
import argparse
import random
import socket
import statistics
import struct
import subprocess
import time

PKT = 32


def pct(v, q):
    v = sorted(v)
    h = (len(v) - 1) * q
    lo = int(h)
    return v[lo] + (v[min(lo + 1, len(v) - 1)] - v[lo]) * (h - lo)


def report(name, rtts_ns, lost):
    ms = [x / 1e6 for x in rtts_ns]
    print(f"{name}: n={len(ms)} lost={lost}  RTT p50={pct(ms,.5):.3f} p90={pct(ms,.9):.3f} "
          f"p95={pct(ms,.95):.3f} p99={pct(ms,.99):.3f} p99.9={pct(ms,.999):.3f} max={max(ms):.3f} ms  "
          f"mean={statistics.mean(ms):.3f} sd={statistics.pstdev(ms):.3f}")
    over = sum(1 for x in ms if x > 20)
    print(f"   RTT > 20 ms: {over} ({100*over/len(ms):.2f}%)")


def paced(count):
    for seq in range(count):
        yield seq
        # Same rhythm as the Lab latency test: 35-180 ms between packets would be slow; use 5-15 ms.
        time.sleep(random.uniform(0.005, 0.015))


def run_adb(count):
    subprocess.run(["adb", "forward", "tcp:47900", "tcp:47900"], check=True, capture_output=True)
    s = socket.create_connection(("127.0.0.1", 47900), timeout=2)
    s.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    rtts, lost = [], 0
    for seq in paced(count):
        pkt = struct.pack("<IQ", seq, time.perf_counter_ns()).ljust(PKT, b"\0")
        t0 = time.perf_counter_ns()
        s.sendall(pkt)
        got = b""
        try:
            while len(got) < PKT:
                chunk = s.recv(PKT - len(got))
                if not chunk:
                    raise ConnectionError("closed")
                got += chunk
        except socket.timeout:
            lost += 1
            continue
        rtts.append(time.perf_counter_ns() - t0)
    report("adb  ", rtts, lost)


def backend():
    import libusb_package
    return libusb_package.get_libusb1_backend()


def switch():
    import usb.core
    be = backend()
    d = usb.core.find(idVendor=0x04E8, backend=be)
    if d is None:
        print("no Samsung device in normal mode (already an accessory?)")
        return
    proto = d.ctrl_transfer(0xC0, 51, 0, 0, 2)
    print("AOA protocol", proto[0] | proto[1] << 8)
    for i, s in enumerate(["Mobilepad", "Companion", "Mobilepad companion link", "1",
                           "https://github.com/pedropasinn/Mobilepad", "0001"]):
        d.ctrl_transfer(0x40, 52, 0, i, s.encode() + b"\0")
    d.ctrl_transfer(0x40, 53, 0, 0, None)
    print("accessory mode requested")


def run_aoa(count):
    import usb.core
    import usb.util
    be = backend()
    d = None
    for pid in (0x2D00, 0x2D01):
        d = usb.core.find(idVendor=0x18D1, idProduct=pid, backend=be)
        if d:
            break
    if d is None:
        raise SystemExit("phone is not in accessory mode: run `switch` first")
    usb.util.claim_interface(d, 0)
    intf = d.get_active_configuration()[(0, 0)]
    ep_out = usb.util.find_descriptor(intf, custom_match=lambda e: usb.util.endpoint_direction(e.bEndpointAddress) == usb.util.ENDPOINT_OUT)
    ep_in = usb.util.find_descriptor(intf, custom_match=lambda e: usb.util.endpoint_direction(e.bEndpointAddress) == usb.util.ENDPOINT_IN)
    rtts, lost = [], 0
    for seq in paced(count):
        pkt = struct.pack("<IQ", seq, time.perf_counter_ns()).ljust(PKT, b"\0")
        t0 = time.perf_counter_ns()
        ep_out.write(pkt, timeout=1000)
        try:
            got = bytes(ep_in.read(512, timeout=1000))
        except usb.core.USBTimeoutError:
            lost += 1
            continue
        if struct.unpack_from("<I", got)[0] != seq:
            lost += 1
            continue
        rtts.append(time.perf_counter_ns() - t0)
    report("aoa  ", rtts, lost)
    usb.util.release_interface(d, 0)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["adb", "aoa", "switch"])
    ap.add_argument("--count", type=int, default=3000)
    a = ap.parse_args()
    random.seed(1)
    {"adb": lambda: run_adb(a.count), "aoa": lambda: run_aoa(a.count), "switch": switch}[a.mode]()
