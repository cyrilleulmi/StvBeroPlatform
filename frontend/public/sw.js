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

// Push (F-19, BEN-10): shows the notification, also when the app is closed. Content from the server:
// { title, body, url, tag }. The tag replaces an older push of the same notification instead of showing it twice.
self.addEventListener('push', (ereignis) => {
  let daten = {};
  try {
    daten = ereignis.data ? ereignis.data.json() : {};
  } catch {
    daten = { body: ereignis.data ? ereignis.data.text() : '' };
  }
  const titel = daten.title || 'STV Bero';
  ereignis.waitUntil(
    self.registration.showNotification(titel, {
      body: daten.body || '',
      tag: daten.tag,
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-192.png',
      lang: 'de-CH',
      data: { url: daten.url || '/benachrichtigungen' },
    }),
  );
});

// A tap on the push opens the event in the app (criterion 4): an open window of the app is reused.
self.addEventListener('notificationclick', (ereignis) => {
  ereignis.notification.close();
  const ziel = new URL(ereignis.notification.data?.url || '/', self.location.origin).href;
  ereignis.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((fenster) => {
      for (const f of fenster) {
        if (new URL(f.url).origin === self.location.origin && 'focus' in f) {
          return f.navigate(ziel).then((g) => (g || f).focus());
        }
      }
      return self.clients.openWindow(ziel);
    }),
  );
});
