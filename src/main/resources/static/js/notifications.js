/**
 * Notification Manager - Handles SSE connection and notification display
 */
class NotificationManager {
    constructor() {
        this.eventSource = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 10;
        this.reconnectDelay = 5000;
        this.unreadCount = 0;
        this.notifications = [];

        // DOM elements
        this.badge = document.getElementById('notification-badge');
        this.dropdown = document.getElementById('notification-dropdown');
        this.toastContainer = document.getElementById('toast-container');

        // Bind methods
        this.connect = this.connect.bind(this);
        this.handleEvent = this.handleEvent.bind(this);
        this.handleError = this.handleError.bind(this);

        // Initialize
        this.init();
    }

    init() {
        // Connect to SSE stream
        this.connect();

        // Load existing notifications
        this.loadNotifications();

        // Set up dropdown handlers
        this.setupDropdownHandlers();

        // Set up polling fallback (in case SSE doesn't work)
        this.setupPollingFallback();
    }

    connect() {
        if (this.eventSource) {
            this.eventSource.close();
        }

        console.log('Connecting to notification stream...');
        this.eventSource = new EventSource('/api/notifications/stream');

        // Connection event
        this.eventSource.addEventListener('connected', (e) => {
            console.log('Connected to notification stream');
            this.reconnectAttempts = 0;
            const data = JSON.parse(e.data);
            this.updateBadge(data.unreadCount);
        });

        // Heartbeat event
        this.eventSource.addEventListener('heartbeat', (e) => {
            // Connection is alive, nothing to do
        });

        // Notification type events
        const notificationTypes = [
            'ONBOARDING_SUBMITTED', 'ONBOARDING_APPROVED', 'ONBOARDING_REJECTED',
            'CONNECTOR_ONLINE', 'CONNECTOR_OFFLINE', 'CONNECTOR_CREATED',
            'DATASET_PUBLISHED', 'DATASET_UPDATED', 'DATASET_DELETED',
            'MEMBER_INVITED', 'MEMBER_JOINED', 'MEMBER_REMOVED',
            'SPIP_SYNC_COMPLETE', 'SPIP_SYNC_FAILED',
            'COLLABORATION_REQUEST_RECEIVED', 'COLLABORATION_REQUEST_APPROVED', 'COLLABORATION_REQUEST_REJECTED',
            'COLLABORATION_CANCELLED',
            'SYSTEM_ANNOUNCEMENT', 'SYSTEM_MAINTENANCE'
        ];

        notificationTypes.forEach(type => {
            this.eventSource.addEventListener(type, this.handleEvent);
        });

        // Error handling
        this.eventSource.onerror = this.handleError;
    }

    handleEvent(event) {
        const notification = JSON.parse(event.data);
        console.log('Received notification:', notification);

        // Add to local list
        this.notifications.unshift(notification);

        // Update UI
        this.updateBadge(this.unreadCount + 1);
        this.addToDropdown(notification);
        this.showToast(notification);
    }

