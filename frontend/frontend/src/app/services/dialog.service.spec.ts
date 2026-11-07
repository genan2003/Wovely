import { DialogService } from './dialog.service';

const options = { title: 'Remove?', message: 'Really?', confirmLabel: 'Remove' };

describe('DialogService', () => {
  let dialog: DialogService;

  beforeEach(() => (dialog = new DialogService()));

  it('resolves confirm with true when accepted and false when dismissed', async () => {
    const accepted = dialog.confirm(options);
    dialog.settle('');
    expect(await accepted).toBe(true);

    const dismissed = dialog.confirm(options);
    dialog.settle(null);
    expect(await dismissed).toBe(false);
  });

  it('resolves prompt with the answer, or null when dismissed', async () => {
    const answered = dialog.prompt({ ...options, label: 'Why?' });
    expect(dialog.pending()?.collectsInput).toBe(true);
    dialog.settle('Too big');
    expect(await answered).toBe('Too big');

    const dismissed = dialog.prompt({ ...options, label: 'Why?' });
    dialog.settle(null);
    expect(await dismissed).toBeNull();
    expect(dialog.pending()).toBeNull();
  });

  it('dismisses an open dialog when a new one replaces it', async () => {
    const first = dialog.confirm(options);
    const second = dialog.confirm({ ...options, title: 'Second' });
    expect(await first).toBe(false);
    expect(dialog.pending()?.title).toBe('Second');
    dialog.settle('');
    expect(await second).toBe(true);
  });
});
