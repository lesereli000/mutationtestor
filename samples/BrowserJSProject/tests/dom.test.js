/**
 * @jest-environment jsdom
 */
const {
    createProgressBar,
    toggleVisibility,
    createStripedList,
    validateForm,
    calculateGridLayout,
    handleDelegatedClick
} = require('../src/dom');

describe('DOM Progress Bar', () => {

    beforeEach(() => {
        document.body.innerHTML = '<div id="container"></div>';
    });

    test('createProgressBar creates element with correct width', () => {
        const bar = createProgressBar('container', 75);
        expect(bar).not.toBeNull();
        expect(bar.style.width).toBe('75%');
        expect(bar.textContent).toBe('75%');
    });

    test('createProgressBar clamps above 100', () => {
        const bar = createProgressBar('container', 150);
        expect(bar.style.width).toBe('100%');
    });

    test('createProgressBar clamps below 0', () => {
        const bar = createProgressBar('container', -10);
        expect(bar.style.width).toBe('0%');
    });

    test('createProgressBar returns null for missing container', () => {
        expect(createProgressBar('nonexistent', 50)).toBeNull();
    });

    test('createProgressBar uses green for >= 70', () => {
        const bar = createProgressBar('container', 70);
        expect(bar.style.backgroundColor).toBe('rgb(76, 175, 80)');
    });

    test('createProgressBar uses orange for >= 40', () => {
        const bar = createProgressBar('container', 50);
        expect(bar.style.backgroundColor).toBe('rgb(255, 152, 0)');
    });

    test('createProgressBar uses red for < 40', () => {
        const bar = createProgressBar('container', 20);
        expect(bar.style.backgroundColor).toBe('rgb(244, 67, 54)');
    });
});

describe('DOM Toggle Visibility', () => {

    beforeEach(() => {
        document.body.innerHTML = '<div id="target" style="display: block;"></div>';
    });

    test('toggleVisibility hides visible element', () => {
        const result = toggleVisibility('target');
        expect(result).toBe(false);
        expect(document.getElementById('target').style.display).toBe('none');
    });

    test('toggleVisibility shows hidden element', () => {
        document.getElementById('target').style.display = 'none';
        const result = toggleVisibility('target');
        expect(result).toBe(true);
        expect(document.getElementById('target').style.display).toBe('block');
    });

    test('toggleVisibility returns false for missing element', () => {
        expect(toggleVisibility('nonexistent')).toBe(false);
    });
});

describe('DOM Striped List', () => {

    beforeEach(() => {
        document.body.innerHTML = '<div id="list"></div>';
    });

    test('createStripedList creates correct number of items', () => {
        const count = createStripedList('list', ['a', 'b', 'c']);
        expect(count).toBe(3);
    });

    test('createStripedList returns 0 for missing container', () => {
        expect(createStripedList('nonexistent', ['a'])).toBe(0);
    });

    test('createStripedList applies alternating backgrounds', () => {
        createStripedList('list', ['a', 'b']);
        const items = document.querySelectorAll('li');
        expect(items[0].style.backgroundColor).toBe('rgb(245, 245, 245)');
        expect(items[1].style.backgroundColor).toBe('rgb(255, 255, 255)');
    });
});

describe('Form Validation', () => {

    test('validateForm accepts valid data', () => {
        const result = validateForm({
            name: 'Alice',
            email: 'alice@example.com',
            age: 25,
            password: 'securepass'
        });
        expect(result.isValid).toBe(true);
        expect(result.errors).toHaveLength(0);
    });

    test('validateForm rejects short name', () => {
        const result = validateForm({ name: 'A', email: 'a@b.com', age: 20 });
        expect(result.isValid).toBe(false);
        expect(result.errors).toContain('Name must be at least 2 characters');
    });

    test('validateForm rejects missing email @', () => {
        const result = validateForm({ name: 'Alice', email: 'invalid', age: 20 });
        expect(result.isValid).toBe(false);
    });

    test('validateForm rejects out of range age', () => {
        const result = validateForm({ name: 'Alice', email: 'a@b.com', age: -1 });
        expect(result.isValid).toBe(false);

        const result2 = validateForm({ name: 'Alice', email: 'a@b.com', age: 200 });
        expect(result2.isValid).toBe(false);
    });

    test('validateForm reports field count', () => {
        const result = validateForm({ name: 'Al', email: 'a@b.com', age: 25 });
        expect(result.fieldCount).toBe(3);
    });
});

describe('Grid Layout Calculation', () => {

    test('calculateGridLayout returns 4 columns for wide screen', () => {
        const layout = calculateGridLayout(1400, 12, 10);
        expect(layout.columns).toBe(4);
        expect(layout.rows).toBe(3);
    });

    test('calculateGridLayout returns 3 columns for medium screen', () => {
        const layout = calculateGridLayout(900, 9, 10);
        expect(layout.columns).toBe(3);
    });

    test('calculateGridLayout returns 2 columns for small screen', () => {
        const layout = calculateGridLayout(500, 4, 10);
        expect(layout.columns).toBe(2);
    });

    test('calculateGridLayout returns 1 column for narrow screen', () => {
        const layout = calculateGridLayout(400, 2, 10);
        expect(layout.columns).toBe(1);
    });

    test('calculateGridLayout at boundary 1200', () => {
        // Catches >= → > mutation
        const layout = calculateGridLayout(1200, 4, 0);
        expect(layout.columns).toBe(4);
    });

    test('calculateGridLayout computes item width', () => {
        // 1200 wide, 4 columns, 10px gap → (1200 - 30) / 4 = 292.5 → 292
        const layout = calculateGridLayout(1200, 4, 10);
        expect(layout.itemWidth).toBe(292);
    });
});

describe('Event Delegation', () => {

    test('handleDelegatedClick increments counter', () => {
        const config = { count: 0, buttonClass: 'btn' };
        const event = {
            target: {
                tagName: 'BUTTON',
                classList: { contains: (cls) => cls === 'btn' },
                dataset: { action: 'increment' }
            }
        };
        expect(handleDelegatedClick(event, config)).toBe(1);
        expect(config.count).toBe(1);
    });

    test('handleDelegatedClick decrements counter', () => {
        const config = { count: 5, buttonClass: 'btn' };
        const event = {
            target: {
                tagName: 'BUTTON',
                classList: { contains: (cls) => cls === 'btn' },
                dataset: { action: 'decrement' }
            }
        };
        expect(handleDelegatedClick(event, config)).toBe(4);
    });

    test('handleDelegatedClick resets counter', () => {
        const config = { count: 10, buttonClass: 'btn' };
        const event = {
            target: {
                tagName: 'BUTTON',
                classList: { contains: (cls) => cls === 'btn' },
                dataset: { action: 'reset' }
            }
        };
        expect(handleDelegatedClick(event, config)).toBe(0);
    });

    test('handleDelegatedClick ignores non-button clicks', () => {
        const config = { count: 0, buttonClass: 'btn' };
        const event = {
            target: {
                tagName: 'DIV',
                classList: { contains: () => false },
                dataset: {}
            }
        };
        expect(handleDelegatedClick(event, config)).toBeNull();
    });
});
