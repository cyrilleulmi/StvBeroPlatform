import '@angular/common/locales/global/de-CH';
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

bootstrapApplication(App, appConfig).catch((err) => console.error(err));

// Press feedback (`:active`, styles.css) only fires in iOS Safari when a touch listener exists.
document.addEventListener('touchstart', () => undefined, { passive: true });

// PWA (NF-05): service worker for installability and an offline page.
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch((err) => console.error('Service Worker', err));
  });
}
