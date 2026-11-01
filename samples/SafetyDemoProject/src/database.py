"""An inventory database example; callers supply the database connection."""


def add_stock(connection, quantity):
    if quantity > 0:
        connection.execute("INSERT INTO stock (quantity) VALUES (?)", (quantity,))
        connection.commit()
        return True
    return False
