/*
 * "Convert again" (regenerate) for the PDFreactor edit-form widget.
 *
 * The regenerate control is intentionally NOT a navigable link: a plain widget
 * anchor is intercepted by the Tool's edit-form frame JS, which AJAX-loads the
 * href into the enclosing widget frame — and the regenerate endpoint's response
 * then destroys the right-rail layout. Instead the control carries
 * data-pdf-regenerate-url and this delegated handler POSTs it (state-changing,
 * so POST — SameSite cookies are not sent on a cross-site POST, closing the
 * CSRF-via-GET vector) and reloads the edit page in place on success.
 */
(function () {
    'use strict';

    // Brightspot validates a state-changing POST against the bsp.csrf cookie
    // via the Brightspot-CSRF header (AuthenticationRequest); without it the
    // request is 403. The cookie is readable here (not HttpOnly) and the header
    // can only be set same-origin, so this is a genuine CSRF defense.
    function csrfToken() {
        var match = document.cookie.match(/(?:^|;\s*)bsp\.csrf=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    function showError(control, message) {
        var host = control.parentNode || control;
        var note = host.querySelector('.PdfWidget-regenerateError');
        if (!note) {
            note = document.createElement('div');
            note.className = 'PdfWidget-regenerateError';
            host.appendChild(note);
        }
        note.textContent = message && message.length > 300
            ? message.slice(0, 300) + '…'
            : (message || 'Conversion failed.');
    }

    document.addEventListener('click', function (event) {
        var control = event.target.closest
            ? event.target.closest('[data-pdf-regenerate-url]')
            : null;
        if (!control) {
            return;
        }
        event.preventDefault();
        if (control.getAttribute('data-pdf-regenerating') === 'true') {
            return;
        }
        var url = control.getAttribute('data-pdf-regenerate-url');
        if (!url) {
            return;
        }

        control.setAttribute('data-pdf-regenerating', 'true');
        var label = control.textContent;
        control.textContent = control.getAttribute('data-pdf-regenerating-label') || 'Converting…';

        fetch(url, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'X-Requested-With': 'XMLHttpRequest',
                'Brightspot-CSRF': csrfToken()
            }
        }).then(function (response) {
            if (response.ok) {
                // Reload so the Stored-PDF line and the center read-only fields
                // reflect the fresh conversion, in place — no new tab.
                window.location.reload();
                return null;
            }
            return response.text().then(function (text) {
                control.removeAttribute('data-pdf-regenerating');
                control.textContent = label;
                showError(control, text);
            });
        }).catch(function () {
            control.removeAttribute('data-pdf-regenerating');
            control.textContent = label;
            showError(control, 'Conversion failed.');
        });
    });
}());
