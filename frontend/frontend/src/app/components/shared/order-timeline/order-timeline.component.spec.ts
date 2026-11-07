import { TestBed } from '@angular/core/testing';
import { OrderStatus, OrderTimelineEntry } from '../../../models/order.model';
import { buildSteps, OrderTimelineComponent } from './order-timeline.component';

const at = (d: number) => new Date(Date.UTC(2026, 10, d, 9, 30)).toISOString();

const entry = (
  status: OrderStatus,
  from: OrderStatus | null,
  day: number,
  extra: Partial<OrderTimelineEntry> = {},
): OrderTimelineEntry => ({ status, from, at: at(day), actor: 'SELLER', reason: null, inferred: false, ...extra });

const placed = entry(OrderStatus.PENDING, null, 1, { actor: 'BUYER' });

describe('buildSteps', () => {
  it('marks the last entry current and promises the remaining main steps', () => {
    const steps = buildSteps([placed, entry(OrderStatus.PROCESSING, OrderStatus.PENDING, 2)], OrderStatus.PROCESSING);
    expect(steps.map((s) => [s.label, s.state])).toEqual([
      ['Order placed', 'done'],
      ['Being prepared', 'current'],
      ['Shipped', 'upcoming'],
      ['Delivered', 'upcoming'],
      ['Completed', 'upcoming'],
    ]);
  });

  it('never promises the optional confirmation step', () => {
    expect(buildSteps([placed], OrderStatus.PENDING).map((s) => s.label)).not.toContain('Accepted by the seller');
  });

  it('ends a cancelled order without upcoming steps and keeps the reason', () => {
    const steps = buildSteps(
      [placed, entry(OrderStatus.CANCELLED, OrderStatus.PENDING, 2, { actor: 'BUYER', reason: ' Changed my mind ' })],
      OrderStatus.CANCELLED,
    );
    expect(steps.at(-1)).toMatchObject({ state: 'ended', by: 'by the buyer', reason: 'Changed my mind' });
    expect(steps.some((s) => s.state === 'upcoming')).toBe(false);
  });

  it('treats a completed order as fully sewn', () => {
    const steps = buildSteps([placed, entry(OrderStatus.COMPLETED, OrderStatus.DELIVERED, 9, { actor: 'SYSTEM' })], OrderStatus.COMPLETED);
    expect(steps.every((s) => s.state === 'done')).toBe(true);
    expect(steps.at(-1)?.by).toBe('automatically');
  });

  it('shows nothing ahead while Wovely reviews a dispute', () => {
    const steps = buildSteps([placed, entry(OrderStatus.DISPUTED, OrderStatus.SHIPPED, 4, { actor: 'ADMIN' })], OrderStatus.DISPUTED);
    expect(steps.at(-1)).toMatchObject({ label: 'Under review by Wovely', state: 'current', by: 'by Wovely support' });
    expect(steps).toHaveLength(2);
  });

  it('names a return to pending differently from the first step', () => {
    const steps = buildSteps([placed, entry(OrderStatus.PENDING, OrderStatus.DISPUTED, 5, { actor: 'ADMIN' })], OrderStatus.PENDING);
    expect(steps[1].label).toBe('Back to pending');
  });

  it('copes with a missing timeline', () => {
    expect(buildSteps([], OrderStatus.SHIPPED)).toEqual([]);
  });
});

describe('OrderTimelineComponent', () => {
  function render(timeline: OrderTimelineEntry[], status: OrderStatus, compact = false): HTMLElement {
    const fixture = TestBed.createComponent(OrderTimelineComponent);
    fixture.componentRef.setInput('timeline', timeline);
    fixture.componentRef.setInput('status', status);
    fixture.componentRef.setInput('compact', compact);
    fixture.componentRef.setInput('label', 'Progress of order ORD-1');
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders an ordered list with the current step announced', () => {
    const el = render([placed, entry(OrderStatus.SHIPPED, OrderStatus.PROCESSING, 3)], OrderStatus.SHIPPED);
    const list = el.querySelector('ol')!;
    expect(list.getAttribute('aria-label')).toBe('Progress of order ORD-1');
    const current = list.querySelector('[aria-current="step"]')!;
    expect(current.textContent).toContain('Shipped');
    expect(current.textContent).toContain('Current step');
    expect(list.querySelectorAll('li.upcoming')).toHaveLength(2);
    expect(list.querySelectorAll('.knot[aria-hidden="true"]')).toHaveLength(4);
  });

  it('puts a machine-readable time on every step that happened', () => {
    const el = render([placed], OrderStatus.PENDING);
    expect(el.querySelector('time')?.getAttribute('datetime')).toBe(at(1));
    expect(el.querySelector('li.upcoming time')).toBeNull();
  });

  it('labels steps read from old order records', () => {
    const el = render([{ ...placed, actor: null, inferred: true }], OrderStatus.PENDING);
    expect(el.querySelector('.inferred')?.textContent).toContain('from order records');
    expect(el.querySelector('.by')).toBeNull();
  });

  it('hides reasons in compact mode', () => {
    const cancelled = [placed, entry(OrderStatus.CANCELLED, OrderStatus.PENDING, 2, { reason: 'Out of wool' })];
    expect(render(cancelled, OrderStatus.CANCELLED).querySelector('.reason')?.textContent).toBe('Out of wool');
    expect(render(cancelled, OrderStatus.CANCELLED, true).querySelector('.reason')).toBeNull();
  });

  it('renders nothing without steps', () => {
    expect(render([], OrderStatus.PENDING).querySelector('ol')).toBeNull();
  });
});
