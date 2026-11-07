import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ListingStatus } from '../../../models/inventory.model';

interface StatusCopy {
  label: string;
  hint: string;
  tone: 'review' | 'changes' | 'rejected' | 'live';
}

const COPY: Record<ListingStatus, StatusCopy | null> = {
  PENDING: { label: 'Awaiting review', hint: 'Hidden from buyers until a moderator approves it.', tone: 'review' },
  NEEDS_CHANGES: {
    label: 'Changes requested',
    hint: 'A moderator asked for changes. Edit the listing to send it back for review.',
    tone: 'changes',
  },
  REJECTED: { label: 'Rejected', hint: 'Not accepted for the shop. Create a new listing instead.', tone: 'rejected' },
  APPROVED: { label: 'Live', hint: 'Buyers can find and order it.', tone: 'live' },
  ARCHIVED: null,
};

/**
 * Review state of a seller's listing. Live listings show nothing unless {@link always} is set, so the
 * badge only appears on cards that need the seller's attention.
 */
@Component({
  selector: 'app-listing-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (copy(); as c) {
      <span class="wv-status" [class]="'wv-status ' + c.tone" [attr.title]="c.hint">
        <span class="knot" aria-hidden="true"></span>
        <span>{{ c.label }}</span>
        <span class="visually-hidden">: {{ c.hint }}</span>
      </span>
    }
  `,
  styles: `
    :host { display: inline-flex; vertical-align: middle; }
    .wv-status {
      display: inline-flex;
      align-items: center;
      gap: 0.4em;
      padding: 0.15em 0.65em 0.15em 0.5em;
      font-size: var(--wv-text-xs);
      font-weight: 600;
      line-height: 1.4;
      border: 1px solid transparent;
      border-radius: var(--wv-radius-pill);
      white-space: nowrap;
    }
    /* a small tied knot instead of a generic dot */
    .knot {
      width: 0.55em;
      height: 0.55em;
      border-radius: 50% 50% 50% 0;
      background: currentColor;
      transform: rotate(-45deg);
    }
    .review { color: var(--wv-warning); background: var(--wv-warning-bg); }
    /* the seller has to act: stitched madder outline, like a tacking thread left to unpick */
    .changes { color: var(--wv-danger); background: var(--wv-surface-raised); border: 1px dashed var(--wv-danger); }
    .rejected { color: var(--wv-on-danger); background: var(--wv-danger); }
    .live { color: var(--wv-eco); background: var(--wv-eco-bg); }
  `,
})
export class ListingStatusComponent {
  readonly status = input<ListingStatus | string | null | undefined>(null);
  /** Also show "Live" (used in the details dialog). */
  readonly always = input(false);

  readonly copy = computed<StatusCopy | null>(() => {
    const c = COPY[this.status() as ListingStatus] ?? null;
    return c && c.tone === 'live' && !this.always() ? null : c;
  });
}
