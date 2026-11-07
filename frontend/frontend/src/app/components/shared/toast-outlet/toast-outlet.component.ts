import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ToastService, ToastTone } from '../../../services/toast.service';

const ICONS: Record<ToastTone, string> = {
  success: 'bi-check-circle',
  error: 'bi-exclamation-circle',
  info: 'bi-info-circle',
};

@Component({
  selector: 'app-toast-outlet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toast-stack" role="region" aria-label="Notifications">
      <div role="status" aria-live="polite">
        @for (toast of polite(); track toast.id) {
          <div [class]="'wv-toast wv-toast--' + toast.tone">
            <i class="bi" [class]="icons[toast.tone]" aria-hidden="true"></i>
            <p>{{ toast.message }}</p>
            <button type="button" aria-label="Dismiss notification" (click)="service.dismiss(toast.id)">
              <i class="bi bi-x-lg" aria-hidden="true"></i>
            </button>
          </div>
        }
      </div>
      <div role="alert">
        @for (toast of urgent(); track toast.id) {
          <div class="wv-toast wv-toast--error">
            <i class="bi" [class]="icons.error" aria-hidden="true"></i>
            <p>{{ toast.message }}</p>
            <button type="button" aria-label="Dismiss notification" (click)="service.dismiss(toast.id)">
              <i class="bi bi-x-lg" aria-hidden="true"></i>
            </button>
          </div>
        }
      </div>
    </div>
  `,
  styleUrl: './toast-outlet.component.css',
})
export class ToastOutletComponent {
  protected service = inject(ToastService);
  protected icons = ICONS;

  protected polite = computed(() => this.service.toasts().filter((toast) => toast.tone !== 'error'));
  protected urgent = computed(() => this.service.toasts().filter((toast) => toast.tone === 'error'));
}
