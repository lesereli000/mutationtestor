"""Small inventory export example used for safety-aware mutation testing."""


def export_if_available(item_count, output_path):
    """Write a report only when inventory is available."""
    if item_count > 0:
        with open(output_path, "w", encoding="utf-8") as report:
            report.write("Inventory is available\n")
        return True
    return False
