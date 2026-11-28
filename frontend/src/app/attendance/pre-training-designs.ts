import {
  ApplicationRef,
  ComponentRef,
  EnvironmentInjector,
  Injectable,
  createComponent,
  inject,
  signal,
} from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';
import { environment } from '../../environments/environment';
import { Design, isDesign } from './pre-training-model';
import { PreTrainingSwitch } from './pre-training-switch';

const PARAM = 'vortraining';
const STORAGE_KEY = 'stv-pre-training-design';

/**
 * PROTOTYPE (E-118, O-77): which design proposal shows the pre-training. The choice comes from `?vortraining=<a…e>`
 * and stays while moving through the app (session storage); without a choice proposal A shows. In test mode a
 * switch bar floats above the page as long as a pre-training is on it. Once Cyrille has chosen, this service, the
 * bar and the other proposals go.
 */
@Injectable({ providedIn: 'root' })
export class PreTrainingDesigns {
  private readonly router = inject(Router);
  private readonly app = inject(ApplicationRef);
  private readonly injector = inject(EnvironmentInjector);

  readonly design = signal<Design>(stored() ?? 'a');

  /** Pre-trainings shown on the page right now; the bar exists while there is one. */
  private shown = 0;
  private bar: ComponentRef<PreTrainingSwitch> | null = null;

  constructor() {
    this.read();
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.read());
  }

  /** The proposal is in the URL, so that a link shows the same design to someone else. */
  choose(design: Design): void {
    this.apply(design);
    void this.router.navigate([], {
      queryParams: { [PARAM]: design },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  /** A pre-training came onto the page. Returns what takes it off again. */
  show(): () => void {
    this.shown++;
    this.syncBar();
    return () => {
      this.shown--;
      // Later, so that moving from one page with a pre-training to the next does not rebuild the bar
      setTimeout(() => this.syncBar());
    };
  }

  private read(): void {
    const value = this.router.parseUrl(this.router.url).queryParamMap.get(PARAM);
    if (isDesign(value) && value !== this.design()) this.apply(value);
  }

  private apply(design: Design): void {
    this.design.set(design);
    store(design);
    this.bar?.setInput('design', design);
  }

  /** The bar lives outside the cards (they are links), directly in the document. */
  private syncBar(): void {
    if (!environment.testMode) return;
    if (this.shown > 0 && !this.bar) {
      this.bar = createComponent(PreTrainingSwitch, { environmentInjector: this.injector });
      this.bar.setInput('design', this.design());
      this.bar.instance.chosen.subscribe((design) => this.choose(design));
      this.app.attachView(this.bar.hostView);
      document.body.appendChild(this.bar.location.nativeElement);
    } else if (this.shown <= 0 && this.bar) {
      this.app.detachView(this.bar.hostView);
      this.bar.destroy();
      this.bar = null;
    }
  }
}

function stored(): Design | null {
  try {
    const value = sessionStorage.getItem(STORAGE_KEY);
    return isDesign(value) ? value : null;
  } catch {
    return null;
  }
}

function store(design: Design): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, design);
  } catch {
    // No storage (private mode): the choice lasts as long as the page
  }
}
