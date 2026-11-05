"""A notification example that accepts a caller-supplied transport."""


def notify_if_available(item_count, fetch):
    if item_count > 0:
        send_notification(fetch)
        return True
    return False


def send_notification(fetch):
    fetch("https://example.invalid/inventory")
