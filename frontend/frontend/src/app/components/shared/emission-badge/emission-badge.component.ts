import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DeliveryEmission } from '../../../models/delivery-emission';

/** Delivery CO₂ badge (PLAN.md 3.5). Renders nothing for STANDARD delivery. */
@Component({
  selector: 'app-emission-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (label(); as text) {
      <span class="wv-emission" [class.zero]="level() === 'ZERO'" [attr.title]="explanation()">
        <svg class="leaf" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
          <path d="M13.5 2.5C7 2.5 3 5.5 3 10c0 1.2.4 2.3 1 3.2C5.2 9.6 7.8 7.4 11 6.2 8.3 7.9 6.3 10.3 5.2 13.6 6 14 6.9 14.2 8 14.2c4.2 0 5.5-4.7 5.5-11.7Z"
            [attr.fill]="level() === 'ZERO' ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
        </svg>
        <span>{{ text }}</span>
      </span>
    }
  `,
  styles: `
    :host { display: inline-flex; }
    .wv-emission {
      display: inline-flex;
      align-items: center;
      gap: 0.3em;
      padding: 0.2em 0.65em 0.2em 0.5em;
      font-size: 0.75rem;
      font-weight: 600;
      line-height: 1.4;
      border-radius: 999px;
      white-space: nowrap;
      color: var(--wv-eco-low-ink);
      background: var(--wv-eco-low-bg);
      border: 1px solid var(--wv-eco-low-border);
    }
    .leaf { width: 0.95em; height: 0.95em; flex: none; }
    .wv-emission.zero {
      color: var(--wv-eco-zero-ink);
      background: var(--wv-eco-zero-bg);
      border-color: var(--wv-eco-zero-bg);
    }
  `,
})
export class EmissionBadgeComponent {
  readonly level = input<DeliveryEmission | null | undefined>(null);
  /** Short text for product cards. */
  readonly compact = input(false);

  readonly label = computed(() => {
    const long = !this.compact();
    switch (this.level()) {
      case 'ZERO': return long ? 'Zero-emission delivery' : 'Zero-emission';
      case 'LOW': return long ? 'Low-emission delivery' : 'Low-emission';
      default: return null;
    }
  });

  readonly explanation = computed(() =>
    this.level() === 'ZERO'
      ? 'Delivered by bicycle or on foot, or available for local pickup'
      : 'Shipped carbon-neutral, electric or with a low CO₂ score',
  );
}
