/**
 * db.js — Couche de persistance de WebHub.
 *
 * Utilise IndexedDB pour stocker la liste des Web Apps et les paramètres.
 * Chaque Web App est un enregistrement indépendant :
 *   { id, name, description, url, icon, color, category, favorite, createdAt }
 *
 * IMPORTANT — Isolation des données entre Web Apps :
 * WebHub ne stocke JAMAIS les données (session, cookies, cache) des Web Apps
 * qu'il lance. Chaque Web App est ouverte dans une <iframe> pointant vers sa
 * propre URL ; le navigateur applique nativement la politique de même origine
 * (Same-Origin Policy), ce qui isole automatiquement le localStorage, les
 * cookies et le cache de chaque origine. WebHub ne fait donc que retenir des
 * métadonnées (nom, url, icône...), jamais le contenu des sessions.
 */
(function (global) {
  "use strict";

  var DB_NAME = "webhub-db";
  var DB_VERSION = 1;
  var STORE_APPS = "apps";
  var STORE_SETTINGS = "settings";

  var dbPromise = null;

  function openDb() {
    if (dbPromise) return dbPromise;
    dbPromise = new Promise(function (resolve, reject) {
      var req = indexedDB.open(DB_NAME, DB_VERSION);

      req.onupgradeneeded = function (event) {
        var db = event.target.result;
        if (!db.objectStoreNames.contains(STORE_APPS)) {
          var appsStore = db.createObjectStore(STORE_APPS, { keyPath: "id" });
          appsStore.createIndex("name", "name", { unique: false });
          appsStore.createIndex("category", "category", { unique: false });
        }
        if (!db.objectStoreNames.contains(STORE_SETTINGS)) {
          db.createObjectStore(STORE_SETTINGS, { keyPath: "key" });
        }
      };

      req.onsuccess = function (event) { resolve(event.target.result); };
      req.onerror = function (event) { reject(event.target.error); };
    });
    return dbPromise;
  }

  function tx(storeName, mode) {
    return openDb().then(function (db) {
      return db.transaction(storeName, mode).objectStore(storeName);
    });
  }

  function promisifyRequest(req) {
    return new Promise(function (resolve, reject) {
      req.onsuccess = function () { resolve(req.result); };
      req.onerror = function () { reject(req.error); };
    });
  }

  var WebHubDB = {
    /** Retourne toutes les Web Apps enregistrées. */
    getAllApps: function () {
      return tx(STORE_APPS, "readonly").then(function (store) {
        return promisifyRequest(store.getAll());
      });
    },

    /** Ajoute ou met à jour une Web App. */
    saveApp: function (app) {
      return tx(STORE_APPS, "readwrite").then(function (store) {
        return promisifyRequest(store.put(app));
      });
    },

    /** Supprime une Web App par id. */
    deleteApp: function (id) {
      return tx(STORE_APPS, "readwrite").then(function (store) {
        return promisifyRequest(store.delete(id));
      });
    },

    /** Remplace entièrement la liste des Web Apps (utilisé par l'import). */
    replaceAllApps: function (apps) {
      return openDb().then(function (db) {
        return new Promise(function (resolve, reject) {
          var t = db.transaction(STORE_APPS, "readwrite");
          var store = t.objectStore(STORE_APPS);
          store.clear();
          apps.forEach(function (app) { store.put(app); });
          t.oncomplete = function () { resolve(); };
          t.onerror = function () { reject(t.error); };
        });
      });
    },

    /** Lit un paramètre (retourne defaultValue si absent). */
    getSetting: function (key, defaultValue) {
      return tx(STORE_SETTINGS, "readonly").then(function (store) {
        return promisifyRequest(store.get(key));
      }).then(function (row) {
        return row ? row.value : defaultValue;
      });
    },

    /** Écrit un paramètre. */
    setSetting: function (key, value) {
      return tx(STORE_SETTINGS, "readwrite").then(function (store) {
        return promisifyRequest(store.put({ key: key, value: value }));
      });
    },

    /** Exporte l'intégralité des données WebHub (apps + paramètres) en JSON. */
    exportAll: function () {
      return Promise.all([
        this.getAllApps(),
        tx(STORE_SETTINGS, "readonly").then(function (store) {
          return promisifyRequest(store.getAll());
        })
      ]).then(function (results) {
        return {
          version: 1,
          exportedAt: new Date().toISOString(),
          apps: results[0],
          settings: results[1]
        };
      });
    },

    /** Importe un export JSON précédemment généré par exportAll(). */
    importAll: function (data) {
      var self = this;
      var apps = (data && data.apps) || [];
      var settings = (data && data.settings) || [];
      return self.replaceAllApps(apps).then(function () {
        return tx(STORE_SETTINGS, "readwrite").then(function (store) {
          return new Promise(function (resolve, reject) {
            settings.forEach(function (row) { store.put(row); });
            store.transaction.oncomplete = function () { resolve(); };
            store.transaction.onerror = function () { reject(store.transaction.error); };
          });
        });
      });
    }
  };

  global.WebHubDB = WebHubDB;
})(window);
