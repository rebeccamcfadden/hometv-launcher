"""bench.py --idle-animation: the frames an idle Home draws, from gfxinfo's window summary."""
from types import SimpleNamespace
import unittest
from unittest import mock

from tools import bench

# `dumpsys gfxinfo com.hometv.launcher` on the API 28 emulator, cut after the histogram.
GFXINFO = """Applications Graphics Acceleration Info:
Uptime: 38122678 Realtime: 38122678

** Graphics info for pid 21286 [com.hometv.launcher] **

Stats since: 37856188778100ns
Total frames rendered: 524
Janky frames: 524 (100.00%)
50th percentile: 150ms
90th percentile: 150ms
95th percentile: 200ms
99th percentile: 250ms
Number Missed Vsync: 516
HISTOGRAM: 5ms=0 6ms=0 150ms=477 200ms=22

Profile data in ms:

\tcom.hometv.launcher/com.hometv.launcher.MainActivity/android.view.ViewRootImpl@8a42e4d (visibility=0)
Total frames rendered: 9
Janky frames: 1 (11.11%)
"""


class GfxSummaryTest(unittest.TestCase):
    def test_reads_the_process_summary(self):
        self.assertEqual(bench.gfx_summary(GFXINFO, 30),
                         {"frames": 524, "janky": 524, "fps": 17.5, "p50_ms": 150, "p90_ms": 150, "p95_ms": 200})

    def test_a_still_home_draws_nothing(self):
        out = GFXINFO.replace("Total frames rendered: 524", "Total frames rendered: 0")
        self.assertEqual(bench.gfx_summary(out, 30)["fps"], 0.0)

    def test_no_summary_is_unknown(self):
        for output in ("", "No process found for: com.hometv.launcher"):
            with self.subTest(output=output):
                self.assertIsNone(bench.gfx_summary(output, 30))


class IdleAnimationTest(unittest.TestCase):
    def test_resets_the_counters_then_reads_them_after_the_wait(self):
        commands = []

        def shell(command):
            commands.append(command)
            return GFXINFO if command == "dumpsys gfxinfo com.hometv.launcher" else ""

        with mock.patch.object(bench, "idle_cpu", return_value=(23.04, 11.96)) as cpu:
            result = bench.idle_animation(SimpleNamespace(shell=shell), "com.hometv.launcher", 30)
        cpu.assert_called_once()
        self.assertEqual(commands, ["dumpsys gfxinfo com.hometv.launcher reset", "dumpsys gfxinfo com.hometv.launcher"])
        self.assertEqual(result["frames"], 524)
        self.assertEqual(result["device_cpu_pct"], 23.0)
        self.assertEqual(result["launcher_cpu_pct"], 12.0)


if __name__ == "__main__":
    unittest.main()
