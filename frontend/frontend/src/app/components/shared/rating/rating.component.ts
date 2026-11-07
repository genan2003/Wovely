import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Read-only star rating with a text alternative. */
@Component({
  selector: 'app-rating',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="wv-rating" role="img" [attr.aria-label]="label()">
      @for (star of stars(); track $index) {
        <i class="bi" [class]="star" aria-hidden="true"></i>
      }
    </span>
    @if (count() !== null) {
      <span class="count">({{ count() }})</span>
    }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: var(--wv-space-1); }
    .wv-rating { display: inline-flex; gap: 1px; }
    .count { color: var(--wv-ink-soft); font-size: var(--wv-text-xs); }
  `,
})
export class RatingComponent {
  readonly value = input(0);
  readonly count = input<number | null>(null);

  readonly stars = computed(() =>
    [1, 2, 3, 4, 5].map((i) => {
      const value = this.value();
      if (i <= value) return 'bi-star-fill';
      return i - 0.5 <= value ? 'bi-star-half' : 'bi-star';
    }),
  );

  readonly label = computed(() =>
    this.count() === 0 ? 'No ratings yet' : `Rated ${Number(this.value().toFixed(1))} out of 5`,
  );
}
