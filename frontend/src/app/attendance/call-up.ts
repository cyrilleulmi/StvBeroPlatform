import { HttpClient } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { CallUp as CallUpData, PersonSummary, EventDetail } from '../api/types';
import { AuthService } from '../core/auth.service';
import { errorStatus, errorText } from '../core/error-text';
import { Icon } from '../core/icon';
import { TEXTS } from '../core/texts';
import { RESPONSE_ICON, responseKey } from './response';
import { yearOf, eventDate, clockTime } from './time';

/**
 * Aufgebot eines Spieltags (`/termine/:terminId/aufgebot`, nur Coaches und Admins): alle Spielerinnen des
 * Teams nach Linien mit Antwortstatus, Auswahl speichert sofort, Veröffentlichen mit Bestätigung
 * (AUF-21 bis AUF-25, AUF-28).
 */
@Component({
  selector: 'app-aufgebot',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="header">
      <a class="back" [routerLink]="['/termine', eventId()]">{{ t.toEvent }}</a>
      <h1>{{ t.title }}</h1>
      @if (event(); as tm) {
        <p class="secondary-text">
          {{ tm.title }} {{ date(tm.start) }} · {{ time(tm.start) }} · {{ teamName() }}
        </p>
      }
    </div>
    @if (message(); as m) {
      <p class="message-success" role="status">{{ m }}</p>
    }
    @if (error(); as m) {
      <p class="message-error" role="alert">{{ m }}</p>
    }
    <div class="sections" [attr.aria-busy]="loading() ? 'true' : null">
      @if (loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (callUp(); as a) {
        <section class="card state" aria-labelledby="aufgebot-stand-titel">
          <h2 id="aufgebot-stand-titel" class="section-title">{{ texts.matchDay.callUp }}</h2>
          <p class="status-row">
            <span class="chip" [attr.data-art]="a.status.toLowerCase()" data-testid="aufgebot-status">{{
              statusText(a)
            }}</span>
            <span class="count-text" data-testid="aufgebot-anzahl">{{ t.count(a.calledUpCount ?? 0) }}</span>
          </p>
          @if (!editable()) {
            <p class="message-notice">{{ t.notEditable }}</p>
          } @else if (a.status === 'PUBLISHED') {
            <p class="secondary-text">{{ t.publishedNotice }}</p>
          } @else {
            <p class="secondary-text">{{ t.draftNotice }}</p>
            <div>
              <button
                type="button"
                class="button button-primary"
                data-testid="aufgebot-veroeffentlichen"
                [disabled]="running() || (a.calledUpCount ?? 0) === 0"
                [attr.aria-describedby]="(a.calledUpCount ?? 0) === 0 ? 'aufgebot-leer' : null"
                (click)="openDialog()"
              >
                {{ t.publish }}
              </button>
              @if ((a.calledUpCount ?? 0) === 0) {
                <p class="secondary-text" id="aufgebot-leer">{{ t.empty }}</p>
              }
            </div>
          }
        </section>

        <section class="card selection" data-testid="aufgebot-auswahl" aria-labelledby="aufgebot-auswahl-titel">
          <h2 id="aufgebot-auswahl-titel" class="section-title">{{ t.selection }}</h2>
          <p class="secondary-text">{{ t.selectionHint }}</p>
          @for (g of a.groups; track g.groupId ?? 'ohne-linie') {
            <fieldset class="group">
              <legend>{{ g.name }}</legend>
              <ul class="people">
                @for (e of g.entries; track e.person.id) {
                  <li
                    class="person"
                    [attr.data-testid]="'aufgebot-person-' + e.person.id"
                    [attr.data-antwort]="e.response.status"
                    [attr.data-aufgeboten]="e.calledUp"
                  >
                    <label class="selection-row">
                      <input
                        type="checkbox"
                        [checked]="e.calledUp"
                        [disabled]="running() || !editable()"
                        [attr.aria-busy]="runningPerson() === e.person.id ? 'true' : null"
                        [attr.aria-describedby]="'aufgebot-antwort-' + e.person.id"
                        (change)="toggle(e.person, $any($event.target))"
                      />
                      <span class="person-name">{{ name(e.person) }}</span>
                    </label>
                    <span class="response" [id]="'aufgebot-antwort-' + e.person.id">
                      <span class="symbol" [attr.data-art]="key(e.response.status)" aria-hidden="true">
                        <app-icon [name]="icon[e.response.status]" [size]="14" />
                      </span>
                      <span class="status-text">{{ texts.response.status[e.response.status] }}</span>
                      @if (e.response.comment; as k) {
                        <span class="secondary-text comment">«{{ k }}»</span>
                      }
                    </span>
                  </li>
                }
              </ul>
            </fieldset>
          } @empty {
            <p class="empty">{{ t.noPlayers }}</p>
          }
        </section>
      }
    </div>

    <dialog
      #dialog
      class="card dialog"
      aria-modal="true"
      aria-labelledby="aufgebot-dialog-titel"
      aria-describedby="aufgebot-dialog-text"
    >
      <div class="form">
        <h2 id="aufgebot-dialog-titel">{{ t.dialogTitle }}</h2>
        <p id="aufgebot-dialog-text">{{ t.dialogText(callUp()?.calledUpCount ?? 0, teamName()) }}</p>
        <div class="dialog-buttons">
          <button
            type="button"
            class="button button-primary button-small"
            [disabled]="running()"
            [attr.aria-busy]="running() ? 'true' : null"
            (click)="publish()"
          >
            {{ t.publish }}
          </button>
          <button
            type="button"
            class="button button-secondary button-small"
            [disabled]="running()"
            (click)="closeDialog()"
          >
            {{ t.cancel }}
          </button>
        </div>
      </div>
    </dialog>
  `,
  styleUrls: ['./overview.css', './call-up.css'],
})
export class CallUpPage {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.callUp;
  protected readonly icon = RESPONSE_ICON;
  protected readonly key = responseKey;
  protected readonly time = clockTime;

  /** Route `/termine/:terminId/aufgebot` */
  readonly eventId = input.required<string>();

  protected readonly event = signal<EventDetail | null>(null);
  protected readonly callUp = signal<CallUpData | null>(null);
  protected readonly loading = signal(true);
  protected readonly running = signal(false);
  protected readonly runningPerson = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);

  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly year = computed(() => yearOf(this.auth.me()?.now));

  protected readonly teamName = computed(() => this.event()?.teams[0]?.name ?? '');

  /** Geplant und nicht vorbei (TER-06); `darfBearbeiten` gilt für Coaches und Admins genau dann. */
  protected readonly editable = computed(() => this.event()?.canEdit === true);

  constructor() {
    effect(() => {
      const id = this.eventId();
      untracked(() => void this.load(id));
    });
  }

  protected date(iso: string): string {
    return eventDate(iso, this.year());
  }

  protected name(p: PersonSummary): string {
    return `${p.firstName} ${p.lastName}`;
  }

  protected statusText(a: CallUpData): string {
    if (a.status === 'PUBLISHED' && a.publishedAt) {
      return this.t.publishedAt(this.date(a.publishedAt), clockTime(a.publishedAt));
    }
    return this.t.status[a.status];
  }

  /** Auswahl sofort speichern (AUF-21, AUF-22). Schlägt es fehl, bleibt die alte Auswahl. */
  protected async toggle(person: PersonSummary, field: HTMLInputElement): Promise<void> {
    const a = this.callUp();
    if (!a || this.running()) {
      field.checked = !field.checked;
      return;
    }
    const yes = field.checked;
    const ids = a.groups
      .flatMap((g) => g.entries)
      .filter((e) => (e.person.id === person.id ? yes : e.calledUp))
      .map((e) => e.person.id);
    this.running.set(true);
    this.runningPerson.set(person.id);
    this.message.set(null);
    this.error.set(null);
    try {
      const updated = await firstValueFrom(
        this.http.put<CallUpData>(`/api/events/${this.eventId()}/call-up`, {
          personIds: [...new Set(ids)],
        }),
      );
      this.callUp.set(updated);
      this.message.set(this.t.saved(this.name(person), yes));
    } catch (e) {
      field.checked = !yes;
      this.error.set(errorText(e));
    } finally {
      this.running.set(false);
      this.runningPerson.set(null);
    }
  }

  protected openDialog(): void {
    const d = this.dialog()?.nativeElement;
    if (d && !d.open) d.showModal();
  }

  protected closeDialog(): void {
    this.dialog()?.nativeElement.close();
  }

  /** Veröffentlichen nach Bestätigung (AUF-23, AUF-24). */
  protected async publish(): Promise<void> {
    if (this.running()) return;
    this.running.set(true);
    this.message.set(null);
    this.error.set(null);
    try {
      const updated = await firstValueFrom(
        this.http.post<CallUpData>(`/api/events/${this.eventId()}/call-up/publish`, null),
      );
      this.callUp.set(updated);
      this.closeDialog();
      this.message.set(this.t.published);
    } catch (e) {
      this.closeDialog();
      this.error.set(errorText(e));
    } finally {
      this.running.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.event.set(null);
    this.callUp.set(null);
    try {
      const event = await firstValueFrom(this.http.get<EventDetail>(`/api/events/${id}`));
      this.event.set(event);
      // Rechte und Art zuerst aus dem Termin: so entsteht ohne Berechtigung keine 403-Anfrage.
      if (event.type !== 'MATCH_DAY') {
        this.error.set(this.t.matchDayOnly);
        return;
      }
      const coach = event.teams.some((t) => t.myTeamRole === 'COACH');
      if (!coach && !this.auth.isAdmin()) {
        this.error.set(TEXTS.common.noPermission);
        return;
      }
      this.callUp.set(
        await firstValueFrom(this.http.get<CallUpData>(`/api/events/${id}/call-up`)),
      );
    } catch (e) {
      const status = errorStatus(e);
      if (status === 404 || status === 400) {
        this.error.set(TEXTS.events.notFound);
      } else if (status === 409) {
        this.error.set(this.t.matchDayOnly);
      } else {
        this.error.set(errorText(e));
      }
    } finally {
      this.loading.set(false);
    }
  }
}
