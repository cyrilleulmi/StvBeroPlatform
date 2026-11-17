/**
 * Web Push in the browser (F-19, criteria 3 and 4): support, permission and the push subscription of this device.
 * The service worker of the PWA (`/sw.js`) receives the pushes.
 */

/** What the browser can do with push on this device. */
export type PushSupport = 'supported' | 'iphone-home-screen' | 'unsupported';

/** iPhone or iPad (iPadOS reports itself as a Mac with touch). */
export function isIos(): boolean {
  return (
    /iPhone|iPad|iPod/.test(navigator.userAgent) ||
    (navigator.userAgent.includes('Macintosh') && navigator.maxTouchPoints > 1)
  );
}

export function isAndroid(): boolean {
  return navigator.userAgent.includes('Android');
}

/** Started from the home screen (installed PWA). */
function standalone(): boolean {
  const nav = navigator as Navigator & { standalone?: boolean };
  return nav.standalone === true || window.matchMedia?.('(display-mode: standalone)').matches === true;
}

/**
 * On iPhone, Safari offers push only to an app added to the home screen (iOS 16.4 and later). There we explain how
 * to add it instead of saying the browser cannot do it.
 */
export function pushSupport(): PushSupport {
  const available = 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  if (isIos() && !standalone()) return 'iphone-home-screen';
  return available ? 'supported' : 'unsupported';
}

export function permission(): NotificationPermission {
  return 'Notification' in window ? Notification.permission : 'denied';
}

async function registration(): Promise<ServiceWorkerRegistration> {
  // Registering again returns the existing registration; works even before the page has finished loading.
  return navigator.serviceWorker.register('/sw.js');
}

/** The push subscription of this device, if there is one. */
export async function currentSubscription(): Promise<PushSubscription | null> {
  if (pushSupport() !== 'supported') return null;
  return (await registration()).pushManager.getSubscription();
}

function keyBytes(base64url: string): Uint8Array<ArrayBuffer> {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64 + '='.repeat((4 - (base64.length % 4)) % 4));
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

function sameKey(a: ArrayBuffer | null | undefined, b: Uint8Array): boolean {
  if (!a || a.byteLength !== b.byteLength) return false;
  const view = new Uint8Array(a);
  return view.every((v, i) => v === b[i]);
}

/**
 * Subscribes this device with the server's public key. An existing subscription with another key (the server
 * key was changed) is replaced.
 */
export async function subscribe(publicKey: string): Promise<PushSubscription> {
  const key = keyBytes(publicKey);
  const manager = (await registration()).pushManager;
  const existing = await manager.getSubscription();
  if (existing) {
    if (sameKey(existing.options?.applicationServerKey, key)) return existing;
    await existing.unsubscribe();
  }
  return manager.subscribe({ userVisibleOnly: true, applicationServerKey: key });
}
