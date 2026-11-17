import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TEXTS } from '../../core/texts';

@Component({
  selector: 'app-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <h1>{{ texts.common.notFound }}</h1>
    <p>
      <a routerLink="/">{{ texts.common.toStartPage }}</a>
    </p>
  `,
  styleUrl: '../page.css',
})
export class NotFound {
  protected readonly texts = TEXTS;
}
