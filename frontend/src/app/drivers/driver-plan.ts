import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import type { Car, DriverPlanMatchDay } from '../api/types';
import { AuthService } from '../core/auth.service';
import { name } from '../core/format';
import { Icon } from '../core/icon';
import { formatDecimal, formatWhole } from '../core/number';
import { TEXTS } from '../core/texts';
import { ConfirmDialog } from '../core/confirm-dialog';
import { Fairness } from './simple/fairness';
import { PlanStore, QueueEntry } from './simple/plan-store';
import { SeatBoard } from './simple/seat-board';
import { Stepper } from './simple/stepper';

/**
 * Driver list of a team (`/teams/:teamId/fahrerliste`): the season as a grid, people down and match days across
 * (E-96). The rows show the places of each car, the columns the distance, the column on the right how often and how
 * far each person drives (DRV-17). A tap on a cell says «drives» or «does not drive»; a driver with passengers is
 * taken off only after a question. «Fair verteilen» shows a proposal for the trips that are missing, which is entered
 * with «Vorschlag übernehmen» and then stays (DRV-30); «Alle entfernen» takes every planned driver off (DRV-33). A tap
 * on a match day opens its cars below (DRV-03, DRV-32). `?saison=<id>` shows another season.
 */
@Component({
  selector: 'app-driver-plan',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon, Fairness, SeatBoard, Stepper, ConfirmDialog],
  providers: [PlanStore],
  template: `
    <div class="header">
      <a class="back" [routerLink]="['/teams', teamId()]">{{ store.plan()?.teamName ?? texts.teams.title }}</a>
      <h1 class="page-title">{{ t.title }}</h1>
      @if (store.plan(); as d) {
        <p class="secondary-text" data-testid="driver-plan-season">{{ t.seasonOf(d.teamName, d.seasonName) }}</p>
      }
      @if (myTeams().length > 1) {
        <div class="field team-switch">
          <label for="plan-team">{{ t.team }}</label>
          <select id="plan-team" name="plan-team" (change)="switchTeam($any($event.target).value)">
            @for (x of myTeams(); track x.id) {
              <option [value]="x.id" [selected]="x.id === teamId()">{{ x.name }}</option>
            }
          </select>
        </div>
      }
    </div>
    @if (store.success(); as m) {
      <p class="message-success" role="status">{{ m }}</p>
    }
    @if (store.error(); as m) {
      <p class="message-error" role="alert">{{ m }}</p>
    }
    <div class="sections" [attr.aria-busy]="store.loading() ? 'true' : null">
      @if (store.loading()) {
        <p class="empty">{{ texts.common.loading }}</p>
      } @else if (store.plan(); as d) {
        <section class="card grid-card" aria-labelledby="grid-title" data-testid="driver-plan-match-days">
          <div class="grid-head">
            <h2 id="grid-title" class="section-title">{{ b.gridTitle }}</h2>
            @if (store.past().length > 0) {
              <label class="check">
                <input type="checkbox" [checked]="showPast()" (change)="showPast.set($any($event.target).checked)" />
                {{ b.showPast }}
              </label>
            }
          </div>
          @if (d.canManage) {
            <!-- Planning the season: one number and the proposal (DRV-29, DRV-30, DRV-33) -->
            <div class="planning" data-testid="planning">
              <span class="expected">
                <span class="expected-label">{{ t.expected }}</span>
                <app-stepper
                  [value]="d.expected"
                  [label]="t.expected"
                  [disabled]="store.busy()"
                  testId="expected"
                  (changed)="store.setExpected($event)"
                />
                @if (d.expectedManual) {
                  <button type="button" class="link" [disabled]="store.busy()" (click)="store.setExpected(null)">
                    {{ b.allAgain(d.expectedDefault) }}
                  </button>
                }
              </span>
              @if (store.proposal() === null) {
                <span class="plan-buttons">
                  <button
                    type="button"
                    class="button button-primary button-small"
                    [disabled]="store.busy() || store.short().length === 0"
                    [attr.aria-busy]="store.running() === 'distribute' ? 'true' : null"
                    data-testid="distribute"
                    (click)="store.preview()"
                  >
                    {{ t.distribute }}
                  </button>
                  @if (hasPlanned()) {
                    <button
                      type="button"
                      class="button button-secondary button-small button-destructive"
                      [disabled]="store.busy()"
                      [attr.aria-busy]="store.running() === 'remove-all' ? 'true' : null"
                      data-testid="remove-all"
                      (click)="askRemoveAll()"
                    >
                      {{ t.removeAll }}
                    </button>
                  }
                </span>
              }
            </div>
            @if (store.proposal() !== null) {
              <section class="proposal" aria-labelledby="proposal-title" data-testid="distribution-proposal">
                <div>
                  <h3 id="proposal-title">
                    <span class="toggle proposed small" aria-hidden="true"><app-icon name="car" [size]="12" /></span>
                    {{ t.proposalTitle }}
                  </h3>
                  <p>{{ t.proposalText(store.proposed().length, proposedDays()) }}</p>
                </div>
                <span class="plan-buttons">
                  <button
                    type="button"
                    class="button button-primary button-small"
                    data-testid="distribute-accept"
                    [disabled]="store.busy() || store.proposed().length === 0"
                    [attr.aria-busy]="store.running() === 'accept' ? 'true' : null"
                    (click)="store.acceptProposal()"
                  >
                    {{ t.proposalAccept }}
                  </button>
                  <button
                    type="button"
                    class="button button-secondary button-small"
                    data-testid="distribute-discard"
                    [disabled]="store.busy()"
                    (click)="store.discardProposal()"
                  >
                    {{ t.proposalDiscard }}
                  </button>
                </span>
              </section>
            }
          }
          <p class="secondary-text">{{ d.canManage ? b.hintCoach : d.canJoin ? b.hintMember : b.hintRead }}</p>

          @if (columns().length === 0) {
            <p class="empty">{{ t.noMatchDays }}</p>
          } @else {
            <div class="scroll" tabindex="0" role="region" [attr.aria-label]="b.gridTitle">
              <table class="grid" data-testid="plan-grid">
                <thead>
                  <tr>
                    <th scope="col" class="corner">{{ b.who }}</th>
                    @for (m of columns(); track m.eventId) {
                      <th
                        scope="col"
                        class="day"
                        [attr.data-state]="m.over ? 'over' : store.state(m)"
                        [attr.data-selected]="selectedId() === m.eventId ? 'true' : null"
                      >
                        <button
                          type="button"
                          class="day-button"
                          [attr.data-testid]="'plan-day-' + m.eventId"
                          [attr.aria-pressed]="selectedId() === m.eventId"
                          [attr.title]="store.dayTime(m) + ', ' + store.venue(m)"
                          (click)="select(m)"
                        >
                          <span class="day-date">{{ shortDay(m) }}</span>
                          <span class="day-place">{{ m.noTrip ? b.home : (m.town ?? m.venue ?? '') }}</span>
                          <span class="day-km" [attr.data-testid]="'plan-day-km-' + m.eventId">{{
                            m.noTrip || !has(m.distanceKm) ? '–' : b.dayKm(km(m.distanceKm))
                          }}</span>
                          <span class="visually-hidden">{{ b.openDay }}</span>
                        </button>
                      </th>
                    }
                    <th scope="col" class="total total-head">
                      @if (store.proposed().length > 0) {
                        <span class="total-with">{{ b.withProposal }}</span>
                      }
                      <span>{{ b.trips }}</span>
                      <span class="total-km">{{ b.km }}</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  @for (q of rows(); track q.person.id) {
                    <tr
                      [attr.data-testid]="'plan-row-' + q.person.id"
                      [attr.data-me]="q.person.id === store.meId() ? 'true' : null"
                      [attr.data-can-drive]="q.canDrive"
                    >
                      <th scope="row" class="person">
                        <span class="person-name">{{ store.shortName(q.person) }}</span>
                        @if (q.canDrive) {
                          <span class="car-seats" [attr.title]="b.carSeats(q.carSeats)" data-testid="plan-car-seats">
                            <app-icon name="armchair" [size]="13" />
                            <span class="visually-hidden">{{ b.carSeats(q.carSeats) }}</span>
                            <span aria-hidden="true">{{ q.carSeats }}</span>
                          </span>
                        } @else {
                          <span class="car-seats none-seats" data-testid="plan-car-seats">{{ b.cannotDrive }}</span>
                        }
                      </th>
                      @for (m of columns(); track m.eventId) {
                        <td
                          class="cell"
                          [attr.data-selected]="selectedId() === m.eventId ? 'true' : null"
                          [attr.data-state]="m.over ? 'over' : null"
                        >
                          @if (m.noTrip || m.cancelled) {
                            <span class="none" aria-hidden="true">·</span>
                          } @else if (carOf(m, q.person.id); as car) {
                            @if (canToggle(m, q)) {
                              <button
                                type="button"
                                class="toggle on"
                                aria-pressed="true"
                                [attr.aria-label]="b.drives(name(q.person), store.day(m))"
                                [attr.data-testid]="'cell-' + q.person.id + '-' + m.eventId"
                                [attr.data-riders]="car.passengers.length > 0 ? car.passengers.length : null"
                                [disabled]="store.busy()"
                                (click)="off(m, car)"
                              >
                                <app-icon name="car" [size]="18" />
                              </button>
                            } @else {
                              <span class="toggle on fixed" [attr.aria-label]="b.drives(name(q.person), store.day(m))" role="img">
                                <app-icon name="car" [size]="18" />
                              </span>
                            }
                          } @else if (isProposed(m, q.person.id)) {
                            <span
                              class="toggle proposed"
                              role="img"
                              [attr.aria-label]="t.proposedCell(name(q.person), store.day(m))"
                              [attr.data-testid]="'proposal-' + q.person.id + '-' + m.eventId"
                            >
                              <app-icon name="car" [size]="18" />
                            </span>
                          } @else if (q.canDrive && canToggle(m, q)) {
                            <button
                              type="button"
                              class="toggle"
                              aria-pressed="false"
                              [attr.aria-label]="b.drivesNot(name(q.person), store.day(m))"
                              [attr.data-testid]="'cell-' + q.person.id + '-' + m.eventId"
                              [disabled]="store.busy()"
                              (click)="store.addDriver(m, q.person)"
                            ></button>
                          } @else {
                            <span class="none" aria-hidden="true"></span>
                          }
                        </td>
                      }
                      <td
                        class="total"
                        data-testid="plan-total"
                        [attr.data-trips]="q.trips"
                        [attr.data-trips-proposed]="withProposal(q).more > 0 ? withProposal(q).trips : null"
                        [attr.data-changed]="withProposal(q).more > 0 ? 'true' : null"
                        [attr.aria-label]="
                          withProposal(q).more > 0
                            ? b.tripsWithProposal(
                                name(q.person),
                                withProposal(q).trips,
                                km(withProposal(q).km),
                                q.trips,
                                km(q.kilometres)
                              )
                            : b.tripsOf(name(q.person), q.trips, km(q.kilometres))
                        "
                      >
                        @if (withProposal(q).more > 0) {
                          <span class="total-trips">{{ withProposal(q).trips }}</span>
                          <span class="total-km">{{ km(withProposal(q).km) }}</span>
                          <span class="total-before" aria-hidden="true">{{ b.before(q.trips, km(q.kilometres)) }}</span>
                        } @else {
                          <span class="total-trips">{{ q.trips }}</span>
                          <span class="total-km">{{ km(q.kilometres) }}</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
                <tfoot>
                  <tr data-testid="plan-places-row">
                    <th scope="row" class="person">{{ b.places }}</th>
                    @for (m of columns(); track m.eventId) {
                      <td
                        class="sum"
                        [attr.data-state]="m.over ? 'over' : store.stateWithProposal(m)"
                        [attr.data-selected]="selectedId() === m.eventId ? 'true' : null"
                        [attr.data-testid]="'plan-places-' + m.eventId"
                      >
                        @if (m.noTrip || m.cancelled) {
                          <span aria-hidden="true">–</span>
                        } @else {
                          <span class="sum-places">{{ store.placesWithProposal(m) }}</span>
                          <span class="sum-of">{{ b.of(m.over ? m.attending : m.expected) }}</span>
                        }
                      </td>
                    }
                    <td
                      class="total"
                      data-testid="plan-total-sum"
                      [attr.data-changed]="store.proposed().length > 0 ? 'true' : null"
                      [attr.aria-label]="
                        store.proposed().length > 0
                          ? b.tripsWithProposal(
                              b.total,
                              totalTrips() + store.proposed().length,
                              km(totalKm() + proposedKmSum()),
                              totalTrips(),
                              km(totalKm())
                            )
                          : b.tripsOf(b.total, totalTrips(), km(totalKm()))
                      "
                    >
                      @if (store.proposed().length > 0) {
                        <span class="total-trips">{{ totalTrips() + store.proposed().length }}</span>
                        <span class="total-km">{{ km(totalKm() + proposedKmSum()) }}</span>
                        <span class="total-before" aria-hidden="true">{{ b.before(totalTrips(), km(totalKm())) }}</span>
                      } @else {
                        <span class="total-trips">{{ totalTrips() }}</span>
                        <span class="total-km">{{ km(totalKm()) }}</span>
                      }
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
            <p class="legend secondary-text">
              <span class="toggle on fixed small" aria-hidden="true"><app-icon name="car" [size]="12" /></span> {{ b.legendDrives }}
              @if (store.proposal() !== null) {
                <span class="toggle proposed small" aria-hidden="true"><app-icon name="car" [size]="12" /></span>
                {{ b.legendProposed }}
              }
              <span class="swatch enough" aria-hidden="true"></span> {{ b.legendEnough }}
              <span class="swatch short" aria-hidden="true"></span> {{ b.legendShort }}
            </p>
          }
        </section>

        @if (selected(); as m) {
          <!-- The cars of the chosen match day: seat people right before the match (DRV-03, DRV-19, DRV-32) -->
          <section class="card" aria-labelledby="day-title" [attr.data-testid]="'plan-editor-' + m.eventId">
            <div class="day-head">
              <div>
                <p class="eyebrow">{{ m.eventId === store.nextMatchDay()?.eventId ? b.nextDay : b.matchDay }}</p>
                <h2 id="day-title" class="section-title">{{ store.dayTime(m) }}</h2>
                <p class="secondary-text">
                  {{ store.venue(m) }}
                  @if (has(m.distanceKm) && !m.noTrip) {
                    · {{ t.distance(km(m.distanceKm)) }}
                  }
                </p>
              </div>
              <a class="to-event" [routerLink]="['/termine', m.eventId]">{{ t.openMatchDay }}</a>
            </div>
            @if (m.cancelled) {
              <p class="empty">{{ t.cancelled }}</p>
            } @else if (m.noTrip) {
              <p class="empty">{{ t.home }}</p>
            } @else if (!m.ready) {
              <p class="empty">{{ t.notReady }}</p>
            } @else {
              @if (d.canManage && !m.over) {
                <div class="expected">
                  <span class="expected-label">{{ b.expectedHere }}</span>
                  <app-stepper
                    [value]="m.expected"
                    [label]="t.expectedOf(store.day(m))"
                    [disabled]="store.busy()"
                    (changed)="store.setMatchDayExpected(m.eventId, $event)"
                  />
                  @if (m.expectedManual) {
                    <button type="button" class="link" [disabled]="store.busy()" (click)="store.setMatchDayExpected(m.eventId, null)">
                      {{ b.likeTeam(d.expected) }}
                    </button>
                  }
                  @if (m.attending > 0) {
                    <span class="secondary-text">{{ b.attendingNow(m.attending) }}</span>
                  }
                </div>
              }
              <app-seat-board [eventId]="m.eventId" />
            }
          </section>
        }

        <section class="card" data-testid="driver-plan-shares">
          <app-fairness [title]="b.settingsTitle" [text]="b.settingsText" />
        </section>
      }
    </div>
    <app-confirm-dialog testId="plan-confirm" />
  `,
  styles: `
    :host {
      display: grid;
      gap: 16px;
      min-width: 0;
    }
    .sections {
      display: grid;
      gap: 16px;
      min-width: 0;
    }
    p,
    h3 {
      margin: 0;
    }
    .card {
      display: grid;
      gap: 12px;
      min-width: 0;
    }
    .team-switch {
      max-width: 320px;
    }
    select {
      min-height: var(--touch);
      padding: 0 12px;
      border: 1.5px solid var(--control-border);
      border-radius: var(--radius-icon);
      background: var(--surface);
      color: var(--text);
      font: 600 15px/1.2 var(--font);
      max-width: 100%;
    }
    .grid-head,
    .day-head {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-start;
      justify-content: space-between;
      gap: 8px 16px;
    }
    .grid-head .section-title,
    .day-head .section-title {
      margin: 0;
    }
    .check {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      min-height: var(--touch);
      font-weight: 600;
      font-size: 14px;
    }
    .check input {
      width: 20px;
      height: 20px;
    }
    .planning,
    .proposal {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 10px 20px;
      padding: 12px 14px;
      border-radius: var(--radius-control);
      background: var(--board);
      border: 1.5px solid var(--board-border);
    }
    .proposal {
      background: var(--primary-soft);
      border: 1.5px dashed var(--primary);
    }
    .proposal > div {
      display: grid;
      gap: 4px;
      flex: 1 1 320px;
    }
    .proposal h3 {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      font: 800 16px/1.3 var(--font);
    }
    .expected,
    .plan-buttons {
      display: inline-flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 8px 12px;
    }
    .expected-label {
      font-weight: 700;
    }
    .link {
      border: 0;
      padding: 0;
      background: none;
      color: var(--primary);
      font: 600 14px/1.3 var(--font);
      text-decoration: underline;
      cursor: pointer;
      min-height: 32px;
    }
    .scroll {
      overflow-x: auto;
      min-width: 0;
      border: 1.5px solid var(--border);
      border-radius: var(--radius-control);
    }
    .grid {
      border-collapse: separate;
      border-spacing: 0;
      width: 100%;
    }
    th,
    td {
      padding: 0;
      border-bottom: 1px solid var(--border);
      text-align: center;
      vertical-align: middle;
    }
    .corner,
    .person {
      position: sticky;
      left: 0;
      z-index: 1;
      background: var(--surface);
      text-align: left;
      padding: 0 10px 0 12px;
      border-right: 1.5px solid var(--border);
      white-space: nowrap;
    }
    .corner {
      font-size: 13px;
      color: var(--muted);
      vertical-align: bottom;
      padding-bottom: 8px;
    }
    .person {
      font-weight: 600;
      font-size: 14px;
    }
    tr[data-me='true'] .person-name {
      font-weight: 800;
    }
    tr[data-me='true'] .person,
    tr[data-me='true'] .cell,
    tr[data-me='true'] .total {
      background: var(--primary-soft);
    }
    tr[data-can-drive='false'] .person-name {
      color: var(--muted);
    }
    .person-name {
      display: inline-block;
      min-width: 74px;
    }
    .car-seats {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      min-width: 24px;
      height: 24px;
      padding: 0 6px;
      border-radius: var(--radius-chip);
      background: var(--tag-grey-soft);
      font-size: 13px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      color: var(--text);
    }
    .none-seats {
      background: transparent;
      padding: 0;
      font-weight: 500;
      font-size: 12px;
      color: var(--muted);
    }
    .total {
      position: sticky;
      right: 0;
      z-index: 1;
      min-width: 64px;
      padding: 0 10px;
      background: var(--surface);
      border-left: 1.5px solid var(--border);
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .total-head {
      font-size: 13px;
      color: var(--muted);
      vertical-align: bottom;
      padding-bottom: 8px;
    }
    .total > span {
      display: block;
    }
    .total-trips {
      font-weight: 800;
      font-size: 15px;
    }
    .total-km {
      font-size: 12px;
      color: var(--muted);
    }
    .total[data-changed='true'] .total-trips,
    .total[data-changed='true'] .total-km,
    .total-with {
      color: var(--primary);
    }
    .total-with {
      font-size: 11px;
      font-weight: 700;
    }
    .total-before {
      font-size: 11px;
      color: var(--muted);
    }
    @media (max-width: 520px) {
      .corner,
      .person {
        padding: 0 6px 0 8px;
        font-size: 13px;
      }
      .person-name {
        min-width: 60px;
      }
      .day {
        min-width: 58px;
      }
      .toggle {
        width: 40px;
        height: 38px;
      }
      .total {
        min-width: 52px;
        padding: 0 6px;
      }
    }
    .day {
      min-width: 66px;
    }
    .day-button {
      display: grid;
      gap: 2px;
      justify-items: center;
      width: 100%;
      min-height: 64px;
      padding: 6px 4px;
      border: 0;
      background: transparent;
      color: var(--text);
      font: inherit;
      cursor: pointer;
    }
    .day-date {
      font-weight: 800;
      font-size: 14px;
      white-space: nowrap;
    }
    .day-place {
      max-width: 84px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 12px;
      font-weight: 500;
      color: var(--muted);
    }
    .day-km {
      font-size: 12px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .day[data-state='over'] .day-date {
      color: var(--muted);
      font-weight: 600;
    }
    [data-selected='true'] {
      box-shadow:
        inset 2px 0 0 var(--primary),
        inset -2px 0 0 var(--primary);
    }
    .day[data-selected='true'] {
      background: var(--primary-soft);
      box-shadow:
        inset 2px 0 0 var(--primary),
        inset -2px 0 0 var(--primary),
        inset 0 2px 0 var(--primary);
    }
    .sum[data-selected='true'] {
      box-shadow:
        inset 2px 0 0 var(--primary),
        inset -2px 0 0 var(--primary),
        inset 0 -2px 0 var(--primary);
    }
    .cell {
      height: 48px;
    }
    .cell[data-state='over'] {
      opacity: 0.6;
    }
    .toggle {
      position: relative;
      display: inline-grid;
      place-items: center;
      width: 44px;
      height: 40px;
      border-radius: var(--radius-icon);
      border: 1.5px dashed var(--border);
      background: transparent;
      color: var(--on-primary);
      cursor: pointer;
      vertical-align: middle;
    }
    .toggle.on {
      border: 0;
      background: var(--primary);
    }
    .toggle.fixed {
      cursor: default;
    }
    /* Proposed by «Fair verteilen», not entered yet (DRV-30) */
    .toggle.proposed {
      cursor: default;
      border: 2px dashed var(--primary);
      background: var(--primary-soft);
      color: var(--primary);
    }
    .toggle.small {
      width: 22px;
      height: 20px;
      border-radius: 5px;
    }
    .toggle.proposed.small {
      border-width: 1.5px;
    }
    /* People in the car: drawn, not text, so the name of the button stays «… fährt am …» */
    .toggle[data-riders]::after {
      content: attr(data-riders);
      position: absolute;
      top: -6px;
      right: -6px;
      min-width: 18px;
      height: 18px;
      border-radius: 9px;
      background: var(--surface);
      border: 1.5px solid var(--primary);
      color: var(--text);
      font: 700 11px/15px var(--font);
    }
    .none {
      color: var(--muted);
    }
    tfoot th,
    tfoot td {
      border-bottom: 0;
      border-top: 1.5px solid var(--border);
      height: 46px;
    }
    .sum {
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .sum-places {
      font-weight: 800;
      font-size: 16px;
    }
    .sum-of {
      font-size: 12px;
      color: var(--muted);
    }
    .sum[data-state='enough'] {
      background: var(--tag-green-soft);
    }
    .sum[data-state='enough'] .sum-places {
      color: var(--tag-green);
    }
    .sum[data-state='short'] {
      background: var(--coral-soft);
    }
    .sum[data-state='short'] .sum-places {
      color: var(--coral);
    }
    .legend {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 8px;
    }
    .swatch {
      display: inline-block;
      width: 16px;
      height: 16px;
      border-radius: 4px;
      margin-left: 8px;
    }
    .swatch.enough {
      background: var(--tag-green-soft);
      border: 1.5px solid var(--tag-green);
    }
    .swatch.short {
      background: var(--coral-soft);
      border: 1.5px solid var(--coral);
    }
    .to-event {
      display: inline-flex;
      align-items: center;
      min-height: var(--touch);
      font-weight: 700;
      font-size: 14px;
      color: var(--primary);
    }
  `,
})
export class DriverPlanPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly store = inject(PlanStore);
  protected readonly texts = TEXTS;
  protected readonly t = TEXTS.simplePlan;
  protected readonly b = TEXTS.planB;
  protected readonly name = name;
  protected readonly has = (v: number | null | undefined): v is number => v !== null && v !== undefined;
  private readonly confirm = viewChild(ConfirmDialog);

  /** Route `/teams/:teamId/fahrerliste` */
  readonly teamId = input.required<string>();
  /** Query parameter `?saison=<id>`; without it the current season. */
  readonly saison = input<string | undefined>(undefined);

  /** Past match days are columns too. */
  protected readonly showPast = signal(false);
  /** The match day whose cars are shown; without a choice the next one. */
  private readonly chosen = signal<string | null>(null);

  /** Own teams of the current season, for the choice «Team». */
  protected readonly myTeams = computed(() => {
    const seen = new Map<string, { id: string; name: string }>();
    for (const m of this.auth.me()?.memberships ?? []) seen.set(m.team.id, m.team);
    return [...seen.values()];
  });

  protected readonly columns = computed(() =>
    this.showPast() || this.store.upcoming().length === 0 ? this.store.matchDays() : this.store.upcoming(),
  );

  /** Everyone of the team by name; who cannot drive comes last. */
  protected readonly rows = computed<QueueEntry[]>(() => {
    const byId = new Map(this.store.queue().map((q) => [q.person.id, q]));
    const all = (this.store.plan()?.drivers ?? []).map((d) => byId.get(d.person.id)!).filter(Boolean);
    return [...all.filter((q) => q.canDrive), ...all.filter((q) => !q.canDrive)];
  });

  protected readonly totalTrips = computed(() => this.store.queue().reduce((sum, q) => sum + q.trips, 0));
  protected readonly totalKm = computed(() => this.store.queue().reduce((sum, q) => sum + q.kilometres, 0));

  protected readonly selectedId = computed(() => {
    const chosen = this.chosen();
    if (chosen && this.columns().some((m) => m.eventId === chosen)) return chosen;
    return this.store.nextMatchDay()?.eventId ?? null;
  });

  protected readonly selected = computed(
    () => this.store.matchDays().find((m) => m.eventId === this.selectedId()) ?? null,
  );

  /** Upcoming match days with planned drivers: what «Alle entfernen» takes off. */
  private readonly planned = computed(() =>
    this.store.upcoming().filter((m) => !m.cancelled && m.cars.length > 0),
  );
  protected readonly hasPlanned = computed(() => this.planned().length > 0);

  protected readonly proposedDays = computed(() => new Set(this.store.proposed().map((t) => t.eventId)).size);

  /**
   * Proposed trips and kilometres per person, for the column on the right: what the totals would be once the
   * proposal is accepted. A trip gets the credit of its match day, like an entered one (DRV-05).
   */
  private readonly proposedPerPerson = computed(() => {
    const days = new Map(this.store.matchDays().map((m) => [m.eventId, m]));
    const per = new Map<string, { trips: number; km: number }>();
    for (const t of this.store.proposed()) {
      const entry = per.get(t.driver.id) ?? { trips: 0, km: 0 };
      entry.trips += 1;
      entry.km += days.get(t.eventId)?.creditKm ?? 0;
      per.set(t.driver.id, entry);
    }
    return per;
  });

  protected readonly proposedKmSum = computed(() =>
    [...this.proposedPerPerson().values()].reduce((sum, p) => sum + p.km, 0),
  );

  /** Trips and kilometres of a person with the proposal; `more` is the number of proposed trips. */
  protected withProposal(q: QueueEntry): { trips: number; km: number; more: number } {
    const p = this.proposedPerPerson().get(q.person.id);
    if (!p) return { trips: q.trips, km: q.kilometres, more: 0 };
    return { trips: q.trips + p.trips, km: Math.round((q.kilometres + p.km) * 10) / 10, more: p.trips };
  }

  constructor() {
    effect(() => {
      const team = this.teamId();
      const season = this.saison();
      untracked(() => {
        this.chosen.set(null);
        this.store.proposal.set(null);
        void this.store.load(team, season);
      });
    });
  }

  /** «23.4» kilometres, whole numbers from 100 on so the column stays narrow. */
  protected km(value: number | null | undefined): string {
    const v = value ?? 0;
    if (v === 0) return '0';
    return v >= 100 ? formatWhole(v) : formatDecimal(v);
  }

  protected switchTeam(team: string): void {
    void this.router.navigate(['/teams', team, 'fahrerliste']);
  }

  /** «So 10.01.» without the year: the columns stay narrow. */
  protected shortDay(m: DriverPlanMatchDay): string {
    return this.store.day(m).replace(/\.\d{4}$/, '.');
  }

  protected select(m: DriverPlanMatchDay): void {
    this.chosen.set(m.eventId);
  }

  protected carOf(m: DriverPlanMatchDay, personId: string): Car | null {
    return m.cars.find((car) => car.driver.id === personId) ?? null;
  }

  protected isProposed(m: DriverPlanMatchDay, personId: string): boolean {
    return this.store.proposedOn(m.eventId).some((t) => t.driver.id === personId);
  }

  /** Coaches and admins change every row, members their own (DRV-02). */
  protected canToggle(m: DriverPlanMatchDay, q: QueueEntry): boolean {
    return m.changeable && (this.store.canManage() || q.person.id === this.store.meId());
  }

  /** Takes a driver off; a car with passengers only after a question (DRV-02). */
  protected off(m: DriverPlanMatchDay, car: Car): void {
    if (car.passengers.length === 0) {
      void this.store.removeCar(m, car.id, car.driver);
      return;
    }
    const board = TEXTS.simplePlan.board;
    this.confirm()?.ask({
      title: board.removeTitle(name(car.driver)),
      text: board.removeText(name(car.driver), car.passengers.length),
      confirm: board.removeConfirm,
      action: () => this.store.removeCar(m, car.id, car.driver),
    });
  }

  /** «Alle entfernen» after a question (DRV-33). */
  protected askRemoveAll(): void {
    const days = this.planned();
    const trips = days.reduce((sum, m) => sum + m.cars.length, 0);
    this.confirm()?.ask({
      title: this.t.removeAllTitle,
      text: this.t.removeAllText(trips, days.length),
      confirm: this.t.removeAll,
      action: () => this.store.removeAll(),
    });
  }
}
