import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { DialogService } from '../../../services/dialog.service';

/** Native <dialog>: the focus trap, Esc and the inert background come from the browser. */
@Component({
  selector: 'app-confirm-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="wv-dialog" aria-labelledby="wv-dialog-title" aria-describedby="wv-dialog-message"
            (cancel)="service.settle(null)">
      @if (service.pending(); as request) {
        <form method="dialog" (submit)="submit($event)">
          <h2 id="wv-dialog-title">{{ request.title }}</h2>
          <p id="wv-dialog-message">{{ request.message }}</p>
          @if (request.collectsInput) {
            <label class="form-label" for="wv-dialog-input">{{ request.label }}</label>
            @if (request.type === 'number') {
              <input #field id="wv-dialog-input" class="form-control" type="number" [min]="request.min ?? null"
                     [value]="answer()" (input)="answer.set(field.value)" />
            } @else {
              <textarea #field id="wv-dialog-input" class="form-control" rows="3"
                        [value]="answer()" (input)="answer.set(field.value)"></textarea>
            }
          }
          <div class="wv-dialog__actions">
            <button type="button" class="btn btn-outline-secondary" (click)="service.settle(null)">
              {{ request.cancelLabel ?? 'Go back' }}
            </button>
            <button type="submit" [class]="request.danger ? 'btn btn-danger' : 'btn btn-primary'" [disabled]="!canSubmit()">
              {{ request.confirmLabel }}
            </button>
          </div>
        </form>
      }
    </dialog>
  `,
  styleUrl: './confirm-dialog.component.css',
})
export class ConfirmDialogComponent {
  protected service = inject(DialogService);
  private dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected answer = signal('');
  protected canSubmit = computed(() => !this.service.pending()?.collectsInput || this.answer().trim().length > 0);

  constructor() {
    effect(() => {
      const element = this.dialog().nativeElement;
      const request = this.service.pending();
      if (request) {
        this.answer.set(request.value ?? '');
        if (!element.open) {
          element.showModal();
        }
      } else if (element.open) {
        element.close();
      }
    });
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (this.canSubmit()) {
      this.service.settle(this.service.pending()?.collectsInput ? this.answer().trim() : '');
    }
  }
}
