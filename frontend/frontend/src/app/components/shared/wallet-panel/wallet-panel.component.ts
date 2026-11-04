import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { WalletService } from '../../../services/wallet.service';
import { TRANSACTION_LABELS, WalletTransaction } from '../../../models/wallet.model';

/** Balance, demo top-up and recent wallet activity (PLAN.md 3.7). */
@Component({
  selector: 'app-wallet-panel',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './wallet-panel.component.html',
  styleUrl: './wallet-panel.component.css',
})
export class WalletPanelComponent implements OnInit {
  readonly wallet = inject(WalletService);
  /** Sellers see payouts; buyers see top-up first. */
  readonly seller = input(false);

  readonly transactions = signal<WalletTransaction[]>([]);
  readonly error = signal<string | null>(null);
  readonly toppingUp = signal(false);
  readonly quickAmounts = [50, 100, 250];
  readonly labels = TRANSACTION_LABELS;
  customAmount: number | null = null;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.wallet.refresh().subscribe({ error: () => this.error.set('Your wallet could not be loaded.') });
    this.wallet.transactions().subscribe({ next: (t) => this.transactions.set(t ?? []), error: () => {} });
  }

  topUp(amount: number | null): void {
    if (!amount || amount <= 0 || amount > 5000) {
      this.error.set('Enter an amount between 0.01 and 5000 RON.');
      return;
    }
    this.error.set(null);
    this.toppingUp.set(true);
    this.wallet.topUp(Math.round(amount * 100) / 100).subscribe({
      next: () => {
        this.toppingUp.set(false);
        this.customAmount = null;
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.toppingUp.set(false);
        this.error.set(err.error?.message || 'The top-up did not go through.');
      },
    });
  }
}
