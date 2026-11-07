import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { OrderStatus, OrderTimelineEntry, TimelineActor } from '../../../models/order.model';

/** The usual path of an order; steps after the current one are shown as still to come. */
const PATH: OrderStatus[] = [
  OrderStatus.PENDING,
  OrderStatus.CONFIRMED,
  OrderStatus.PROCESSING,
  OrderStatus.SHIPPED,
  OrderStatus.DELIVERED,
  OrderStatus.COMPLETED,
];

/** CONFIRMED is optional (sellers may go straight to PROCESSING), so it is never promised as a future step. */
const UPCOMING = new Set<OrderStatus>([
  OrderStatus.PROCESSING,
  OrderStatus.SHIPPED,
  OrderStatus.DELIVERED,
  OrderStatus.COMPLETED,
]);

const ENDS = new Set<OrderStatus>([OrderStatus.CANCELLED, OrderStatus.REFUNDED]);

const LABEL: Record<OrderStatus, string> = {
  PENDING: 'Order placed',
  CONFIRMED: 'Accepted by the seller',
  PROCESSING: 'Being prepared',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  REFUNDED: 'Refunded',
  DISPUTED: 'Under review by Wovely',
};

const BY: Record<TimelineActor, string> = {
  BUYER: 'by the buyer',
  SELLER: 'by the seller',
  ADMIN: 'by Wovely support',
  SYSTEM: 'automatically',
};

export type StepState = 'done' | 'current' | 'upcoming' | 'ended';

export interface TimelineStep {
  key: string;
  label: string;
  state: StepState;
  at: string | null;
  by: string | null;
  reason: string | null;
  inferred: boolean;
}

/** Turns the server timeline into the steps to draw (exported for tests). */
export function buildSteps(entries: readonly OrderTimelineEntry[], status: OrderStatus | null): TimelineStep[] {
  if (!entries.length) {
    return []; // nothing known yet: don't draw a path that starts nowhere
  }
  const steps: TimelineStep[] = entries.map((e, i) => ({
    key: `${i}-${e.status}`,
    label: e.status === OrderStatus.PENDING && e.from ? 'Back to pending' : LABEL[e.status] ?? e.status,
    state: 'done',
    at: e.at,
    by: e.actor ? BY[e.actor] : null,
    reason: e.reason?.trim() || null,
    inferred: e.inferred,
  }));
  const current = status ?? entries.at(-1)?.status ?? null;
  const last = steps.at(-1);
  if (last && current) {
    last.state = ENDS.has(current) ? 'ended' : current === OrderStatus.COMPLETED ? 'done' : 'current';
  }
  if (current && !ENDS.has(current) && current !== OrderStatus.DISPUTED) {
    const from = PATH.indexOf(current);
    if (from >= 0) {
      for (const s of PATH.slice(from + 1)) {
        if (UPCOMING.has(s)) {
          steps.push({ key: `next-${s}`, label: LABEL[s], state: 'upcoming', at: null, by: null, reason: null, inferred: false });
        }
      }
    }
  }
  return steps;
}

const STATE_TEXT: Record<StepState, string> = {
  done: 'Done',
  current: 'Current step',
  upcoming: 'Not yet',
  ended: 'Final step',
};

/**
 * Order status timeline (README Story 2: "a clear status timeline for every active order"; PLAN.md 7.1).
 * Drawn as a running stitch: finished steps are sewn, the current one is a knot, steps to come are basted.
 * A cancelled or refunded order ends in a madder knot where the thread is cut.
 */
