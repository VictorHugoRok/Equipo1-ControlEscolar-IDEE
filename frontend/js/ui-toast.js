/**
 * ui-toast.js
 * Toast/barra delgada estilo sistema (no alert del navegador).
 * - Autocierra por defecto
 * - Botón X para cerrar
 */
(function () {
  'use strict';

  function ensureContainer() {
    var id = 'systemToastContainer';
    var el = document.getElementById(id);
    if (el) return el;
    el = document.createElement('div');
    el.id = id;
    el.className = 'system-toast-container';
    document.body.appendChild(el);
    return el;
  }

  function iconFor(type) {
    if (type === 'success') return 'bi-check-circle';
    if (type === 'error' || type === 'danger') return 'bi-exclamation-triangle';
    if (type === 'warning') return 'bi-exclamation-circle';
    return 'bi-info-circle';
  }

  function normalizeType(type) {
    if (!type) return 'info';
    var t = String(type).toLowerCase();
    if (t === 'danger') t = 'error';
    if (!['success', 'error', 'warning', 'info'].includes(t)) t = 'info';
    return t;
  }

  /**
   * Muestra una barra delgada (toast) con autocierre.
   * @param {string} message
   * @param {{type?: 'success'|'error'|'warning'|'info', durationMs?: number}} opts
   */
  function showSystemToast(message, opts) {
    opts = opts || {};
    var type = normalizeType(opts.type);
    var durationMs = typeof opts.durationMs === 'number' ? opts.durationMs : 4200;

    var container = ensureContainer();
    var toast = document.createElement('div');
    toast.className = 'system-toast system-toast--' + type;
    toast.setAttribute('role', 'status');
    toast.setAttribute('aria-live', 'polite');

    var msg = document.createElement('div');
    msg.className = 'system-toast__msg';
    msg.innerHTML =
      '<i class="bi ' + iconFor(type) + ' system-toast__icon" aria-hidden="true"></i>' +
      '<span class="system-toast__text"></span>';
    msg.querySelector('.system-toast__text').textContent = String(message || '');

    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'system-toast__close';
    btn.setAttribute('aria-label', 'Cerrar notificación');
    btn.innerHTML = '&times;';

    var closed = false;
    function close() {
      if (closed) return;
      closed = true;
      toast.classList.add('is-hiding');
      window.setTimeout(function () {
        try { toast.remove(); } catch (_) {}
      }, 180);
    }

    btn.addEventListener('click', close);
    toast.addEventListener('click', function (e) {
      // si dan click en la barra (no en links), cerrar
      if (e && e.target && e.target.tagName && String(e.target.tagName).toLowerCase() === 'a') return;
      if (e && e.target === btn) return;
      close();
    });

    toast.appendChild(msg);
    toast.appendChild(btn);
    container.appendChild(toast);

    // animación de entrada
    window.setTimeout(function () { toast.classList.add('is-show'); }, 10);

    if (durationMs > 0) {
      window.setTimeout(close, durationMs);
    }
  }

  window.showSystemToast = showSystemToast;
})();

