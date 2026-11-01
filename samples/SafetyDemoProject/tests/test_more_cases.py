import sqlite3
import unittest

from src.countdown import countdown
from src.archive import remove_if_expired
from src.database import add_stock
from src.discount import has_discount
from src.notification import notify_if_available
from src.pricing import total_price
from src.jobs import run_if_pending


class MoreSafetyCases(unittest.TestCase):
    def test_database_uses_only_an_in_memory_database(self):
        with sqlite3.connect(":memory:") as connection:
            connection.execute("CREATE TABLE stock (quantity INTEGER)")
            self.assertFalse(add_stock(connection, 0))
            self.assertTrue(add_stock(connection, 2))
            self.assertEqual([(2,)], connection.execute("SELECT quantity FROM stock").fetchall())

    def test_notification_uses_a_fake_transport(self):
        calls = []
        self.assertFalse(notify_if_available(0, calls.append))
        self.assertTrue(notify_if_available(1, calls.append))
        self.assertEqual(["https://example.invalid/inventory"], calls)

    def test_loop_terminates_and_handles_zero(self):
        self.assertEqual([3, 2, 1], countdown(3))
        self.assertEqual([], countdown(0))

    def test_calculation(self):
        self.assertEqual(13, total_price(10, 3))

    def test_discount_positive_and_negative(self):
        self.assertTrue(has_discount(1))
        self.assertFalse(has_discount(-1))

    def test_job_uses_fake_process_runner(self):
        calls = []
        self.assertFalse(run_if_pending(0, calls.append))
        self.assertTrue(run_if_pending(1, calls.append))
        self.assertEqual(["refresh-index"], calls)

    def test_archive_uses_fake_delete(self):
        calls = []
        self.assertFalse(remove_if_expired(0, calls.append))
        self.assertTrue(remove_if_expired(1, calls.append))
        self.assertEqual(["old-report.txt"], calls)
