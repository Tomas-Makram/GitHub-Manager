(function(global) {
    'use strict';

    function getContainer() {
        let container = document.getElementById('toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toast-container';
            document.body.appendChild(container);
        }
        return container;
    }

    const ICONS = {
        success: '✅',
        error: '❌',
        warning: '⚠️',
        info: 'ℹ️'
    };

    const Toast = {
        show: function(message, type = 'info', duration = 4000) {
            if (!message) return;
            const container = getContainer();
            const toast = document.createElement('div');
            toast.className = 'toast-item toast-' + (type || 'info');

            const icon = document.createElement('span');
            icon.className = 'toast-icon';
            icon.textContent = ICONS[type] || 'ℹ️';

            const msg = document.createElement('div');
            msg.className = 'toast-message';
            msg.textContent = message;

            const closeBtn = document.createElement('button');
            closeBtn.className = 'toast-close';
            closeBtn.innerHTML = '&times;';
            closeBtn.setAttribute('aria-label', 'Close');
            closeBtn.onclick = function() {
                dismiss();
            };

            toast.appendChild(icon);
            toast.appendChild(msg);
            toast.appendChild(closeBtn);
            container.appendChild(toast);

            let timer = null;
            function dismiss() {
                if (timer) clearTimeout(timer);
                toast.classList.add('hide');
                setTimeout(() => {
                    if (toast.parentNode) toast.parentNode.removeChild(toast);
                }, 300);
            }

            if (duration > 0) {
                timer = setTimeout(dismiss, duration);
            }

            return { dismiss: dismiss };
        },
        success: function(msg, dur) { return this.show(msg, 'success', dur || 4000); },
        error: function(msg, dur) { return this.show(msg, 'error', dur || 6000); },
        warning: function(msg, dur) { return this.show(msg, 'warning', dur || 5000); },
        info: function(msg, dur) { return this.show(msg, 'info', dur || 4000); }
    };

    function showConfirm(message, title) {
        return new Promise(function(resolve) {
            const overlay = document.createElement('div');
            overlay.className = 'floating-modal-overlay';

            const box = document.createElement('div');
            box.className = 'floating-modal-box';

            const isAr = (document.documentElement.lang === 'ar' || document.documentElement.dir === 'rtl');

            const h4 = document.createElement('div');
            h4.className = 'floating-modal-title';
            h4.textContent = title || (isAr ? 'تأكيد العملية' : 'Confirm Action');
            box.appendChild(h4);

            const p = document.createElement('div');
            p.className = 'floating-modal-msg';
            p.textContent = message;
            box.appendChild(p);

            const actions = document.createElement('div');
            actions.className = 'floating-modal-actions';

            const cancelBtn = document.createElement('button');
            cancelBtn.type = 'button';
            cancelBtn.className = 'btn btn-outline';
            cancelBtn.textContent = isAr ? 'إلغاء' : 'Cancel';

            const okBtn = document.createElement('button');
            okBtn.type = 'button';
            okBtn.className = 'btn btn-primary';
            okBtn.textContent = isAr ? 'موافق' : 'Confirm';

            actions.appendChild(cancelBtn);
            actions.appendChild(okBtn);
            box.appendChild(actions);
            overlay.appendChild(box);
            document.body.appendChild(overlay);

            // Auto-focus OK button
            setTimeout(() => { okBtn.focus(); }, 50);

            function cleanup(result) {
                if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
                resolve(result);
            }

            cancelBtn.onclick = function() { cleanup(false); };
            okBtn.onclick = function() { cleanup(true); };
            overlay.onclick = function(e) {
                if (e.target === overlay) cleanup(false);
            };
            document.addEventListener('keydown', function onEsc(e) {
                if (e.key === 'Escape') {
                    document.removeEventListener('keydown', onEsc);
                    cleanup(false);
                }
            });
        });
    }

    // استبدال دالة alert الأصلية للمتصفح تلقائياً لمنع ظهور "localhost says"
    global.alert = function(msg) {
        Toast.error(msg, 6000);
    };

    global.Toast = Toast;
    global.showConfirm = showConfirm;
})(window);
