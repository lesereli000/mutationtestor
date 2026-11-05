import tempfile
import unittest
from pathlib import Path

from src.inventory import export_if_available


class InventoryTests(unittest.TestCase):
    def test_positive_count_creates_report(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "inventory.txt"
            self.assertTrue(export_if_available(1, report))
            self.assertEqual("Inventory is available\n", report.read_text())

    def test_zero_count_does_not_create_report(self):
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "inventory.txt"
            self.assertFalse(export_if_available(0, report))
            self.assertFalse(report.exists())


if __name__ == "__main__":
    unittest.main()
