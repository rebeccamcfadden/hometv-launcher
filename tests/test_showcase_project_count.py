"""SHOWCASE.md opens with "N projects so far.", and nothing was counting the rows.

The line was right at 70 rows. Then four projects landed in three separate merges - the HAAM HDMI
visibility fork, the Bluetooth pill fork and Tellyscope - and one of the three bumped the number
(70 -> 71) while the others did not, so the page offered a reader four projects fewer than it holds.

These tests read the number out of the page rather than copying it into the test, count the project
rows the way the page lays them out (a table row whose first cell is a project link), and fail when
the two disagree, naming both numbers. No other prose is matched.

Adding a project therefore means bumping the number, which is what the failure message says.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHOWCASE = ROOT / "SHOWCASE.md"

# The page held 50+ rows when this test landed. A parser that silently matches nothing (a changed
# row layout, a reordered page) must not read as "0 == 0, all good".
MIN_ROWS = 50


def project_rows(lines):
    """[(line number, line)] for every project row: a "|" row whose first cell is a link."""
    found = []
    for number, line in enumerate(lines, 1):
        stripped = line.strip()
        if re.match(r"^\|\s*\[", stripped):
            found.append((number, stripped))
    return found


def stated_number(lines):
    """The number in "N projects so far.", or None when the page does not carry the line."""
    for line in lines:
        match = re.match(r"^(\d+)\s+projects so far\.$", line.strip())
        if match:
            return int(match.group(1))
    return None


def audit(showcase_lines):
    """(stated, counted, error) - error is None when the page and its rows agree."""
    counted = len(project_rows(showcase_lines))
    stated = stated_number(showcase_lines)
    if stated is None:
        return None, counted, "SHOWCASE.md carries no 'N projects so far.' line"
    if counted < MIN_ROWS:
        return stated, counted, (
            f"only {counted} project rows were read, fewer than the {MIN_ROWS} the page is known to "
            "hold - the row layout changed and this check is reading nothing"
        )
    if stated != counted:
        return stated, counted, (
            f"SHOWCASE.md says {stated} projects and its tables hold {counted} rows: bump the number "
            "bump the number when you add a project"
        )
    return stated, counted, None


class ShowcaseProjectCount(unittest.TestCase):
    def setUp(self):
        self.showcase = SHOWCASE.read_text(encoding="utf-8").splitlines()

    def test_the_page_states_the_number_of_rows_it_holds(self):
        stated, counted, error = audit(self.showcase)
        self.assertIsNone(error, error)
        self.assertEqual(stated, counted)

    def test_the_count_line_sits_in_the_intro_before_the_first_section(self):
        headings = [n for n, line in enumerate(self.showcase) if line.startswith("## ")]
        counted_at = [
            n for n, line in enumerate(self.showcase) if stated_number([line]) is not None
        ]
        self.assertEqual(len(counted_at), 1, "expected exactly one 'N projects so far.' line")
        self.assertLess(counted_at[0], headings[0], "the count line belongs above the first section")


class TheCheckerItself(unittest.TestCase):
    """The repo-wide tests above are only worth their failures if these hold."""

    PAGE = [
        "71 projects so far.",
        "",
        "## Forks of the launcher",
        "",
        "| Project | What it is | Built on | By |",
        "|---|---|---|---|",
        "| [One](https://example.com/one) | A fork | TCL G08 | [me](https://example.com) |",
        "| [Two](https://example.com/two) | A fork | TCL G08 | [me](https://example.com) |",
    ]

    def test_a_short_count_is_reported_with_both_numbers(self):
        _, counted, error = audit(self.PAGE + ["| [Three](https://example.com/3) | x | y | z |"] * 48)
        self.assertEqual(counted, 50)
        self.assertIn("says 71", error)
        self.assertIn("hold 50", error)

    def test_an_agreeing_page_is_quiet(self):
        page = ["50 projects so far."] + self.PAGE[2:] + [
            f"| [P{i}](https://example.com/{i}) | x | y | z |" for i in range(48)
        ]
        stated, counted, error = audit(page)
        self.assertIsNone(error)
        self.assertEqual((stated, counted), (50, 50))

    def test_a_page_without_the_line_is_reported_rather_than_read_as_zero(self):
        _, _, error = audit(self.PAGE[2:])
        self.assertIn("no 'N projects so far.' line", error)

    def test_a_header_row_and_the_brand_index_are_not_project_rows(self):
        page = self.PAGE + ["", "- **TCL:** [One](https://example.com/one)"]
        self.assertEqual(len(project_rows(page)), 2)

    def test_a_number_in_prose_is_not_the_count_line(self):
        self.assertIsNone(stated_number(["About 71 projects so far."]))


if __name__ == "__main__":
    unittest.main()
