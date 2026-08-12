/**
 * app.js — Logique applicative de WebHub.
 * Aucune dépendance externe (pas de framework), ES6 natif.
 */
(function () {
  "use strict";

  var DEFAULT_APPS = [
    {
      id: "baccpro",
      name: "baccPRO",
      description: "Révision du Bac",
      url: "",
      icon: "📚",
      color: "#14315C",
      category: "Éducation",
      favorite: false,
      createdAt: Date.now()
    },
    {
      id: "9pro",
      name: "9PRO",
      description: "Révision du 9e AF",
      url: "",
      icon: "🎓",
      color: "#1E4785",
      category: "Éducation",
      favorite: false,
      createdAt: Date.now()
    },
    {
      id: "collection-doree",
      name: "Collection Dorée",
      description: "Boutique",
      url: "",
      icon: "🛒",
      color: "#B8860B",
      category: "Boutique",
      favorite: false,
      createdAt: Date.now()
    },
    {
      id: "oddslab",
      name: "OddsLab",
      description: "Analyse sportive",
      url: "",
      icon: "⚽",
      color: "#0E7C61",
      category: "Sport",
      favorite: false,
      createdAt: Date.now()
    }
  ];

  var COLOR_CHOICES = [
    "#14315C", "#00b8a9", "#B8860B", "#0E7C61",
    "#8E44AD", "#E4572E", "#2C7BE5", "#455A64"
  ];

  var state = {
    apps: [],
    filter: "all", // all | favorites | <category>
    sort: "name", // name | recent
    query: "",
    editingId: null,
    activeApp: null
  };

  var el = {}; // cache des éléments DOM, rempli au démarrage

  // ------------------------------------------------------------------ init
  document.addEventListener("DOMContentLoaded", function () {
    cacheDom();
    bindEvents();
    applyStoredTheme();
    loadApps();
    registerServiceWorker();
    setupInstallPrompt();
  });

  function cacheDom() {
    el.grid = document.getElementById("app-grid");
    el.emptyState = document.getElementById("empty-state");
    el.searchInput = document.getElementById("search-input");
    el.filters = document.getElementById("filter-chips");
    el.sortSelect = document.getElementById("sort-select");
    el.themeToggle = document.getElementById("theme-toggle");
    el.settingsBtn = document.getElementById("settings-btn");
    el.homeView = document.getElementById("home-view");
    el.settingsView = document.getElementById("settings-view");
    el.backFromSettings = document.getElementById("back-from-settings");

    el.fab = document.getElementById("fab-add");
    el.modalBackdrop = document.getElementById("add-modal-backdrop");
    el.modalTitle = document.getElementById("modal-title");
    el.form = document.getElementById("app-form");
    el.fieldName = document.getElementById("field-name");
    el.fieldDesc = document.getElementById("field-description");
    el.fieldUrl = document.getElementById("field-url");
    el.fieldIcon = document.getElementById("field-icon");
    el.fieldCategory = document.getElementById("field-category");
    el.colorRow = document.getElementById("color-row");
    el.formError = document.getElementById("form-error");
    el.cancelModal = document.getElementById("cancel-modal");
    el.deleteAppBtn = document.getElementById("delete-app-btn");

    el.viewer = document.getElementById("viewer-view");
    el.viewerFrame = document.getElementById("viewer-frame");
    el.viewerTitle = document.getElementById("viewer-title");
    el.viewerLoading = document.getElementById("viewer-loading");
    el.viewerBack = document.getElementById("viewer-back");
    el.viewerRefresh = document.getElementById("viewer-refresh");
    el.viewerShare = document.getElementById("viewer-share");
    el.viewerFullscreen = document.getElementById("viewer-fullscreen");
    el.viewerMenuBtn = document.getElementById("viewer-menu-btn");
    el.viewerMenuPanel = document.getElementById("viewer-menu-panel");
    el.viewerLogout = document.getElementById("viewer-logout");
    el.viewerOpenExternal = document.getElementById("viewer-open-external");

    el.toastStack = document.getElementById("toast-stack");

    el.themeSwitch = document.getElementById("theme-switch-row");
    el.clearCacheBtn = document.getElementById("clear-cache-btn");
    el.exportBtn = document.getElementById("export-btn");
    el.importInput = document.getElementById("import-input");
    el.importBtn = document.getElementById("import-btn");
    el.installBtn = document.getElementById("install-btn");
    el.versionLabel = document.getElementById("version-label");
    el.resetBtn = document.getElementById("reset-btn");
  }

  function bindEvents() {
    el.searchInput.addEventListener("input", function (e) {
      state.query = e.target.value.trim().toLowerCase();
      render();
    });

    el.filters.addEventListener("click", function (e) {
      var chip = e.target.closest(".chip");
      if (!chip) return;
      state.filter = chip.dataset.filter;
      Array.prototype.forEach.call(el.filters.querySelectorAll(".chip"), function (c) {
        c.classList.toggle("active", c === chip);
      });
      render();
    });

    el.sortSelect.addEventListener("change", function (e) {
      state.sort = e.target.value;
      render();
    });

    el.themeToggle.addEventListener("click", toggleTheme);
    el.settingsBtn.addEventListener("click", openSettings);
    el.backFromSettings.addEventListener("click", closeSettings);

    el.fab.addEventListener("click", function () { openAppModal(null); });
    el.cancelModal.addEventListener("click", closeAppModal);
    el.modalBackdrop.addEventListener("click", function (e) {
      if (e.target === el.modalBackdrop) closeAppModal();
    });
    el.form.addEventListener("submit", handleFormSubmit);
    el.deleteAppBtn.addEventListener("click", handleDeleteApp);

    el.colorRow.addEventListener("click", function (e) {
      var swatch = e.target.closest(".color-swatch");
      if (!swatch) return;
      Array.prototype.forEach.call(el.colorRow.querySelectorAll(".color-swatch"), function (s) {
        s.classList.toggle("selected", s === swatch);
      });
    });

    el.viewerBack.addEventListener("click", closeViewer);
    el.viewerRefresh.addEventListener("click", refreshViewer);
    el.viewerShare.addEventListener("click", shareActiveApp);
    el.viewerFullscreen.addEventListener("click", toggleViewerFullscreen);
    el.viewerMenuBtn.addEventListener("click", function () {
      el.viewerMenuPanel.classList.toggle("hidden");
    });
    document.addEventListener("click", function (e) {
      if (!el.viewerMenuPanel.classList.contains("hidden") &&
          !el.viewerMenuPanel.contains(e.target) &&
          e.target !== el.viewerMenuBtn) {
        el.viewerMenuPanel.classList.add("hidden");
      }
    });
    el.viewerLogout.addEventListener("click", logoutActiveApp);
    el.viewerOpenExternal.addEventListener("click", function () {
      if (state.activeApp) window.open(state.activeApp.url, "_blank", "noopener");
    });
    el.viewerFrame.addEventListener("load", function () {
      el.viewerLoading.classList.add("hidden");
    });

    el.clearCacheBtn.addEventListener("click", clearAppCache);
    el.resetBtn.addEventListener("click", resetWebHub);
    el.exportBtn.addEventListener("click", exportData);
    el.importBtn.addEventListener("click", function () { el.importInput.click(); });
    el.importInput.addEventListener("change", importData);
    el.themeSwitch.addEventListener("click", toggleTheme);

    window.addEventListener("keydown", function (e) {
      if (e.key === "Escape") {
        if (!el.modalBackdrop.classList.contains("hidden")) closeAppModal();
        else if (!el.viewer.classList.contains("hidden")) closeViewer();
      }
    });
  }

  // ------------------------------------------------------------------ data
  function loadApps() {
    WebHubDB.getAllApps().then(function (apps) {
      if (apps.length === 0) {
        // Première utilisation : on propose les Web Apps d'exemple.
        return Promise.all(DEFAULT_APPS.map(function (a) { return WebHubDB.saveApp(a); }))
          .then(function () { return DEFAULT_APPS.slice(); });
      }
      return apps;
    }).then(function (apps) {
      state.apps = apps;
      renderFilterChips();
      render();
    }).catch(function (err) {
      console.error("Erreur de chargement des Web Apps :", err);
      showToast("Impossible de charger les Web Apps enregistrées.");
    });
  }

  function renderFilterChips() {
    var categories = Array.from(new Set(state.apps.map(function (a) { return a.category; }).filter(Boolean)));
    var extra = categories.map(function (c) {
      return '<button class="chip" data-filter="' + escapeHtml(c) + '">' + escapeHtml(c) + "</button>";
    }).join("");
    el.filters.innerHTML =
      '<button class="chip active" data-filter="all">Toutes</button>' +
      '<button class="chip" data-filter="favorites">Favoris</button>' + extra;
  }

  function getFilteredSortedApps() {
    var list = state.apps.filter(function (a) {
      if (state.filter === "favorites" && !a.favorite) return false;
      if (state.filter !== "all" && state.filter !== "favorites" && a.category !== state.filter) return false;
      if (state.query) {
        var haystack = (a.name + " " + a.description).toLowerCase();
        if (haystack.indexOf(state.query) === -1) return false;
      }
      return true;
    });
    list.sort(function (a, b) {
      if (state.sort === "recent") return (b.createdAt || 0) - (a.createdAt || 0);
      return a.name.localeCompare(b.name);
    });
    return list;
  }

  // ------------------------------------------------------------------ render
  function render() {
    var list = getFilteredSortedApps();
    el.grid.innerHTML = "";
    el.emptyState.classList.toggle("hidden", list.length > 0);

    list.forEach(function (app) {
      el.grid.appendChild(buildCard(app));
    });
  }

  function buildCard(app) {
    var card = document.createElement("div");
    card.className = "app-card";
    card.dataset.id = app.id;

    var initials = (app.icon && /\p{Emoji}/u.test(app.icon)) ? app.icon : (app.name || "?").charAt(0).toUpperCase();

    card.innerHTML =
      '<div class="blob" style="background:' + escapeAttr(app.color || "#14315C") + '">' + escapeHtml(initials) + "</div>" +
      "<h3>" + escapeHtml(app.name) + "</h3>" +
      "<p>" + escapeHtml(app.description || "") + "</p>" +
      '<div class="card-footer">' +
      '<button class="open-btn" data-action="open">Ouvrir</button>' +
      '<button class="fav-btn' + (app.favorite ? " active" : "") + '" data-action="fav" title="Favori" aria-label="Marquer comme favori">' +
      favIconSvg() +
      "</button></div>";

    card.addEventListener("click", function (e) {
      spawnRipple(card, e);
      var action = e.target.closest("[data-action]");
      if (action && action.dataset.action === "fav") {
        e.stopPropagation();
        toggleFavorite(app.id);
        return;
      }
      if (action && action.dataset.action === "open") {
        openViewer(app);
        return;
      }
      // Clic long / droit non géré ici ; double-clic sur la carte = éditer.
    });

    card.addEventListener("dblclick", function () { openAppModal(app.id); });

    return card;
  }

  function spawnRipple(card, evt) {
    var rect = card.getBoundingClientRect();
    var ripple = document.createElement("span");
    ripple.className = "ripple";
    var size = Math.max(rect.width, rect.height);
    ripple.style.width = ripple.style.height = size + "px";
    ripple.style.left = (evt.clientX - rect.left - size / 2) + "px";
    ripple.style.top = (evt.clientY - rect.top - size / 2) + "px";
    card.appendChild(ripple);
    setTimeout(function () { ripple.remove(); }, 600);
  }

  function toggleFavorite(id) {
    var app = state.apps.find(function (a) { return a.id === id; });
    if (!app) return;
    app.favorite = !app.favorite;
    WebHubDB.saveApp(app).then(render);
  }

  // ------------------------------------------------------------------ modal ajout/édition
  function openAppModal(id) {
    state.editingId = id;
    var app = id ? state.apps.find(function (a) { return a.id === id; }) : null;

    el.modalTitle.textContent = app ? "Modifier la Web App" : "Ajouter une Web App";
    el.fieldName.value = app ? app.name : "";
    el.fieldDesc.value = app ? app.description : "";
    el.fieldUrl.value = app ? app.url : "";
    el.fieldIcon.value = app ? app.icon : "";
    el.fieldCategory.value = app ? (app.category || "") : "";
    el.formError.textContent = "";
    el.deleteAppBtn.classList.toggle("hidden", !app);

    el.colorRow.innerHTML = COLOR_CHOICES.map(function (c) {
      var selected = app ? app.color === c : c === COLOR_CHOICES[0];
      return '<button type="button" class="color-swatch' + (selected ? " selected" : "") +
        '" data-color="' + c + '" style="background:' + c + '" aria-label="Choisir la couleur ' + c + '"></button>';
    }).join("");

    el.modalBackdrop.classList.remove("hidden");
    el.fieldName.focus();
  }

  function closeAppModal() {
    el.modalBackdrop.classList.add("hidden");
    state.editingId = null;
  }

  function handleFormSubmit(e) {
    e.preventDefault();
    var name = el.fieldName.value.trim();
    var url = el.fieldUrl.value.trim();

    if (!name) { el.formError.textContent = "Le nom est obligatoire."; return; }
    if (!isValidHttpsUrl(url)) {
      el.formError.textContent = "L'URL doit être une adresse HTTPS valide.";
      return;
    }

    var selectedSwatch = el.colorRow.querySelector(".color-swatch.selected");
    var color = selectedSwatch ? selectedSwatch.dataset.color : COLOR_CHOICES[0];

    var app = state.editingId
      ? state.apps.find(function (a) { return a.id === state.editingId; })
      : {
          id: "app-" + Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
          favorite: false,
          createdAt: Date.now()
        };

    app.name = name;
    app.description = el.fieldDesc.value.trim();
    app.url = url;
    app.icon = el.fieldIcon.value.trim() || name.charAt(0).toUpperCase();
    app.category = el.fieldCategory.value.trim();
    app.color = color;

    WebHubDB.saveApp(app).then(function () {
      if (!state.editingId) state.apps.push(app);
      closeAppModal();
      renderFilterChips();
      render();
      showToast("Web App enregistrée.");
    }).catch(function (err) {
      console.error(err);
      el.formError.textContent = "Erreur lors de l'enregistrement.";
    });
  }

  function handleDeleteApp() {
    if (!state.editingId) return;
    if (!window.confirm("Supprimer définitivement cette Web App de WebHub ?")) return;
    var id = state.editingId;
    WebHubDB.deleteApp(id).then(function () {
      state.apps = state.apps.filter(function (a) { return a.id !== id; });
      closeAppModal();
      renderFilterChips();
      render();
      showToast("Web App supprimée.");
    });
  }

  function isValidHttpsUrl(value) {
    if (!value) return false;
    try {
      var u = new URL(value);
      return u.protocol === "https:";
    } catch (e) {
      return false;
    }
  }

  // ------------------------------------------------------------------ visionneuse
  function openViewer(app) {
    if (!app.url) {
      showToast("Aucune URL configurée pour cette Web App. Modifiez-la pour en ajouter une.");
      openAppModal(app.id);
      return;
    }
    state.activeApp = app;
    el.viewerTitle.textContent = app.name;
    el.viewerLoading.classList.remove("hidden");
    el.viewerFrame.src = app.url;
    el.viewer.classList.remove("hidden");
    el.viewerMenuPanel.classList.add("hidden");
    document.body.style.overflow = "hidden";
  }

  function closeViewer() {
    el.viewer.classList.add("hidden");
    el.viewerFrame.src = "about:blank";
    state.activeApp = null;
    document.body.style.overflow = "";
  }

  function refreshViewer() {
    if (!state.activeApp) return;
    el.viewerLoading.classList.remove("hidden");
    el.viewerFrame.src = el.viewerFrame.src; // recharge
  }

  function shareActiveApp() {
    if (!state.activeApp) return;
    var data = { title: state.activeApp.name, text: state.activeApp.description, url: state.activeApp.url };
    if (navigator.share) {
      navigator.share(data).catch(function () {});
    } else if (navigator.clipboard) {
      navigator.clipboard.writeText(state.activeApp.url).then(function () {
        showToast("Lien copié dans le presse-papiers.");
      });
    }
  }

  function toggleViewerFullscreen() {
    if (!document.fullscreenElement) {
      el.viewer.requestFullscreen && el.viewer.requestFullscreen().catch(function () {});
    } else {
      document.exitFullscreen && document.exitFullscreen();
    }
  }

  /**
   * "Déconnexion" — vide uniquement les données de la Web App actuellement
   * ouverte, jamais celles des autres.
   *
   * Limite technique à connaître : pour des raisons de sécurité du
   * navigateur (Same-Origin Policy), une page ne peut pas effacer par
   * JavaScript le localStorage, les cookies ou le cache d'une autre origine
   * — même depuis une iframe qu'elle héberge. WebHub applique donc la
   * meilleure approche possible côté client : il force un rechargement
   * complet de l'iframe (ce qui réinitialise l'état JS en mémoire de la
   * Web App, y compris sessionStorage) et, si la Web App expose un
   * paramètre d'URL de déconnexion standard, il peut être ajouté dans ses
   * paramètres. Un effacement garanti des cookies/localStorage persistants
   * nécessite une action de la Web App elle-même (son propre bouton de
   * déconnexion) ou un nettoyage manuel dans les paramètres du navigateur.
   */
  function logoutActiveApp() {
    if (!state.activeApp) return;
    el.viewerMenuPanel.classList.add("hidden");
    var app = state.activeApp;
    el.viewerLoading.classList.remove("hidden");
    var bustUrl = app.url + (app.url.indexOf("?") === -1 ? "?" : "&") + "_wh_reset=" + Date.now();
    el.viewerFrame.src = "about:blank";
    setTimeout(function () { el.viewerFrame.src = bustUrl; }, 60);
    showToast('"' + app.name + '" a été rechargée. Les cookies/données persistantes propres à cette Web App ne peuvent être effacés que par elle-même, conformément aux règles de sécurité du navigateur.');
  }

  // ------------------------------------------------------------------ thème
  function applyStoredTheme() {
    WebHubDB.getSetting("theme", "auto").then(function (theme) {
      setTheme(theme === "auto" ? preferredSystemTheme() : theme, false);
    });
  }

  function preferredSystemTheme() {
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }

  function setTheme(theme, persist) {
    document.documentElement.setAttribute("data-theme", theme);
    el.themeSwitch && el.themeSwitch.classList.toggle("on", theme === "dark");
    if (persist) WebHubDB.setSetting("theme", theme);
  }

  function toggleTheme() {
    var current = document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light";
    setTheme(current === "dark" ? "light" : "dark", true);
  }

  // ------------------------------------------------------------------ paramètres
  function openSettings() {
    el.homeView.classList.add("hidden");
    el.settingsView.classList.remove("hidden");
    el.versionLabel.textContent = window.WEBHUB_VERSION || "1.0.0";
  }

  function closeSettings() {
    el.settingsView.classList.add("hidden");
    el.homeView.classList.remove("hidden");
  }

  function clearAppCache() {
    if (!window.confirm("Vider le cache de WebHub (fichiers hors-ligne) ? Vos Web Apps enregistrées ne sont pas affectées.")) return;
    if ("caches" in window) {
      caches.keys().then(function (names) {
        return Promise.all(names.map(function (n) { return caches.delete(n); }));
      }).then(function () {
        showToast("Cache hors-ligne vidé.");
      });
    }
  }

  function resetWebHub() {
    if (!window.confirm("Cette opération supprimera toutes les Web Apps, leurs configurations et leurs données locales. Cette action est irréversible. Continuer ?")) return;
    WebHubDB.resetAll().then(function () {
      showToast("WebHub a été réinitialisé.");
      closeSettings();
      loadApps();
    }).catch(function (err) {
      console.error(err);
      showToast("Erreur lors de la réinitialisation.");
    });
  }

  function exportData() {
    WebHubDB.exportAll().then(function (data) {
      var blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
      var a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = "webhub-export-" + new Date().toISOString().slice(0, 10) + ".json";
      a.click();
      URL.revokeObjectURL(a.href);
      showToast("Export généré.");
    });
  }

  function importData(e) {
    var file = e.target.files[0];
    if (!file) return;
    var reader = new FileReader();
    reader.onload = function () {
      try {
        var data = JSON.parse(reader.result);
        if (!window.confirm("Importer ces données remplacera la liste actuelle de Web Apps. Continuer ?")) return;
        WebHubDB.importAll(data).then(function () {
          showToast("Import réussi.");
          loadApps();
        });
      } catch (err) {
        showToast("Fichier d'import invalide.");
      }
      e.target.value = "";
    };
    reader.readAsText(file);
  }

  // ------------------------------------------------------------------ PWA
  var deferredInstallPrompt = null;

  function setupInstallPrompt() {
    window.addEventListener("beforeinstallprompt", function (e) {
      e.preventDefault();
      deferredInstallPrompt = e;
      el.installBtn.classList.remove("hidden");
    });
    el.installBtn.addEventListener("click", function () {
      if (!deferredInstallPrompt) return;
      deferredInstallPrompt.prompt();
      deferredInstallPrompt.userChoice.finally(function () {
        deferredInstallPrompt = null;
        el.installBtn.classList.add("hidden");
      });
    });
  }

  function registerServiceWorker() {
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.register("service-worker.js").catch(function (err) {
        console.warn("Échec de l'enregistrement du service worker :", err);
      });
    }
  }

  // ------------------------------------------------------------------ utils
  function showToast(message) {
    var toast = document.createElement("div");
    toast.className = "toast";
    toast.textContent = message;
    el.toastStack.appendChild(toast);
    setTimeout(function () { toast.remove(); }, 3200);
  }

  function favIconSvg() {
    return '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.86L12 17.77l-6.18 3.23L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>';
  }

  function escapeHtml(str) {
    return String(str == null ? "" : str).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function escapeAttr(str) { return escapeHtml(str); }
})();

