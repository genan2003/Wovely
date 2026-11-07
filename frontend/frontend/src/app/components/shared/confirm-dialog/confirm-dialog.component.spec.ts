import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { ConfirmDialogComponent } from './confirm-dialog.component';
import { DialogService } from '../../../services/dialog.service';

describe('ConfirmDialogComponent', () => {
  const buttons = (root: HTMLElement) => Array.from(root.querySelectorAll('button'));

  async function setup() {
    // jsdom has no modal dialog support
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) { this.open = true; });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) { this.open = false; });
    TestBed.configureTestingModule({ imports: [ConfirmDialogComponent] });
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    await fixture.whenStable();
    return { fixture, root: fixture.nativeElement as HTMLElement, service: TestBed.inject(DialogService) };
  }

  it('names the action on its button and resolves on submit', async () => {
    const { fixture, root, service } = await setup();
    const answer = service.confirm({ title: 'Remove basket?', message: 'Gone for good.', confirmLabel: 'Remove', danger: true });
    await fixture.whenStable();

    expect(root.querySelector('h2')?.textContent).toContain('Remove basket?');
    const confirm = buttons(root)[1];
    expect(confirm.textContent).toContain('Remove');
    expect(confirm.className).toContain('btn-danger');

    root.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    expect(await answer).toBe(true);
    await fixture.whenStable();
    expect(root.querySelector('dialog')?.open).toBe(false);
  });

  it('requires text before a prompt can be submitted', async () => {
    const { fixture, root, service } = await setup();
    const answer = service.prompt({ title: 'Report', message: 'Why?', label: 'Reason', confirmLabel: 'Send' });
    await fixture.whenStable();
    expect(buttons(root)[1].disabled).toBe(true);

    const field = root.querySelector('textarea')!;
    field.value = '  scam  ';
    field.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(buttons(root)[1].disabled).toBe(false);

    root.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    expect(await answer).toBe('scam');
  });

  it('resolves as dismissed from the secondary button', async () => {
    const { fixture, root, service } = await setup();
    const answer = service.confirm({ title: 'Sure?', message: '', confirmLabel: 'Yes' });
    await fixture.whenStable();
    buttons(root)[0].click();
    expect(await answer).toBe(false);
  });
});
