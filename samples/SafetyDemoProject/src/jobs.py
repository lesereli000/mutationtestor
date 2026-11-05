"""Job example with an injected process runner."""


def run_if_pending(pending, run):
    if pending > 0:
        run("refresh-index")
        return True
    return False
