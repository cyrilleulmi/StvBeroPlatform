import type { NotificationSettings } from '../api/types';
import { TEXTS } from '../core/texts';

/** Push is on for this device: its subscription (`endpoint`) is registered on the server (F-19). */
export function pushOnThisDevice(
  settings: Pick<NotificationSettings, 'devices'> | null,
  endpoint: string | null,
): boolean {
  return endpoint !== null && (settings?.devices.some((d) => d.endpoint === endpoint) ?? false);
}

/**
 * State line of «Benachrichtigungen» in the profile: «E-Mail an · Push auf diesem Gerät aus». Push is left out
 * when the server cannot send pushes.
 */
export function notificationSummary(
  settings: Pick<NotificationSettings, 'email' | 'pushAvailable' | 'devices'>,
  endpoint: string | null,
): string {
  const t = TEXTS.notificationSettings;
  const parts = [t.stateEmail(settings.email)];
  if (settings.pushAvailable) parts.push(t.statePush(pushOnThisDevice(settings, endpoint)));
  return parts.join(' · ');
}
