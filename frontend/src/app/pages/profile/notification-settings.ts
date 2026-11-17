import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { NotificationSettings, PushDevice } from '../../api/types';
import { errorText } from '../../core/error-text';
import { date } from '../../core/format';
import {
  PushSupport,
  currentSubscription,
  isAndroid,
  isIos,
  permission,
  pushSupport,
  subscribe,
} from '../../core/push';
import { TEXTS } from '../../core/texts';

/**
 * Section «Benachrichtigungen» in the profile (F-19, criteria 2 and 3): one switch for e-mail and one for push on
 * this device, the other devices with push, and explanations when push is not possible or was not allowed.
 */
@Component({
  selector: 'app-notification-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      class="card channels"
      aria-labelledby="profile-notifications"
      data-testid="notification-settings"
      [attr.aria-busy]="loading() ? 'true' : null"
    >
      <h2 id="profile-notifications" class="section-title">{{ texts.title }}</h2>
      <p class="secondary-text">{{ texts.intro }}</p>

      @if (settings(); as s) {
        <div class="channel">
          <label class="checkbox">
            <input
              type="checkbox"
              role="switch"
              aria-describedby="channel-email-hint"
              [checked]="s.email"
              [disabled]="!s.emailAddress || !s.emailAvailable || busy()"
              (change)="setEmail($any($event.target).checked)"
            />
            {{ texts.email }}
          </label>
          <p id="channel-email-hint" class="secondary-text hint">
            @if (!s.emailAvailable) {
              {{ texts.emailUnavailable }}
            } @else if (s.emailAddress) {
              {{ texts.emailTo(s.emailAddress) }}
            } @else {
              {{ texts.emailNoAddress }}
            }
          </p>
        </div>

        <div class="channel">
          <label class="checkbox">
            <input
              type="checkbox"
              role="switch"
              aria-describedby="channel-push-hint"
              [checked]="pushOn()"
              [disabled]="!pushPossible() || busy()"
              (change)="setPush($any($event.target).checked)"
            />
            {{ texts.push }}
          </label>
          <p id="channel-push-hint" class="secondary-text hint">
            @if (!s.pushAvailable) {
              {{ texts.pushUnavailable }}
            } @else if (support === 'unsupported') {
              {{ texts.pushUnsupported }}
            } @else {
              {{ texts.pushHint }}
            }
          </p>
        </div>

        @if (s.pushAvailable && support === 'iphone-home-screen') {
          <div class="message-notice explanation" data-testid="push-iphone-hint">
            <p class="explanation-title">{{ texts.iphoneTitle }}</p>
            <ol>
              @for (step of texts.iphoneSteps; track $index) {
                <li>{{ step }}</li>
              }
            </ol>
          </div>
        }

        @if (s.pushAvailable && support === 'supported' && denied()) {
          <div class="message-notice explanation" role="alert" data-testid="push-denied-hint">
            <p class="explanation-title">{{ texts.deniedTitle }}</p>
            <p>{{ texts.deniedText }}</p>
            <p>{{ deniedHowTo }}</p>
            <p>{{ texts.deniedAfter }}</p>
          </div>
        }

        @if (otherDevices().length > 0) {
          <h3 class="subtitle">{{ texts.otherDevices }}</h3>
          <ul class="devices" data-testid="push-devices">
            @for (d of otherDevices(); track d.id) {
              <li>
                <span class="device">
                  <span class="device-name">{{ d.label }}</span>
                  <span class="secondary-text">{{ texts.since(dateText(d.createdAt)) }}</span>
                </span>
                <button
                  type="button"
                  class="button button-secondary button-small"
                  [attr.aria-label]="texts.removeLabel(d.label)"
                  [disabled]="busy()"
                  (click)="remove(d)"
                >
                  {{ texts.remove }}
                </button>
              </li>
            }
          </ul>
        }
      } @else if (loading()) {
        <p class="secondary-text">{{ common.loading }}</p>
      }

      @if (message(); as m) {
        <p class="message-success" role="status">{{ m }}</p>
      }
      @if (error(); as m) {
        <p class="message-error" role="alert">{{ m }}</p>
      }
    </section>
  `,
  styles: `
    .channels {
      display: grid;
      gap: 12px;
    }
    .channels p,
    .channels h2,
    .channels h3 {
      margin: 0;
    }
    .channel {
      display: grid;
      gap: 2px;
    }
    .hint {
      padding-left: 34px;
    }
    .explanation {
      display: grid;
      gap: 6px;
    }
    .explanation-title {
      font-weight: 700;
    }
    .explanation ol {
      margin: 0;
      padding-left: 20px;
      display: grid;
      gap: 4px;
    }
    .subtitle {
      font-size: 15px;
      font-weight: 700;
    }
    .devices {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      gap: 8px;
    }
    .devices li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      flex-wrap: wrap;
    }
    .device {
      display: grid;
      min-width: 0;
    }
    .device-name {
      font-weight: 600;
      overflow-wrap: anywhere;
    }
  `,
})
export class NotificationSettingsSection {
  private readonly http = inject(HttpClient);
  protected readonly texts = TEXTS.notificationSettings;
  protected readonly common = TEXTS.common;
  protected readonly support: PushSupport = pushSupport();
  protected readonly deniedHowTo = isIos()
    ? this.texts.deniedIphone
    : isAndroid()
      ? this.texts.deniedAndroid
      : this.texts.deniedDesktop;

  protected readonly settings = signal<NotificationSettings | null>(null);
  /** Endpoint of the push subscription of this browser, if it has one. */
  protected readonly endpoint = signal<string | null>(null);
  protected readonly denied = signal(permission() === 'denied');
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Push is on for this device: its subscription is registered on the server. */
  protected readonly pushOn = computed(() => {
    const e = this.endpoint();
    return e !== null && (this.settings()?.devices.some((d) => d.endpoint === e) ?? false);
  });
  protected readonly pushPossible = computed(
    () =>
      (this.settings()?.pushAvailable ?? false) && this.support === 'supported' && !this.denied(),
  );
  protected readonly otherDevices = computed(
    () => this.settings()?.devices.filter((d) => d.endpoint !== this.endpoint()) ?? [],
  );

  constructor() {
    void this.load();
  }

  protected dateText(iso: string): string {
    return date(iso);
  }

  private async load(): Promise<void> {
    try {
      const [settings, subscription] = await Promise.all([
        firstValueFrom(this.http.get<NotificationSettings>('/api/me/notification-settings')),
        currentSubscription().catch(() => null),
      ]);
      this.endpoint.set(subscription?.endpoint ?? null);
      this.settings.set(settings);
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.loading.set(false);
    }
  }

  private start(): boolean {
    if (this.busy()) return false;
    this.busy.set(true);
    this.message.set(null);
    this.error.set(null);
    return true;
  }

  protected async setEmail(on: boolean): Promise<void> {
    if (!this.start()) return;
    try {
      this.settings.set(
        await firstValueFrom(
          this.http.put<NotificationSettings>('/api/me/notification-settings', { email: on }),
        ),
      );
      this.message.set(on ? this.texts.emailOn : this.texts.emailOff);
    } catch (e) {
      this.error.set(errorText(e));
      this.settings.update((s) => (s ? { ...s } : s));
    } finally {
      this.busy.set(false);
    }
  }

  protected async setPush(on: boolean): Promise<void> {
    if (!this.start()) return;
    try {
      if (on) {
        await this.pushOnForThisDevice();
      } else {
        await this.pushOffForThisDevice();
      }
    } finally {
      this.busy.set(false);
    }
  }

  private async pushOnForThisDevice(): Promise<void> {
    const key = this.settings()?.pushPublicKey;
    if (!key) return;
    // Ask for the browser permission (criterion 3); a refusal is explained, the switch stays off.
    const answer = permission() === 'granted' ? 'granted' : await Notification.requestPermission();
    if (answer !== 'granted') {
      this.denied.set(answer === 'denied');
      this.settings.update((s) => (s ? { ...s } : s));
      return;
    }
    try {
      const subscription = await subscribe(key);
      await firstValueFrom(this.http.post<PushDevice>('/api/me/push-subscriptions', subscription.toJSON()));
      this.endpoint.set(subscription.endpoint);
      await this.reload();
      this.message.set(this.texts.pushOn);
    } catch (e) {
      this.error.set(e instanceof DOMException ? this.texts.pushFailed : errorText(e));
      this.settings.update((s) => (s ? { ...s } : s));
    }
  }

  private async pushOffForThisDevice(): Promise<void> {
    const device = this.settings()?.devices.find((d) => d.endpoint === this.endpoint());
    try {
      if (device) {
        await firstValueFrom(this.http.delete<void>(`/api/me/push-subscriptions/${device.id}`));
      }
      const subscription = await currentSubscription().catch(() => null);
      await subscription?.unsubscribe().catch(() => false);
      this.endpoint.set(null);
      await this.reload();
      this.message.set(this.texts.pushOff);
    } catch (e) {
      this.error.set(errorText(e));
      this.settings.update((s) => (s ? { ...s } : s));
    }
  }

  protected async remove(device: PushDevice): Promise<void> {
    if (!this.start()) return;
    try {
      await firstValueFrom(this.http.delete<void>(`/api/me/push-subscriptions/${device.id}`));
      await this.reload();
      this.message.set(this.texts.removed(device.label));
    } catch (e) {
      this.error.set(errorText(e));
    } finally {
      this.busy.set(false);
    }
  }

  private async reload(): Promise<void> {
    this.settings.set(
      await firstValueFrom(this.http.get<NotificationSettings>('/api/me/notification-settings')),
    );
  }
}
