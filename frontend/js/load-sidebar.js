/**
 * Carga el menú lateral desde components/sidebar.html, lo inserta en #sidebar-container
 * y marca el enlace activo según la página actual. Dispara 'sidebarLoaded' al terminar.
 */
(function () {
  'use strict';

  var container = document.getElementById('sidebar-container');
  if (!container) return;

  var src = container.getAttribute('data-sidebar-src') || '../components/sidebar.html';

  function setActiveLink() {
    var path = window.location.pathname || '';
    var currentFile = path.split('/').pop() || 'dashboard.html';
    if (currentFile === '' || currentFile === 'pages') currentFile = 'dashboard.html';
    var currentSearch = window.location.search || '';

    function getQueryParam(search, key) {
      try {
        var s = String(search || '');
        if (!s) return '';
        var p = new URLSearchParams(s);
        return p.get(key) || '';
      } catch (_) {
        return '';
      }
    }

    var currentV = getQueryParam(currentSearch, 'v');

    function normalizeHref(href) {
      href = String(href || '').trim();
      if (!href) return { file: '', v: '' };
      var last = href.split('/').pop() || '';
      var file = last.split('#')[0].split('?')[0];
      var v = '';
      try {
        var q = last.indexOf('?') !== -1 ? ('?' + last.split('?').slice(1).join('?')) : '';
        if (q) v = getQueryParam(q, 'v');
      } catch (_) {}
      return { file: file, v: v };
    }
    var nav = document.getElementById('navbarMenuPrincipal');
    if (!nav) return;
    nav.querySelectorAll('.nav-link-ide').forEach(function (a) {
      var href = a.getAttribute('href') || '';
      var nh = normalizeHref(href);
      var match = false;
      if (nh.file && nh.file === currentFile) {
        // Si el link incluye v=..., exigir coincidencia con el query actual.
        match = nh.v ? (String(nh.v) === String(currentV)) : true;
      }
      if (match) {
        a.classList.add('active');
      } else {
        a.classList.remove('active');
      }
    });

    // Eventos dedicados (si la página requiere lógica especial adicional).
    var type = nav.getAttribute('data-sidebar-type') || '';
    if (type === 'alumno') {
      document.dispatchEvent(new CustomEvent('alumnoSidebarReady'));
    } else if (type === 'maestro') {
      document.dispatchEvent(new CustomEvent('maestroSidebarReady'));
    }
  }

  function fillUserInSidebar() {
    var root = container;
    if (!root) return;
    var nombreEl = root.querySelector('#sidebarUserNombre');
    var nombre = localStorage.getItem('userNombreCompleto');
    if (nombreEl) {
      if (nombre && nombre.trim()) {
        nombreEl.textContent = typeof formatearNombreMostrar === 'function'
          ? formatearNombreMostrar(nombre.trim())
          : nombre.trim();
      } else {
        nombreEl.textContent = '—';
      }
    }
    var emailEl = root.querySelector('#userEmail');
    if (emailEl) emailEl.textContent = localStorage.getItem('userEmail') || '—';
    /* El rol activo va solo en la insignia superior (#sidebarRolSwitcherWrap); no repetir rol ni nombre abajo. */
  }

  fetch(src)
    .then(function (r) { return r.text(); })
    .then(function (html) {
      container.innerHTML = html;
      fillUserInSidebar();
      setActiveLink();
      if (typeof setupLogoutButtons === 'function') {
        setupLogoutButtons();
      }
      document.dispatchEvent(new CustomEvent('sidebarLoaded'));
    })
    .catch(function (err) {
      console.error('Error cargando menú lateral:', err);
      document.dispatchEvent(new CustomEvent('sidebarLoaded'));
    });
})();
