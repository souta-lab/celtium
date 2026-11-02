"""The creative grid's checker separates "the container is empty" from "the renderer drew nothing".

`scripts/cpu-contract/creative-grid-check.py` reads a run's report (`creativeGridLayout`, `creativeGridFilled`, and the
per-slot `creativeGridAt*Contents` when the snapshots fired) and the presented dump, turns the layout into cells with
the picture's own scale, and requires every slot the container holds a stack in to carry an icon.

The leg that takes that picture needs a GPU and a five-minute game launch, so this test pins the reading itself: a
synthetic grid whose filled cells carry icons has to pass, and the same grid with the icons removed - the shape the
2026-11-02 evidence measured, where 19 of 24 cells differed from the reference - has to fail. A checker that cannot
fail would make the leg's PASSED meaningless, which is the trap this file exists to close.
"""
import importlib.util
import json
import pathlib
import tempfile
import unittest

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts" / "cpu-contract" / "creative-grid-check.py"

GUI_WIDTH = 427
GUI_HEIGHT = 240
SCALE = 3
LEFT = 116
TOP = 52
FIRST_X = 9
FIRST_Y = 18
PITCH = 18
COLUMNS = 9
ROWS = 6
SLOTS = COLUMNS * ROWS
#: The slot background the creative container texture draws, and the icon colour painted over it.
SLOT_BACKGROUND = (139, 139, 139)
ICON = (200, 120, 40)
#: The slots the synthetic container holds a stack in: the first 46, which is the measured leg's count.
FILLED = list(range(46))


def layout_text():
    return ("gui=%dx%d left=%d top=%d first=%d,%d pitch=%d cols=%d rows=%d slots=%d"
            % (GUI_WIDTH, GUI_HEIGHT, LEFT, TOP, FIRST_X, FIRST_Y, PITCH, COLUMNS, ROWS, SLOTS))


def contents_text(slots):
    return " ".join("%d=minecraft:stonex1" % index for index in slots)


def report(filled=FILLED, with_snapshots=True):
    data = {
        "creativeGridLayout": layout_text(),
        "creativeGridSlots": SLOTS,
        "creativeGridFilled": len(filled),
        "creativeGridFirstStack": "Stone x1",
    }
    if with_snapshots:
        data["creativeGridAt60Screen"] = "net.minecraft.client.gui.inventory.GuiContainerCreative"
        data["creativeGridAt60Slots"] = SLOTS
        data["creativeGridAt60Filled"] = len(filled)
        data["creativeGridAt60Contents"] = contents_text(filled)
        data["creativeGridAt60Layout"] = layout_text()
    return data


def picture(path, drawn):
    """Draws the grid at scale 3: every slot's background, and an icon in the slots named by `drawn`."""
    image = Image.new("RGB", (GUI_WIDTH * SCALE, GUI_HEIGHT * SCALE), (24, 24, 32))
    for index in range(SLOTS):
        column = index % COLUMNS
        row = index // COLUMNS
        x = (LEFT + FIRST_X + column * PITCH) * SCALE
        y = (TOP + FIRST_Y + row * PITCH) * SCALE
        image.paste(SLOT_BACKGROUND, (x, y, x + PITCH * SCALE, y + PITCH * SCALE))
        if index in drawn:
            # An icon covers the middle of the cell and leaves the slot background around it, which is what a real
            # item icon does (its texture has transparent margins). The checker insets by one GUI unit, so the icon
            # is drawn inside that inset area rather than over the slot's border.
            inset = 3
            image.paste(ICON, (x + inset * SCALE, y + inset * SCALE,
                               x + (PITCH - inset) * SCALE, y + (PITCH - inset) * SCALE))
    image.save(path)


def load_check():
    spec = importlib.util.spec_from_file_location("creative_grid_check", CHECK)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class CreativeGridCheckTest(unittest.TestCase):

    def setUp(self):
        self.check = load_check()
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = pathlib.Path(self.directory.name)

    def run_check(self, data, drawn):
        report_path = self.root / "celtium-validation.json"
        report_path.write_text(json.dumps(data), encoding="utf-8")
        picture_path = self.root / "presented.png"
        picture(picture_path, drawn)
        return self.check.main(["--report", str(report_path), "--picture", str(picture_path), "--leg", "synthetic"])

    def test_a_grid_whose_filled_cells_carry_icons_passes(self):
        self.assertEqual(0, self.run_check(report(), FILLED))

    def test_a_grid_that_draws_nothing_fails(self):
        # The 2026-11-02 shape: the container holds 46 stacks and the picture carries no icon at all.
        self.assertEqual(1, self.run_check(report(), []))

    def test_one_missing_icon_fails_and_is_named(self):
        drawn = [index for index in FILLED if index != 17]
        self.assertEqual(1, self.run_check(report(), drawn))

    def test_the_count_fallback_still_fails_when_the_grid_is_empty(self):
        # Without the per-slot snapshots the check falls back to the count, and an empty picture must still fail.
        self.assertEqual(1, self.run_check(report(with_snapshots=False), []))

    def test_the_count_fallback_passes_when_every_stack_is_drawn(self):
        self.assertEqual(0, self.run_check(report(with_snapshots=False), FILLED))

    def test_a_report_without_a_layout_fails_rather_than_passing(self):
        # No geometry anywhere: the check must refuse, not pass on the count alone. A run whose creative scene never
        # ran has no layout, and "no cells measured" is not "every cell drawn".
        data = report()
        for key in [k for k in data if k.endswith("Layout")]:
            del data[key]
        self.assertEqual(1, self.run_check(data, FILLED))

    def test_the_layout_is_read_from_the_last_snapshot_when_the_switch_one_is_absent(self):
        data = report()
        del data["creativeGridLayout"]
        self.assertEqual(0, self.run_check(data, FILLED))


if __name__ == "__main__":
    unittest.main()
