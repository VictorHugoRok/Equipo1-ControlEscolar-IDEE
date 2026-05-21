/**
 * Sidebar: redimensionable, colapsable y comportamiento en móvil/tablet.
 */
(function () {
  'use strict';

  var STORAGE_WIDTH = 'idee-sidebar-width';
  var STORAGE_COLLAPSED = 'idee-sidebar-collapsed';
  var DEFAULT_WIDTH = 210;
  var MIN_WIDTH = 200;
  var MAX_WIDTH = 320;
  var WIDTH_COLLAPSED = 64;

  var sidebar = document.querySelector('.sidebar-ide');
  var main = document.querySelector('.dashboard-main');
  var resizeHandle = document.getElementById('sidebarResizeHandle');
  var toggleBtn = document.getElementById('sidebarToggle');
  var overlay = document.getElementById('sidebarOverlay');
  var mobileToggle = document.getElementById('sidebarMobileToggle');

  function getStoredWidth() {
    var w = localStorage.getItem(STORAGE_WIDTH);
    if (w === null) return DEFAULT_WIDTH;
    w = parseInt(w, 10);
    return isNaN(w) ? DEFAULT_WIDTH : Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, w));
  }

  function getStoredCollapsed() {
    return localStorage.getItem(STORAGE_COLLAPSED) === 'true';
  }

  function applyWidth(px) {
    if (!sidebar || !main) return;
    px = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, px));
    document.documentElement.style.setProperty('--sidebar-width', px + 'px');
    sidebar.style.width = px + 'px';
    sidebar.classList.remove('sidebar-ide--collapsed');
    localStorage.setItem(STORAGE_WIDTH, String(px));
    localStorage.setItem(STORAGE_COLLAPSED, 'false');
  }

  var SIDEBAR_TOOLTIP_SELECTOR = '.nav-link-ide, .sidebar-toggle, .btn-logout-ide';

  function disposeSidebarTooltips() {
    if (!sidebar || typeof bootstrap === 'undefined') return;
    sidebar.querySelectorAll(SIDEBAR_TOOLTIP_SELECTOR).forEach(function (el) {
      var t = bootstrap.Tooltip.getInstance(el);
      if (t) t.dispose();
    });
  }

  function hideTitlesForExpanded() {
    if (!sidebar) return;
    sidebar.querySelectorAll(SIDEBAR_TOOLTIP_SELECTOR).forEach(function (el) {
      var t = el.getAttribute('title');
      if (t) {
        el.setAttribute('data-sidebar-title', t);
        el.removeAttribute('title');
      }
    });
  }

  function restoreTitlesForCollapsed() {
    if (!sidebar) return;
    sidebar.querySelectorAll(SIDEBAR_TOOLTIP_SELECTOR).forEach(function (el) {
      var t = el.getAttribute('data-sidebar-title');
      if (t) {
        el.setAttribute('title', t);
        el.removeAttribute('data-sidebar-title');
      }
    });
  }

  function initSidebarTooltips() {
    if (!sidebar || typeof bootstrap === 'undefined') return;
    var opts = { trigger: 'hover', delay: { show: 400, hide: 100 } };
    sidebar.querySelectorAll(SIDEBAR_TOOLTIP_SELECTOR).forEach(function (el) {
      if (bootstrap.Tooltip.getInstance(el)) return;
      var title = el.getAttribute('title');
      if (title) new bootstrap.Tooltip(el, opts);
    });
  }

  function applyCollapsed() {
    if (!sidebar || !main) return;
    document.documentElement.style.setProperty('--sidebar-width', WIDTH_COLLAPSED + 'px');
    sidebar.style.width = '';
    sidebar.classList.add('sidebar-ide--collapsed');
    localStorage.setItem(STORAGE_COLLAPSED, 'true');
    disposeSidebarTooltips();
    restoreTitlesForCollapsed();
    initSidebarTooltips();
  }

  function applyExpanded() {
    if (!sidebar || !main) return;
    var w = getStoredWidth();
    document.documentElement.style.setProperty('--sidebar-width', w + 'px');
    sidebar.style.width = w + 'px';
    sidebar.classList.remove('sidebar-ide--collapsed');
    localStorage.setItem(STORAGE_COLLAPSED, 'false');
    disposeSidebarTooltips();
    hideTitlesForExpanded();
  }

  function initState() {
    if (!sidebar) return;
    if (getStoredCollapsed()) {
      applyCollapsed();
    } else {
      var w = getStoredWidth();
      document.documentElement.style.setProperty('--sidebar-width', w + 'px');
      sidebar.style.width = w + 'px';
      disposeSidebarTooltips();
      hideTitlesForExpanded();
    }
  }

  function setupResize() {
    if (!resizeHandle || !sidebar) return;
    var startX, startWidth;
    resizeHandle.addEventListener('mousedown', function (e) {
      if (sidebar.classList.contains('sidebar-ide--collapsed')) return;
      e.preventDefault();
      startX = e.clientX;
      startWidth = sidebar.offsetWidth;

      function onMove(e) {
        var dx = e.clientX - startX;
        var newWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Math.round(startWidth + dx)));
        document.documentElement.style.setProperty('--sidebar-width', newWidth + 'px');
        sidebar.style.width = newWidth + 'px';
      }
      function onUp() {
        var w = sidebar.offsetWidth;
        localStorage.setItem(STORAGE_WIDTH, String(w));
        document.removeEventListener('mousemove', onMove);
        document.removeEventListener('mouseup', onUp);
      }
      document.addEventListener('mousemove', onMove);
      document.addEventListener('mouseup', onUp);
    });
  }

  function setupToggle() {
    if (!toggleBtn || !sidebar) return;
    toggleBtn.addEventListener('click', function () {
      if (sidebar.classList.contains('sidebar-ide--collapsed')) {
        applyExpanded();
        setToggleIconExpanded();
      } else {
        applyCollapsed();
        setToggleIconCollapsed();
      }
    });
  }

  function setToggleIconExpanded() {
    if (!toggleBtn) return;
    var icon = toggleBtn.querySelector('i');
    if (icon) icon.className = 'bi bi-chevron-left';
    toggleBtn.setAttribute('title', 'Contraer menú');
    toggleBtn.setAttribute('aria-label', 'Contraer menú');
  }

  function setToggleIconCollapsed() {
    if (!toggleBtn) return;
    var icon = toggleBtn.querySelector('i');
    if (icon) icon.className = 'bi bi-chevron-right';
    toggleBtn.setAttribute('title', 'Expandir menú');
    toggleBtn.setAttribute('aria-label', 'Expandir menú');
  }

  function updateToggleIcon() {
    if (!toggleBtn || !sidebar) return;
    if (sidebar.classList.contains('sidebar-ide--collapsed')) {
      setToggleIconCollapsed();
    } else {
      setToggleIconExpanded();
    }
  }

  function setupMobile() {
    if (!sidebar || !overlay || !mobileToggle) return;
    function openSidebar() {
      sidebar.classList.add('sidebar-ide--open');
      overlay.classList.add('sidebar-overlay--visible');
      document.body.style.overflow = 'hidden';
    }
    function closeSidebar() {
      sidebar.classList.remove('sidebar-ide--open');
      overlay.classList.remove('sidebar-overlay--visible');
      document.body.style.overflow = '';
    }
    mobileToggle.addEventListener('click', function () {
      if (sidebar.classList.contains('sidebar-ide--open')) closeSidebar();
      else openSidebar();
    });
    overlay.addEventListener('click', closeSidebar);
    window.addEventListener('resize', function () {
      if (window.innerWidth >= 768) closeSidebar();
    });
  }

  function init() {
    sidebar = document.querySelector('.sidebar-ide');
    main = document.querySelector('.dashboard-main');
    resizeHandle = document.getElementById('sidebarResizeHandle');
    toggleBtn = document.getElementById('sidebarToggle');
    overlay = document.getElementById('sidebarOverlay');
    mobileToggle = document.getElementById('sidebarMobileToggle');
    if (sidebar) {
      initState();
      updateToggleIcon();
      setupResize();
      setupToggle();
      setupMobile();
    }
  }

  if (document.querySelector('.sidebar-ide')) {
    init();
  } else {
    document.addEventListener('sidebarLoaded', init);
  }
})();
