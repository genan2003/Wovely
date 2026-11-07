import { Injectable, signal } from '@angular/core';

export interface ConfirmOptions {
  title: string;
  message: string;
  /** Names the action, e.g. "Cancel order". */
  confirmLabel: string;
  cancelLabel?: string;
  /** Destructive actions get the danger button. */
  danger?: boolean;
}

export interface PromptOptions extends ConfirmOptions {
  /** Label of the text field. */
  label: string;
  value?: string;
  type?: 'text' | 'number';
  /** Lower bound for `type: 'number'`. */
  min?: number;
}

export type PendingDialog = ConfirmOptions & Partial<PromptOptions> & {
  /** Present when the dialog collects text. */
  collectsInput: boolean;
  resolve: (answer: string | null) => void;
};

/**
 * In-app dialogs, rendered once by ConfirmDialogComponent.
 * Replaces `confirm()` and `prompt()` (PLAN.md 5.3).
 */
@Injectable({ providedIn: 'root' })
export class DialogService {
  readonly pending = signal<PendingDialog | null>(null);

  async confirm(options: ConfirmOptions): Promise<boolean> {
    return (await this.open({ ...options, collectsInput: false })) !== null;
  }

  /** Resolves with the trimmed text, or null when dismissed. The field is required. */
  prompt(options: PromptOptions): Promise<string | null> {
    return this.open({ ...options, collectsInput: true });
  }

  settle(answer: string | null): void {
    this.pending()?.resolve(answer);
    this.pending.set(null);
  }

  private open(options: Omit<PendingDialog, 'resolve'>): Promise<string | null> {
    this.pending()?.resolve(null);
    return new Promise((resolve) => this.pending.set({ ...options, resolve }));
  }
}
