"""F8: the launcher's package was renamed (com.gathin.tvhome -> com.hometv.launcher) and the old one stays installed,
disabled, on a TV that ran it. The tools still know it as this project's launcher:

- restore: a record taken while either name owned Home restores Home to the stock launcher, as it always has for this
  launcher, rather than point Home at a disabled package;
- check: a TV not reinstalled since still has an expected launcher (so a stock launcher taking Home back is drift),
  and --fix prefers this launcher, the new name first.

Pure Python, against the tools' functions; the install scripts' side was run against an emulator (PR #57).
"""
import io
import sys
import tempfile
import unittest
from contextlib import redirect_stderr
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import check  # noqa: E402
import recovery  # noqa: E402

STOCK = "com.google.android.tvlauncher"


class RestoreKnowsTheOldNameTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        patcher = mock.patch.object(recovery.Path, "home", return_value=self.base / "home")
        patcher.start()
        self.addCleanup(patcher.stop)
        self.list_path = self.base / "debloat.txt"
        self.list_path.write_text("com.example.bloat\n")

    def restore_with_home(self, home):
        record = {"version": recovery.VERSION, "created": "2026-11-01", "home": home,
                  "packages": {"com.example.bloat": "enabled", STOCK: "enabled"}, "settings": {}}
        recovery.write_record(recovery.record_path(self.list_path, "tv"), record)
        with redirect_stderr(io.StringIO()):
            return recovery.restore_commands(self.list_path, STOCK, "tv")

    def home_command(self, commands):
        home = [c for c in commands if c.startswith("cmd package set-home-activity")]
        self.assertEqual(len(home), 1, commands)
        return home[0]

    def test_the_launcher_before_its_rename_hands_home_back_to_the_stock_launcher(self):
        cmd = self.home_command(self.restore_with_home("com.gathin.tvhome/.MainActivity"))
        self.assertNotIn("com.gathin.tvhome", cmd)
        self.assertIn("resolve-activity", cmd)
        self.assertIn(STOCK, cmd)

    def test_the_renamed_launcher_hands_home_back_to_the_stock_launcher(self):
        cmd = self.home_command(self.restore_with_home("com.hometv.launcher/.MainActivity"))
        self.assertNotIn("com.hometv.launcher", cmd)
        self.assertIn("resolve-activity", cmd)

    def test_a_stock_home_is_still_replayed(self):
        cmd = self.home_command(self.restore_with_home(f"{STOCK}/.MainActivity"))
        self.assertEqual(cmd, f"cmd package set-home-activity --user 0 {STOCK}/.MainActivity")


class CheckKnowsTheOldNameTest(unittest.TestCase):
    def test_a_tv_still_on_the_old_launcher_expects_it_on_home(self):
        self.assertEqual(check.expected_launcher({STOCK, "com.gathin.tvhome"}), "com.gathin.tvhome")

    def test_the_new_name_wins_once_both_are_installed(self):
        self.assertEqual(check.expected_launcher({STOCK, "com.gathin.tvhome", "com.hometv.launcher"}), "com.hometv.launcher")

    def test_no_launcher_of_ours_expects_none(self):
        self.assertIsNone(check.expected_launcher({STOCK}))

    def test_an_asked_for_launcher_is_kept(self):
        self.assertEqual(check.expected_launcher({STOCK, "org.example.home"}, "org.example.home"), "org.example.home")

    def launcher_to_use(self, homes):
        with mock.patch.object(check, "home_activities", return_value=homes):
            return check.launcher_to_use(object(), set())

    def test_fix_prefers_the_old_launcher_over_the_stock_one(self):
        self.assertEqual(self.launcher_to_use([f"{STOCK}/.MainActivity", "com.gathin.tvhome/.MainActivity"]),
                         "com.gathin.tvhome/.MainActivity")

    def test_fix_prefers_the_new_name_over_the_old(self):
        self.assertEqual(self.launcher_to_use(["com.gathin.tvhome/.MainActivity", "com.hometv.launcher/.MainActivity",
                                               f"{STOCK}/.MainActivity"]), "com.hometv.launcher/.MainActivity")


if __name__ == "__main__":
    unittest.main()
