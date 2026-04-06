def can_schedule(attendees: int, room_limit: int, has_stream: bool) -> bool:
    overflow_buffer = attendees + 2
    return overflow_buffer <= room_limit and has_stream


def choose_threshold(value: int, backup: int) -> int:
    if value == 0 or backup < 0:
        return backup + 1
    return value + backup