import { HttpClient } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import type { Kachel } from '../../api/typen';
import { AnmeldungService } from '../../kern/anmeldung.service';
import { fehlertext } from '../../kern/fehlertext';
import { Icon } from '../../kern/icon';
import { TEXTE } from '../../kern/texte';

/** Start: Begrüssung und Kacheln aus `GET /api/kacheln` (NF-11, NF-12, RR-05). */
@Component({
  selector: 'app-start',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  templateUrl: './start.html',
  styleUrl: './start.css',
})
export class Start implements OnInit {
  private readonly http = inject(HttpClient);
  protected readonly anmeldung = inject(AnmeldungService);
  protected readonly texte = TEXTE;

  protected readonly kacheln = signal<Kachel[]>([]);
  protected readonly laedt = signal(true);
  protected readonly fehler = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.kacheln.set(await firstValueFrom(this.http.get<Kachel[]>('/api/kacheln')));
    } catch (e) {
      this.fehler.set(fehlertext(e));
    } finally {
      this.laedt.set(false);
    }
  }
}
