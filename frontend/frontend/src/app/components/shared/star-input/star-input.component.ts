import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';

let nextId = 0;

/**
 * 1–5 star picker built on native radio buttons: arrow keys move between stars, screen readers
 * announce "3 of 5 stars", and the group is named by {@link label}. 0 means "not rated yet".
 */
@Component({
  selector: 'app-star-input',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset class="wv-stars">
      <legend [class.visually-hidden]="hideLabel()">{{ label() }}</legend>
      <span class="row">
        @for (i of [1, 2, 3, 4, 5]; track i) {
          <label class="star" [class.on]="i <= value()">
            <input type="radio" class="visually-hidden" [name]="name" [value]="i"
                   [checked]="value() === i" (change)="value.set(i)" />
            <i class="bi" [class.bi-star-fill]="i <= value()" [class.bi-star]="i > value()" aria-hidden="true"></i>
            <span class="visually-hidden">{{ i }} of 5 stars</span>
          </label>
        }
      </span>
    </fieldset>
  `,
  styles: `
    :host { display: block; }
    .wv-stars { margin: 0; padding: 0; border: 0; display: flex; flex-wrap: wrap; align-items: center;
      justify-content: space-between; gap: var(--wv-space-2); }
    legend { float: left; margin: 0; font-size: var(--wv-text-md); width: auto; }
    .row { display: inline-flex; gap: 2px; }
    .star { cursor: pointer; padding: 2px; border-radius: var(--wv-radius-s); color: var(--wv-ink-soft); line-height: 1; }
    .star.on { color: var(--wv-rating); }
    .star i { font-size: 1.5rem; }
    .star:has(input:focus-visible) { outline: 2px solid var(--wv-focus); outline-offset: 2px; }
    @media (prefers-reduced-motion: no-preference) { .star i { transition: transform 120ms var(--wv-ease); } .star:hover i { transform: scale(1.12); } }
  `,
})
export class StarInputComponent {
  readonly label = input.required<string>();
  readonly hideLabel = input(false);
  readonly value = model(0);
  protected readonly name = `wv-stars-${nextId++}`;
}
