// ============================================================
// Browser DOM Manipulation functions
// Showcases browser-specific JavaScript with mutation targets
// Uses arithmetic, comparison, logical, and ternary operators
// in realistic DOM interaction scenarios
// ============================================================

/**
 * Creates an element with computed styles.
 * AOR targets: +, *
 * ROR target: >
 */
function createProgressBar(containerId, percentage) {
    let clamped = percentage;
    if (percentage > 100) {
        clamped = 100;
    }
    if (percentage < 0) {
        clamped = 0;
    }

    let container = document.getElementById(containerId);
    if (!container) {
        return null;
    }

    let bar = document.createElement('div');
    bar.className = 'progress-bar';
    bar.style.width = clamped + '%';
    bar.style.height = '20px';
    bar.style.backgroundColor = clamped >= 70 ? '#4caf50' : (clamped >= 40 ? '#ff9800' : '#f44336');
    bar.textContent = clamped + '%';

    container.appendChild(bar);
    return bar;
}

/**
 * Toggle visibility of an element.
 * EQ_NEGATE target: ===
 * Logical target: !
 */
function toggleVisibility(elementId) {
    let element = document.getElementById(elementId);
    if (!element) {
        return false;
    }

    if (element.style.display === 'none') {
        element.style.display = 'block';
        return true;
    } else {
        element.style.display = 'none';
        return false;
    }
}

/**
 * Creates a list of items with index-based styling.
 * AOR targets: +, %
 * ROR target: <
 * EQ_NEGATE target: ===
 */
function createStripedList(containerId, items) {
    let container = document.getElementById(containerId);
    if (!container) {
        return 0;
    }

    let list = document.createElement('ul');
    let count = 0;

    for (let i = 0; i < items.length; i++) {
        let li = document.createElement('li');
        li.textContent = items[i];
        li.style.backgroundColor = i % 2 === 0 ? '#f5f5f5' : '#ffffff';
        li.style.padding = 8 + 4 + 'px';
        list.appendChild(li);
        count++;
    }

    container.appendChild(list);
    return count;
}

/**
 * Validates and submits a form.
 * Logical targets: &&, ||
 * ROR targets: <, >=
 * EQ_NEGATE target: ===
 */
function validateForm(formData) {
    let errors = [];

    if (!formData.name || formData.name.length < 2) {
        errors.push('Name must be at least 2 characters');
    }

    if (!formData.email || !formData.email.includes('@')) {
        errors.push('Valid email is required');
    }

    if (formData.age === undefined || formData.age < 0 || formData.age > 150) {
        errors.push('Age must be between 0 and 150');
    }

    if (formData.password && formData.password.length >= 8 && formData.password !== formData.name) {
        // password is valid
    } else if (formData.password !== undefined) {
        errors.push('Password must be at least 8 characters and different from name');
    }

    return {
        isValid: errors.length === 0,
        errors: errors,
        fieldCount: Object.keys(formData).length
    };
}

/**
 * Calculates a responsive grid layout.
 * AOR targets: /, -, *
 * ROR target: >=
 */
function calculateGridLayout(containerWidth, itemCount, gap) {
    let columns;
    if (containerWidth >= 1200) {
        columns = 4;
    } else if (containerWidth >= 800) {
        columns = 3;
    } else if (containerWidth >= 480) {
        columns = 2;
    } else {
        columns = 1;
    }

    let totalGapWidth = (columns - 1) * gap;
    let itemWidth = (containerWidth - totalGapWidth) / columns;
    let rows = Math.ceil(itemCount / columns);
    let totalHeight = rows * itemWidth + (rows - 1) * gap;

    return {
        columns,
        rows,
        itemWidth: Math.floor(itemWidth),
        totalHeight: Math.ceil(totalHeight)
    };
}

/**
 * Event delegation handler for browser click events.
 * Logical target: &&
 * EQ_NEGATE target: ===
 */
function handleDelegatedClick(event, config) {
    let target = event.target;

    if (target.tagName === 'BUTTON' && target.classList.contains(config.buttonClass)) {
        let action = target.dataset.action;

        if (action === 'increment') {
            config.count++;
            return config.count;
        } else if (action === 'decrement') {
            config.count--;
            return config.count;
        } else if (action === 'reset') {
            config.count = 0;
            return 0;
        }
    }

    return null;
}

module.exports = {
    createProgressBar,
    toggleVisibility,
    createStripedList,
    validateForm,
    calculateGridLayout,
    handleDelegatedClick
};
