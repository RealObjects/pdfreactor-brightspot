/*
 * PDF-preview problem banner listener (contributed to the Tool page head by
 * PdfReactorToolPageHead).
 *
 * Editor-facing messages belong in the Tool DOM, where the active theme
 * styles them -- the platform never renders Tool-styled content inside a
 * preview iframe. So PdfPreviewPage posts its problem report to the parent
 * window and this listener renders it as a themed banner inside the preview
 * pane's PreviewFrame-typeDisplay, sizing the PDF iframe below it via the
 * --PdfPreview-bannerHeight variable (consumed by preview-frame.css). A
 * ResizeObserver keeps that variable in sync as the banner reflows on pane
 * resize. The banner and observer clear when the preview loader inserts a
 * fresh iframe (refresh / type switch).
 */
(function () {
    'use strict';

    function clearBanner(display) {
        var existing = display.querySelector('.PdfPreview-problems');
        if (existing) {
            existing.remove();
        }
        if (display.pdfBannerSizeObserver) {
            display.pdfBannerSizeObserver.disconnect();
            display.pdfBannerSizeObserver = null;
        }
        // Disconnect any prior reload MutationObserver too: if a banner is
        // shown again before an iframe re-insertion fires the self-disconnect,
        // observers would otherwise accumulate on the display element.
        if (display.pdfReloadObserver) {
            display.pdfReloadObserver.disconnect();
            display.pdfReloadObserver = null;
        }
        display.style.removeProperty('--PdfPreview-bannerHeight');
    }

    function watchForReload(display) {
        var observer = new MutationObserver(function (mutations) {
            for (var i = 0; i < mutations.length; i++) {
                var added = mutations[i].addedNodes;
                for (var j = 0; j < added.length; j++) {
                    if (added[j].tagName === 'IFRAME') {
                        clearBanner(display);
                        observer.disconnect();
                        return;
                    }
                }
            }
        });
        display.pdfReloadObserver = observer;
        observer.observe(display, { childList: true });
    }

    function buildBanner(data) {
        var banner = document.createElement('div');
        // Both class generations: the v5 skin bridges legacy classes by
        // injecting `.message:not(.Message) { display: none }` rules, so the
        // element must carry the bridged classes (Message, is-warning/
        // is-error/is-info) alongside the legacy ones -- and those bridged
        // classes are what the theme styles. NOTE: the skin themes is-warning
        // and is-error but has NO is-info rule, so .PdfPreview-problems
        // .is-info is themed by the plugin's own preview-frame.css using the
        // --btu-theme-primary-* scale.
        var variant = data.severity === 'error'
            ? 'message-error is-error'
            : data.severity === 'info'
                ? 'message-info is-info'
                : 'message-warning is-warning';
        banner.className = 'PdfPreview-problems message Message ' + variant;
        var headline = document.createElement('strong');
        headline.textContent = data.headline || '';
        banner.appendChild(headline);
        if (data.details && data.details.length) {
            var list = document.createElement('ul');
            data.details.forEach(function (detail) {
                var item = document.createElement('li');
                item.textContent = detail;
                list.appendChild(item);
            });
            banner.appendChild(list);
        }
        if (data.remedy) {
            var remedy = document.createElement('p');
            remedy.textContent = data.remedy;
            banner.appendChild(remedy);
        }
        if (data.technical) {
            var details = document.createElement('details');
            var summary = document.createElement('summary');
            summary.textContent = data.technicalLabel || 'Technical details';
            var pre = document.createElement('pre');
            pre.textContent = data.technical;
            details.appendChild(summary);
            details.appendChild(pre);
            banner.appendChild(details);
        }
        if (data.log) {
            var logDetails = document.createElement('details');
            var logSummary = document.createElement('summary');
            logSummary.textContent = data.logLabel || 'Conversion log';
            var logPre = document.createElement('pre');
            logPre.textContent = data.log;
            logDetails.appendChild(logSummary);
            logDetails.appendChild(logPre);
            banner.appendChild(logDetails);
        }
        return banner;
    }

    function showBanner(display, data) {
        clearBanner(display);
        var banner = buildBanner(data);
        display.insertBefore(banner, display.firstChild);
        function syncHeight() {
            display.style.setProperty('--PdfPreview-bannerHeight', banner.offsetHeight + 'px');
        }
        syncHeight();
        // Keep the offset synced as the banner reflows on pane resize, so the
        // PDF viewer below it neither gaps nor overlaps it (the height is
        // content- and width-dependent, so a one-time measurement goes stale
        // the moment the pane is resized).
        if (typeof ResizeObserver === 'function') {
            var sizeObserver = new ResizeObserver(syncHeight);
            sizeObserver.observe(banner);
            display.pdfBannerSizeObserver = sizeObserver;
        }
        watchForReload(display);
    }

    window.addEventListener('message', function (event) {
        if (event.origin !== window.location.origin) {
            return;
        }
        var data = event.data;
        if (!data || data.type !== 'pdfreactor-preview-problems') {
            return;
        }
        var displays = document.querySelectorAll('.PreviewFrame-typeDisplay');
        for (var i = 0; i < displays.length; i++) {
            var display = displays[i];
            var frames = display.querySelectorAll('iframe');
            var ours = false;
            for (var j = 0; j < frames.length; j++) {
                if (frames[j].contentWindow === event.source) {
                    ours = true;
                    break;
                }
            }
            if (!ours) {
                continue;
            }
            showBanner(display, data);
        }
    });
}());
