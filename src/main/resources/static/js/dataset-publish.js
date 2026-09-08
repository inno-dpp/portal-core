/**
 * Dataset Publish Form - Dependent Dropdowns
 * Filters phase_activity options based on selected value_chain_phase
 */

document.addEventListener('DOMContentLoaded', function() {
    const phaseSelect = document.getElementById('valueChainPhase');
    const activitySelect = document.getElementById('phaseActivity');

    if (!phaseSelect || !activitySelect) {
        return; // Exit if elements not found
    }

    // Store all activity options with their phase data
    const allActivities = [];
    Array.from(activitySelect.options).forEach(option => {
        if (option.value) {
            allActivities.push({
                value: option.value,
                text: option.text,
                phase: option.dataset.phase,
                element: option.cloneNode(true)
            });
        }
    });

    // Function to filter activities by phase
    function filterActivities(selectedPhase) {
        // Store current selected value if any
        const currentValue = activitySelect.value;

        // Clear current options (except placeholder)
        activitySelect.innerHTML = '';

        // Add placeholder option
        const placeholderOption = document.createElement('option');
        placeholderOption.value = '';
        if (!selectedPhase) {
            placeholderOption.text = 'Select activity (choose phase first)';
        } else {
            placeholderOption.text = 'Select activity';
        }
        activitySelect.appendChild(placeholderOption);

        if (!selectedPhase) {
            return;
        }

        // Add matching activities
        let matchFound = false;
        allActivities.forEach(activity => {
            if (activity.phase === selectedPhase) {
                const option = activity.element.cloneNode(true);
                activitySelect.appendChild(option);
                if (option.value === currentValue) {
                    option.selected = true;
                    matchFound = true;
                }
            }
        });

        // If previous selection doesn't match new phase, reset
        if (!matchFound) {
            activitySelect.value = '';
        }
    }

    // Event listener for phase change
    phaseSelect.addEventListener('change', function() {
        const selectedPhase = this.value;
        filterActivities(selectedPhase);
    });

    // Initialize: filter based on current selection (for form resubmission with errors)
    if (phaseSelect.value) {
        filterActivities(phaseSelect.value);
    } else {
        // Initial state: show placeholder
        filterActivities('');
    }

    // Form validation before submit
    const form = document.getElementById('datasetPublishForm');
    if (form) {
        form.addEventListener('submit', function(e) {
            const title = document.querySelector('[name="title"]');
            const phase = phaseSelect.value;
            const activity = activitySelect.value;
            const material = document.querySelector('[name="materialCategory"]');

            let isValid = true;
            let errorMessages = [];

            // Validate required fields
            if (title && !title.value.trim()) {
                errorMessages.push('Title is required');
                title.classList.add('is-invalid');
                isValid = false;
            } else if (title) {
                title.classList.remove('is-invalid');
            }

            if (!phase) {
                errorMessages.push('Value Chain Phase is required');
                phaseSelect.classList.add('is-invalid');
                isValid = false;
            } else {
                phaseSelect.classList.remove('is-invalid');
            }

            if (!activity) {
                errorMessages.push('Phase Activity is required');
                activitySelect.classList.add('is-invalid');
                isValid = false;
            } else {
                activitySelect.classList.remove('is-invalid');
            }

            if (material && !material.value) {
                errorMessages.push('Material Category is required');
                material.classList.add('is-invalid');
                isValid = false;
            } else if (material) {
                material.classList.remove('is-invalid');
            }

            if (!isValid) {
                e.preventDefault();
                // Scroll to first error
                const firstInvalid = document.querySelector('.is-invalid');
                if (firstInvalid) {
                    firstInvalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    firstInvalid.focus();
                }
            }
        });
    }
});
