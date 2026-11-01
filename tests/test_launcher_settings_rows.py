"""README: the Launcher settings section names every section the settings page has.

The sections are read from the page, not from a list kept here. `SettingsSections.all` adds each section's class in
order, and each class names its label: `label()` returns `R.string.<name>`, or a stub passes it to `SectionStub`'s
constructor. So a section added to the page without a README line fails in CI, while rewording a README line stays
free as long as the section's own label - the text the TV shows - is still there.

The old `LauncherSettingsActivity` and its rows were replaced in M9 by the split Launcher settings page
(`LauncherSettingsPage`, design_preview/README.md § Launcher settings), and this test with them.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "launcher/app/src/main/java/com/gathin/tvhome"
REGISTRY = SOURCES / "SettingsSections.java"
VALUES = ROOT / "launcher/app/src/main/res/values"
README = ROOT / "docs" / "launcher.md"

SUMMARY = "Launcher settings"


def english_strings(texts):
    """name -> value for every <string> in the given res/values files (the English text Android shows)."""
    out = {}
    for text in texts:
        for match in re.finditer(r"<string name=\"([a-z_0-9]+)\"[^>]*>(.*?)</string>", text, re.S):
            out[match.group(1)] = match.group(2).strip()
    return out


def normalise(text):
    """One line, so a label the README wraps across two lines still matches."""
    return re.sub(r"\s+", " ", text.replace("**", "").replace("\\", "")).strip()


def row_label(name, strings):
    """A resource name as the row's label, without any placeholder: 'x_state' ("Animations: %1$s") is 'Animations'."""
    value = strings.get(name)
    if value is None:
        return None
    value = re.sub(r"%\d\$s", "", value)
    return value.strip().rstrip(":").strip() or None


def section_classes(registry_text):
    """The section classes `SettingsSections.all` adds, in the order it adds them."""
    return re.findall(r"\bout\.add\(new (\w+)\(", registry_text)


def label_name(class_text):
    """The string resource a section class shows as its label, or None."""
    found = re.search(r"\bint label\(\)\s*\{\s*return R\.string\.([a-z_0-9]+);", class_text)
    if not found:
        found = re.search(r"\bsuper\(\s*\"[a-z]+\",\s*R\.string\.([a-z_0-9]+)", class_text)
    return found.group(1) if found else None


def section_labels(registry_text, read_class, strings):
    """Every section the settings page lists, in order, as [(class, label)]."""
    rows = []
    for name in section_classes(registry_text):
        label = row_label(label_name(read_class(name)) or "", strings)
        if label:
            rows.append((name, label))
    return rows


def missing_rows(section, rows):
    """The (id, label) rows the section does not name."""
    seen = normalise(section).lower()
    return [(row_id, label) for row_id, label in rows if label.lower() not in seen]


def detail_section(page_text, heading):
    """The text under one "## heading" up to the next "## " heading, or '' - so a row named three
    sections further down does not count as the settings page being documented."""
    start = page_text.find("\n## %s\n" % heading)
    if start < 0:
        return ""
    end = page_text.find("\n## ", start + 1)
    return page_text[start:end if end > 0 else len(page_text)]


def read_class(name):
    return (SOURCES / ("%s.java" % name)).read_text(encoding="utf-8")


class PageSections(unittest.TestCase):
    """The README against the settings page it describes."""

    def setUp(self):
        strings = english_strings(p.read_text(encoding="utf-8") for p in sorted(VALUES.glob("strings*.xml")))
        self.registry = REGISTRY.read_text(encoding="utf-8")
        self.rows = section_labels(self.registry, read_class, strings)
        self.section = detail_section(README.read_text(encoding="utf-8"), SUMMARY)

    def test_page_sections_are_read(self):
        """The page has its sections, and each one resolved to the label the TV shows."""
        classes = section_classes(self.registry)
        self.assertGreaterEqual(len(classes), 10, classes)
        self.assertEqual(len(self.rows), len(classes), (self.rows, classes))
        labels = [label for _, label in self.rows]
        self.assertEqual(len(labels), len(set(labels)), labels)

    def test_readme_names_every_section(self):
        self.assertTrue(self.section, "README has no '%s' section" % SUMMARY)
        self.assertEqual(missing_rows(self.section, self.rows), [])

    def test_a_label_is_found_in_a_section_or_a_stub(self):
        section = "final class X implements SettingsSection {\n    public int label() {\n" \
                  "        return R.string.settings_motion;\n    }\n}"
        stub = "    XStub() {\n        super(\"apps\", R.string.settings_apps, R.drawable.ic_ui_apps,\n"
        self.assertEqual(label_name(section), "settings_motion")
        self.assertEqual(label_name(stub), "settings_apps")
        self.assertIsNone(label_name("class Y {}"))

    def test_the_registry_is_read_in_order(self):
        registry = ("if (a) out.add(new AccountSectionStub());\nout.add(new WeatherSection());\n"
                    "out.add(new MotionSection(config));\n")
        self.assertEqual(section_classes(registry), ["AccountSectionStub", "WeatherSection", "MotionSection"])
        strings = {"settings_weather": "Weather", "settings_motion": "Motion"}
        classes = {"AccountSectionStub": "class A {}",
                   "WeatherSection": "int label() { return R.string.settings_weather; }",
                   "MotionSection": "int label() { return R.string.settings_motion; }"}
        self.assertEqual(section_labels(registry, classes.get, strings),
                         [("WeatherSection", "Weather"), ("MotionSection", "Motion")])

    def test_a_section_the_readme_leaves_out_is_reported(self):
        rows = [("WeatherSection", "Weather"), ("AboutSection", "About")]
        self.assertEqual(missing_rows("- **Weather**: the city.", rows), [("AboutSection", "About")])

    def test_a_section_named_in_another_block_does_not_count(self):
        readme = ("# Launcher\n\n## %s\n\nNothing here.\n\n## Elsewhere\n\n- **Wallpaper** this is not the "
                  "settings page.\n" % SUMMARY)
        section = detail_section(readme, SUMMARY)
        self.assertIn("Nothing here", section)
        self.assertNotIn("Elsewhere", section)
        self.assertEqual(missing_rows(section, [("w", "Wallpaper")]), [("w", "Wallpaper")])

    def test_a_placeholder_is_not_part_of_the_label(self):
        strings = {"animations_state": "Animations: %1$s", "wallpaper_label": "Wallpaper"}
        self.assertEqual(row_label("animations_state", strings), "Animations")
        self.assertEqual(missing_rows("**Animations** on or off", [("a", "Animations")]), [])

    def test_a_label_wrapped_across_lines_still_matches(self):
        section = "**Stores for rent\n  and buy** where prices come from."
        self.assertEqual(missing_rows(section, [("s", "Stores for rent and buy")]), [])


if __name__ == "__main__":
    unittest.main()
