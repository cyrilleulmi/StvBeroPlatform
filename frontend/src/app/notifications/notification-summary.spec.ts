import type { NotificationSettings, PushDevice } from '../api/types';
import { notificationSummary, pushOnThisDevice } from './notification-summary';

const device = (endpoint: string): PushDevice =>
  ({ id: endpoint, endpoint, label: 'Chrome', createdAt: '2026-11-01T10:00:00Z' }) as PushDevice;

const settings = (over: Partial<NotificationSettings>): NotificationSettings => ({
  email: true,
  emailAvailable: true,
  pushAvailable: true,
  devices: [],
  ...over,
});

describe('notification summary (F-19, E-109)', () => {
  it('push is on only when this device is registered', () => {
    const s = settings({ devices: [device('https://push/a')] });
    expect(pushOnThisDevice(s, 'https://push/a')).toBe(true);
    expect(pushOnThisDevice(s, 'https://push/b')).toBe(false);
    expect(pushOnThisDevice(s, null)).toBe(false);
    expect(pushOnThisDevice(null, 'https://push/a')).toBe(false);
  });

  it('names e-mail and push on this device', () => {
    expect(notificationSummary(settings({}), null)).toBe('E-Mail an · Push auf diesem Gerät aus');
    expect(notificationSummary(settings({ email: false, devices: [device('e')] }), 'e')).toBe(
      'E-Mail aus · Push auf diesem Gerät an',
    );
  });

  it('leaves push out when the server cannot send pushes', () => {
    expect(notificationSummary(settings({ pushAvailable: false }), null)).toBe('E-Mail an');
  });
});
