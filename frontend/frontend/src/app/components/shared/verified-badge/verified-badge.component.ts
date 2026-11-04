import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * "Verified purchase" mark for reviews (PLAN.md 3.3). The flag is computed by the server from the
 * author's delivered orders; this component only renders it.
 */
@Component({
  selector: 'app-verified-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="wv-verified" [attr.title]="explanation()">
      <i class="bi bi-patch-check-fill" aria-hidden="true"></i>
      <span>Verified purchase</span>
      <span class="visually-hidden">: {{ explanation() }}</span>
    </span>
  `,
  styles: `
    :host { display: inline-flex; vertical-align: middle; }
    .wv-verified {
      display: inline-flex;
      align-items: center;
      gap: 0.3em;
      padding: 0.15em 0.6em 0.15em 0.45em;
      font-size: 0.75rem;
      font-weight: 600;
      line-height: 1.4;
      color: var(--wv-verified-ink);
      background: var(--wv-verified-bg);
      /* running-stitch outline: the one textile accent */
      border: 1px dashed var(--wv-verified-stitch);
      border-radius: 999px;
      white-space: nowrap;
    }
    .wv-verified i { font-size: 0.9em; }
  `,
})
export class VerifiedBadgeComponent {
  /** What was bought: the product itself, or anything from this seller. */
  readonly kind = input<'product' | 'seller'>('product');

  readonly explanation = computed(() =>
    this.kind() === 'seller'
      ? 'This buyer received an order from this seller through Wovely'
      : 'This buyer received this item through Wovely',
  );
}
