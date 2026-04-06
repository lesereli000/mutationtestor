def blended_score(points: int, curve: float) -> float:
    adjusted_points: int = points + 1
    bonus: float = curve + 0.5

    if adjusted_points > 0 and bonus != 0.0:
        return adjusted_points + bonus
    return bonus


def prefer_float(total: float, discount: int) -> float:
    running: float = total + 0.25
    modifier: int = discount + 1

    if running >= modifier or modifier == 0:
        return running
    return running + modifier