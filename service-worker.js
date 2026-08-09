/**
 * service-worker.js — Met en cache l'app shell de WebHub (le lanceur
 * lui-même) pour un chargement rapide et un usage hors-ligne.
 *
 * Important : ce service worker ne met en cache QUE les fichiers propres à
 * WebHub. Il n'intercepte jamais les requêtes faites par les Web Apps
 * ouvertes dans la visionneuse (elles vivent sur d'autres origines et sont
 * gérées, le cas échéant, par leur propre service worker).
 */
"use strict";

var CACHE_NAME = "webhub-cache-v1";
var APP_SHELL = [
  "./",
  "./index.html",
  "./manifest.json",
  "./css/styles.css",
  "./js/app.js",
  "./js/db.js",
  "./icons/icon-192.png",
  "./icons/icon-512.png"
];

self.addEventListener("install", function (event) {
  event.waitUntil(
    caches.open(CACHE_NAME).then(function (cache) {
      return cache.addAll(APP_SHELL);
    }).then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener("activate", function (event) {
  event.waitUntil(
    caches.keys().then(function (names) {
      return Promise.all(
        names
          .filter(function (name) { return name !== CACHE_NAME; })
          .map(function (name) { return caches.delete(name); })
      );
    }).then(function () { return self.clients.claim(); })
  );
});

self.addEventListener("fetch", function (event) {
  var url = new URL(event.request.url);

  // Ne gère que les requêtes de même origine (l'app shell WebHub).
  // Les Web Apps ouvertes dans la visionneuse pointent vers d'autres
  // origines et ne passent jamais par ce gestionnaire.
  if (url.origin !== self.location.origin) return;
  if (event.request.method !== "GET") return;

  event.respondWith(
    caches.match(event.request).then(function (cached) {
      var networkFetch = fetch(event.request).then(function (response) {
        if (response && response.ok) {
          var clone = response.clone();
          caches.open(CACHE_NAME).then(function (cache) { cache.put(event.request, clone); });
        }
        return response;
      }).catch(function () { return cached; });

      // Cache d'abord pour un chargement instantané, mise à jour en tâche de fond.
      return cached || networkFetch;
    })
  );
});

