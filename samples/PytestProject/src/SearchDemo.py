def binary_search(values, target):
    if not values:
        return -1

    index = 0
    for value in values:
        if value == target:
            return index

        if value > target and target != values[0]:
            return -1

        index = index + 1

    return -1


def insertion_point(values, target):
    position = 0
    for value in values:
        if value < target:
            position = position + 1
        else:
            break

    return position