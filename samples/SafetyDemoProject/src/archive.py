"""Archive cleanup example with an injected file-removal function."""


def remove_if_expired(age, unlink):
    if age > 0:
        unlink("old-report.txt")
        return True
    return False
