/**
 * Punto rojo en el menú lateral (Evaluación docente) cuando hay informes institucionales sin leer.
 * En evaluacion-docente.html el punto no se muestra (la página ya es el destino).
 */
(function () {
  'use strict';

  function currentPageFile() {
    var p = (window.location.pathname || '').replace(/\\/g, '/').split('/').pop() || '';
    return p;
  }

  function isEvalDocentePage() {
    return currentPageFile() === 'evaluacion-docente.html';
  }

  function setSidebarDot(visible) {
    var dot = document.querySelector('#navbarMenuPrincipal .ed-nav-badge-informe');
    if (!dot) return;
    dot.classList.toggle('d-none', !visible);
  }

  async function fetchSinLeerCount() {
    if (localStorage.getItem('userTipo') !== 'MAESTRO') return 0;
    if (typeof authFetch !== 'function') return 0;
    try {
      var r = await authFetch('/evaluaciones-docente/maestro/informes-academicos/resumen', { method: 'GET' });
      return r && r.sinLeer != null ? Number(r.sinLeer) : 0;
    } catch (_) {
      return 0;
    }
  }

  window.refreshEvalDocenteSidebarBadgeMaestro = async function () {
    var n = await fetchSinLeerCount();
    if (isEvalDocentePage()) {
      setSidebarDot(false);
    } else {
      setSidebarDot(n > 0);
    }
    return n;
  };

  document.addEventListener('maestroSidebarReady', function () {
    if (typeof window.refreshEvalDocenteSidebarBadgeMaestro === 'function') {
      window.refreshEvalDocenteSidebarBadgeMaestro();
    }
  });
})();