@Component({
  selector: 'app-order-timeline',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe],
  template: `
    @if (steps().length) {
      <ol class="thread" [attr.aria-label]="label()">
        @for (step of steps(); track step.key) {
          <li class="step" [class]="'step ' + step.state" [attr.aria-current]="step.state === 'current' ? 'step' : null">
            <span class="knot" aria-hidden="true"></span>
            <div class="body">
              <span class="name">{{ step.label }}</span>
              <span class="visually-hidden">: {{ stateText[step.state] }}</span>
              @if (step.at) {
                <span class="when">
                  <time [attr.datetime]="step.at" [attr.title]="step.at | date: 'full'">
                    <span class="day">{{ step.at | date: 'd MMM' }}</span>
                    <span class="time">{{ step.at | date: 'HH:mm' }}</span>
                  </time>
                  @if (step.by) {
                    <span class="by">{{ step.by }}</span>
                  }
                  @if (step.inferred) {
                    <span class="inferred" title="This order is older than the timeline; the time comes from its records.">from order records</span>
                  }
                </span>
              }
              @if (step.reason && !compact()) {
                <q class="reason">{{ step.reason }}</q>
              }
            </div>
          </li>
        }
      </ol>
    }
  `,
  styles: `
    :host {
      display: block;
      --stitch: var(--wv-primary);
      --baste: var(--wv-line-strong);
      --knot: 0.75rem;
    }
    .thread {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .step {
      position: relative;
      display: grid;
      grid-template-columns: var(--knot) 1fr;
      column-gap: var(--wv-space-3);
      padding-bottom: var(--wv-space-4);
    }
    :host(.compact) .step { padding-bottom: var(--wv-space-2); }
    .step:last-child { padding-bottom: 0; }

    /* The thread to the next step: a running stitch, sewn (done) or basted (still to come). */
    .step:not(:last-child)::before {
      content: '';
      position: absolute;
      left: calc(var(--knot) / 2 - 1px);
      top: calc(var(--knot) + 0.3rem);
      bottom: 0.15rem;
      width: 2px;
      background: repeating-linear-gradient(to bottom, var(--stitch) 0 6px, transparent 6px 10px);
    }
    .step.current:not(:last-child)::before,
    .step.upcoming:not(:last-child)::before {
      background: repeating-linear-gradient(to bottom, var(--baste) 0 3px, transparent 3px 9px);
      opacity: 0.7;
    }

    .knot {
      box-sizing: border-box;
      width: var(--knot);
      height: var(--knot);
      margin-top: 0.3rem;
      border-radius: 50%;
      background: var(--stitch);
      border: 2px solid var(--stitch);
    }
    .current .knot {
      transform: scale(1.35);
      background: var(--wv-surface-raised);
      border-width: 3px;
      box-shadow: 0 0 0 3px var(--wv-cloth-deep);
    }
    /* A stitch hole waiting for the thread: thin ring, slightly smaller (dashed borders break up at this size). */
    .upcoming .knot {
      background: transparent;
      border: 1.5px solid var(--baste);
      transform: scale(0.8);
    }
    .ended .knot {
      background: var(--wv-danger);
      border-color: var(--wv-danger);
      transform: scale(1.2);
    }

    .body {
      display: flex;
      flex-direction: column;
      gap: 0.1rem;
      min-width: 0;
    }
    .name {
      font-weight: 600;
      color: var(--wv-ink);
      line-height: 1.35;
    }
    .upcoming .name { font-weight: 500; color: var(--wv-ink-soft); }
    .ended .name { color: var(--wv-danger); }

    .when {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      column-gap: var(--wv-space-2);
      font-size: var(--wv-text-xs);
      color: var(--wv-ink-soft);
    }
    .day {
      font-family: var(--wv-font-display);
      color: var(--wv-ink);
      margin-right: 0.3em;
    }
    .inferred {
      font-style: italic;
    }
    .reason {
      margin-top: var(--wv-space-1);
      max-width: 60ch;
      font-size: var(--wv-text-sm);
      color: var(--wv-ink);
      overflow-wrap: anywhere;
    }

    @media (prefers-reduced-motion: no-preference) {
      .current .knot { transition: transform 200ms var(--wv-ease); }
    }
    @media (forced-colors: active) {
      .knot, .ended .knot { forced-color-adjust: none; background: CanvasText; border-color: CanvasText; }
      .current .knot, .upcoming .knot { background: Canvas; }
      .step:not(:last-child)::before { background: CanvasText; }
    }
  `,
  host: { '[class.compact]': 'compact()' },
})
export class OrderTimelineComponent {
  readonly timeline = input<readonly OrderTimelineEntry[] | null | undefined>([]);
  /** The order's current status; defaults to the last timeline entry. */
  readonly status = input<OrderStatus | `${OrderStatus}` | null>(null);
  /** Tighter spacing and no reasons, for table rows. */
  readonly compact = input(false);
  /** Accessible name of the list, e.g. "Progress of order ORD-123". */
  readonly label = input('Order progress');

  protected readonly stateText = STATE_TEXT;
  readonly steps = computed(() => buildSteps(this.timeline() ?? [], (this.status() as OrderStatus | null) ?? null));
}
