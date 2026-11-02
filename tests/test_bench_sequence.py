"""The key probe starts each series on Home's app row, and every key it sends moves the focus; no TV is used."""
import re
import unittest
from unittest import mock

from tools import bench

HOME, UP, DOWN, LEFT, RIGHT = 3, 19, 20, 21, 22


class Home:
    """Home's focus as rows, top to bottom: the nav bar, the app row, then the rows below it. Up and Down keep each
    row's column (Shell's focus memory); a row in `swallows` takes Left and Right without moving (the weather strip,
    HomePage.steer). HOME puts the focus on the first app (HomePage.defaultFocus)."""

    APPS = 1

    def __init__(self, rows, swallows=()):
        self.rows = rows
        self.swallows = set(swallows)
        self.home()

    def home(self):
        self.columns = [0] * len(self.rows)
        self.row = self.APPS

    def press(self, key):
        """Moves the focus; returns whether it moved."""
        if key == HOME:
            self.home()
            return True
        if key in (LEFT, RIGHT):
            if self.row in self.swallows:
                return False
            col = self.columns[self.row] + (1 if key == RIGHT else -1)
            if not 0 <= col < self.rows[self.row]:
                return False
            self.columns[self.row] = col
            return True
        row = self.row + (1 if key == DOWN else -1)
        if not 0 <= row < len(self.rows):
            return False
        self.row = row
        return True


class FakeTV:
    def __init__(self):
        self.keys = []

    def shell(self, command):
        match = re.fullmatch(r"input keyevent (\d+)", command)
        if match:
            self.keys.append(int(match.group(1)))
        return ""


def setup_keys():
    tv = FakeTV()
    with mock.patch.object(bench.time, "sleep"):
        bench.start_series(tv)
    return tv.keys


class SequenceTest(unittest.TestCase):
    # Homes like the TCL's on 2026-11-02: four nav items, seven apps, and below them a row of inputs and shortcuts
    # and the weather strip, in either order, or only one of them.
    HOMES = [
        ([4, 7, 3, 1], {3}),
        ([4, 7, 1, 3], {2}),
        ([4, 7, 1], {2}),
        ([4, 7, 2], set()),
        ([4, 4, 1], {2}),
    ]

    def run_series(self, rows, swallows, cycles=3):
        home = Home(rows, swallows)
        for key in setup_keys():
            home.press(key)
        start = (home.row, home.columns[home.row])
        for cycle in range(cycles):
            for i, key in enumerate(bench.DEFAULT_SEQUENCE):
                name = bench.KEYCODE_NAMES[key]
                if key in (LEFT, RIGHT):
                    self.assertEqual(Home.APPS, home.row, f"key {i} ({name}) is off the app row, cycle {cycle}, {rows}")
                self.assertTrue(home.press(key), f"key {i} ({name}) moved nothing, cycle {cycle}, {rows}")
            self.assertEqual(start, (home.row, home.columns[home.row]), "a cycle ends where it started")

    def test_a_series_starts_on_the_first_app(self):
        self.assertEqual([HOME], setup_keys())

    def test_every_left_and_right_moves_along_the_app_row(self):
        for rows, swallows in self.HOMES:
            with self.subTest(rows=rows):
                self.run_series(rows, swallows)

    def test_it_still_moves_between_rows_and_along_one(self):
        self.assertTrue({UP, DOWN, LEFT, RIGHT} <= set(bench.DEFAULT_SEQUENCE))


if __name__ == "__main__":
    unittest.main()
