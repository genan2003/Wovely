import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** What to show when a list has nothing in it: says why, and what to do next (projected action). */
@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <i class="bi" [class]="icon()" aria-hidden="true"></i>
    <h2>{{ title() }}</h2>
    @if (message()) {
      <p>{{ message() }}</p>
    }
    <ng-content />
  `,
  styles: `
    :host {
      display: block;
      padding: var(--wv-space-7) var(--wv-space-4);
      text-align: center;
      border: 2px dashed var(--wv-line-strong);
      border-radius: var(--wv-radius-m);
    }
    i { display: block; font-size: 2rem; color: var(--wv-ink-soft); margin-bottom: var(--wv-space-3); }
    h2 { font-size: var(--wv-text-lg); margin-bottom: var(--wv-space-2); }
    p { color: var(--wv-ink-soft); margin: 0 auto var(--wv-space-4); max-width: 44ch; }
  `,
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly message = input<string>();
  readonly icon = input('bi-basket');
}
