// Service Worker der PWA (NF-05). Bewusst schlank: Daten kommen immer frisch vom Server.
// Nur wenn das Netz fehlt, zeigt eine Navigation die Offline-Seite.
const CACHE = 'stv-offline-v1';
const OFFLINE = '/offline.html';

self.addEventListener('install', (ereignis) => {
  ereignis.waitUntil(caches.open(CACHE).then((cache) => cache.addAll([OFFLINE, '/icons/icon.svg'])));
  self.skipWaiting();
});

self.addEventListener('activate', (ereignis) => {
  ereignis.waitUntil(
    caches
      .keys()
      .then((namen) => Promise.all(namen.filter((n) => n !== CACHE).map((n) => caches.delete(n))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (ereignis) => {
  if (ereignis.request.mode !== 'navigate') {
    return;
  }
  ereignis.respondWith(fetch(ereignis.request).catch(() => caches.match(OFFLINE)));
});
