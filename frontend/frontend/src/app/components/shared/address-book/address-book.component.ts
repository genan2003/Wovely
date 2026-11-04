import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Address, AddressRequest } from '../../../models/address.model';
import { AddressService } from '../../../services/address.service';

/** Manage the signed-in user's shipping addresses (used at checkout). */
@Component({
  selector: 'app-address-book',
  standalone: true,
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './address-book.component.html',
  styleUrl: './address-book.component.css',
})
export class AddressBookComponent implements OnInit {
  private service = inject(AddressService);
  private fb = inject(NonNullableFormBuilder);

  readonly addresses = signal<Address[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  /** null = form closed, '' = adding, id = editing that entry. */
  readonly editing = signal<string | null>(null);
  readonly confirmingDelete = signal<string | null>(null);
  readonly saving = signal(false);

  readonly form = this.fb.group({
    label: ['', [Validators.maxLength(40)]],
    recipientName: ['', [Validators.required, Validators.maxLength(100)]],
    street: ['', [Validators.required, Validators.maxLength(200)]],
    city: ['', [Validators.required, Validators.maxLength(100)]],
    postalCode: ['', [Validators.maxLength(20), Validators.pattern(/^[A-Za-z0-9 -]*$/)]],
    country: ['Romania', [Validators.required, Validators.maxLength(60)]],
    phone: ['', [Validators.maxLength(30), Validators.pattern(/^[0-9 +()-]*$/)]],
    makeDefault: [false],
  });

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.service.list().subscribe({
      next: (list) => {
        this.addresses.set(list ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Your addresses could not be loaded. Refresh the page to try again.');
        this.loading.set(false);
      },
    });
  }

  startAdd(): void {
    this.form.reset({ country: 'Romania', makeDefault: this.addresses().length === 0 });
    this.editing.set('');
  }

  startEdit(a: Address): void {
    this.form.reset({
      label: a.label ?? '', recipientName: a.recipientName, street: a.street, city: a.city,
      postalCode: a.postalCode ?? '', country: a.country, phone: a.phone ?? '', makeDefault: a.default,
    });
    this.editing.set(a.id);
  }

  cancel(): void {
    this.editing.set(null);
    this.error.set(null);
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const id = this.editing();
    const body = this.form.getRawValue() as AddressRequest;
    this.saving.set(true);
    (id ? this.service.update(id, body) : this.service.add(body)).subscribe({
      next: () => {
        this.saving.set(false);
        this.editing.set(null);
        this.error.set(null);
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(err.error?.message || 'The address could not be saved.');
      },
    });
  }

  makeDefault(a: Address): void {
    this.service.makeDefault(a.id).subscribe({ next: () => this.reload(), error: () => this.reload() });
  }

  remove(a: Address): void {
    this.service.remove(a.id).subscribe({
      next: () => {
        this.confirmingDelete.set(null);
        this.reload();
      },
      error: (err: HttpErrorResponse) => this.error.set(err.error?.message || 'The address could not be deleted.'),
    });
  }

  invalid(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }
}