    handleError(error) {
        console.error('SSE connection error:', error);

        if (this.eventSource.readyState === EventSource.CLOSED) {
            // Connection closed, attempt to reconnect
            if (this.reconnectAttempts < this.maxReconnectAttempts) {
                this.reconnectAttempts++;
                const delay = this.reconnectDelay * Math.min(this.reconnectAttempts, 5);
                console.log(`Reconnecting in ${delay/1000}s (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
                setTimeout(this.connect, delay);
            } else {
                console.error('Max reconnection attempts reached');
            }
        }
    }

    async loadNotifications() {
        try {
            const response = await fetch('/api/notifications?limit=10');
            if (response.ok) {
                this.notifications = await response.json();
                this.renderDropdown();
            }
        } catch (error) {
            console.error('Failed to load notifications:', error);
        }
    }

    updateBadge(count) {
        this.unreadCount = count;
        if (this.badge) {
            if (count > 0) {
                this.badge.textContent = count > 99 ? '99+' : count;
                this.badge.classList.remove('d-none');
            } else {
                this.badge.classList.add('d-none');
            }
        }
    }

    renderDropdown() {
        if (!this.dropdown) return;

        if (this.notifications.length === 0) {
            this.dropdown.innerHTML = `
                <li class="dropdown-item text-muted text-center py-3">
                    <i class="fas fa-bell-slash me-2"></i>No notifications
                </li>
            `;
            return;
        }

        let html = '';
        this.notifications.slice(0, 5).forEach(notification => {
            html += this.renderNotificationItem(notification);
        });

        html += `
            <li><hr class="dropdown-divider"></li>
            <li class="d-flex justify-content-center gap-3 py-1">
                <a class="text-primary small" href="#" onclick="notificationManager.markAllAsRead(); return false;">
                    <i class="fas fa-check-double me-1"></i>Mark read
                </a>
                <a class="text-danger small" href="#" onclick="notificationManager.clearAll(); return false;">
                    <i class="fas fa-trash me-1"></i>Clear all
                </a>
            </li>
        `;

        this.dropdown.innerHTML = html;
    }

    renderNotificationItem(notification) {
        const alertClass = notification.alertClass || 'info';
        const iconMap = {
            'success': 'fa-check-circle text-success',
            'info': 'fa-info-circle text-info',
            'warning': 'fa-exclamation-triangle text-warning',
            'danger': 'fa-times-circle text-danger'
        };
        const icon = iconMap[alertClass] || iconMap['info'];
        const readClass = notification.read ? 'text-muted' : '';
        const timeAgo = this.formatTimeAgo(notification.timestamp);
        const actionUrl = notification.actionUrl || '#';
        const hasAction = !!notification.actionUrl;
        const actionLabel = notification.actionLabel || 'View';

        // Actionable notifications get an explicit primary action (e.g. "Review request") plus a
        // "Dismiss" that marks it read without navigating. The title/message is also clickable so
        // clicking the notification still takes the user straight to where the task is handled.
        const actions = `
            <div class="d-flex gap-3 mt-1">
                ${hasAction ? `<a class="small text-primary fw-semibold" href="${actionUrl}"
                       onclick="notificationManager.markAsRead(${notification.id})">${this.escapeHtml(actionLabel)}</a>` : ''}
                ${!notification.read ? `<a class="small text-muted" href="#"
                       onclick="notificationManager.dismissNotification(${notification.id}); return false;">Dismiss</a>` : ''}
            </div>
        `;

        return `
            <li>
                <div class="dropdown-item-text px-3 ${readClass}">
                    <div class="d-flex align-items-start">
                        <i class="fas ${icon} me-2 mt-1"></i>
                        <div class="flex-grow-1">
                            <a class="text-decoration-none ${readClass || 'text-body'}" href="${actionUrl}"
                               onclick="notificationManager.markAsRead(${notification.id})">
                                <div class="fw-semibold">${this.escapeHtml(notification.title)}</div>
                                <small class="text-muted d-block">${this.escapeHtml(notification.message || '')}</small>
                            </a>
                            <div class="small text-muted mt-1">${timeAgo}</div>
                            ${actions}
                        </div>
                    </div>
                </div>
            </li>
        `;
    }

    addToDropdown(notification) {
        if (!this.dropdown) return;

        // Remove "no notifications" message if present
        const emptyMessage = this.dropdown.querySelector('.text-muted.text-center');
        if (emptyMessage) {
            emptyMessage.closest('li').remove();
        }

        // Add new notification at the top
        const li = document.createElement('li');
        li.innerHTML = this.renderNotificationItem(notification).trim();
        const firstChild = li.firstElementChild;

        const firstItem = this.dropdown.firstElementChild;
        if (firstItem) {
            this.dropdown.insertBefore(firstChild.parentElement, firstItem);
        } else {
            this.dropdown.appendChild(firstChild.parentElement);
        }

        // Limit to 5 items (plus divider and mark all read link)
        const items = this.dropdown.querySelectorAll('li');
        if (items.length > 7) {
            items[5].remove();
        }
    }

    showToast(notification) {
        if (!this.toastContainer) return;

        const alertClass = notification.alertClass || 'info';
        const bgClass = {
            'success': 'bg-success',
            'info': 'bg-info',
            'warning': 'bg-warning',
            'danger': 'bg-danger'
        }[alertClass] || 'bg-info';

        const toastId = 'toast-' + Date.now();
        const toastHtml = `
            <div id="${toastId}" class="toast" role="alert" aria-live="assertive" aria-atomic="true" data-bs-delay="8000">
                <div class="toast-header ${bgClass} text-white">
                    <i class="fas fa-bell me-2"></i>
                    <strong class="me-auto">${this.escapeHtml(notification.title)}</strong>
                    <small>Just now</small>
                    <button type="button" class="btn-close btn-close-white" data-bs-dismiss="toast" aria-label="Close"></button>
                </div>
                <div class="toast-body">
                    ${this.escapeHtml(notification.message || '')}
                    ${notification.actionUrl ? `
                        <div class="mt-2">
                            <a href="${notification.actionUrl}" class="btn btn-sm btn-outline-primary">
                                ${notification.actionLabel || 'View'}
                            </a>
                        </div>
                    ` : ''}
                </div>
            </div>
        `;

        this.toastContainer.insertAdjacentHTML('beforeend', toastHtml);

        const toastElement = document.getElementById(toastId);
        const toast = new bootstrap.Toast(toastElement);
        toast.show();

        // Remove from DOM after hidden
        toastElement.addEventListener('hidden.bs.toast', () => {
            toastElement.remove();
        });
    }

    async markAsRead(notificationId) {
        try {
            const response = await fetch(`/api/notifications/${notificationId}/read`, {
                method: 'POST'
            });
            if (response.ok) {
                const data = await response.json();
                this.updateBadge(data.unreadCount);

                // Update local state
                const notification = this.notifications.find(n => n.id === notificationId);
                if (notification) {
                    notification.read = true;
                }
            }
        } catch (error) {
            console.error('Failed to mark notification as read:', error);
        }
    }

    async dismissNotification(notificationId) {
        await this.markAsRead(notificationId);
        // Reflect the read state in the dropdown (greys the item, drops the Dismiss link).
        this.renderDropdown();
    }

    async markAllAsRead() {
        try {
            const response = await fetch('/api/notifications/read-all', {
                method: 'POST'
            });
            if (response.ok) {
                this.updateBadge(0);
                this.notifications.forEach(n => n.read = true);
                this.renderDropdown();
            }
        } catch (error) {
            console.error('Failed to mark all notifications as read:', error);
        }
    }

    async clearAll() {
        try {
            const response = await fetch('/api/notifications/all', {
                method: 'DELETE'
            });
            if (response.ok) {
                this.notifications = [];
                this.updateBadge(0);
                this.renderDropdown();
            }
        } catch (error) {
            console.error('Failed to clear notifications:', error);
        }
    }

    setupDropdownHandlers() {
        // Refresh notifications when dropdown is opened
        const dropdownToggle = document.querySelector('[data-bs-toggle="dropdown"]');
        if (dropdownToggle) {
            dropdownToggle.addEventListener('show.bs.dropdown', () => {
                this.loadNotifications();
            });
        }
    }

    setupPollingFallback() {
        // If SSE connection fails repeatedly, fall back to polling
        setTimeout(() => {
            if (!this.eventSource || this.eventSource.readyState !== EventSource.OPEN) {
                console.warn('SSE connection not established, using polling fallback');
                this.startPolling();
            }
        }, 10000); // Wait 10 seconds before enabling fallback
    }

    startPolling() {
        // Poll for unread count every 30 seconds
        this.pollingInterval = setInterval(async () => {
            try {
                const response = await fetch('/api/notifications/unread/count');
                if (response.ok) {
                    const data = await response.json();
                    this.updateBadge(data.count);
                }
            } catch (error) {
                console.error('Polling failed:', error);
            }
        }, 30000); // Poll every 30 seconds
    }

    formatTimeAgo(timestamp) {
        if (!timestamp) return '';

        const date = new Date(timestamp);
        const now = new Date();
        const seconds = Math.floor((now - date) / 1000);

        if (seconds < 60) return 'Just now';
        if (seconds < 3600) return Math.floor(seconds / 60) + 'm ago';
        if (seconds < 86400) return Math.floor(seconds / 3600) + 'h ago';
        if (seconds < 604800) return Math.floor(seconds / 86400) + 'd ago';

        return date.toLocaleDateString();
    }

    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    disconnect() {
        if (this.eventSource) {
            this.eventSource.close();
            this.eventSource = null;
        }
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
            this.pollingInterval = null;
        }
    }
}

// Initialize notification manager when DOM is ready
let notificationManager;
document.addEventListener('DOMContentLoaded', function() {
    // Only initialize if user is authenticated (notification elements exist)
    if (document.getElementById('notification-bell')) {
        notificationManager = new NotificationManager();
    }
});

// Clean up on page unload
window.addEventListener('beforeunload', function() {
    if (notificationManager) {
        notificationManager.disconnect();
    }
});
