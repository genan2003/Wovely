import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { InventoryService } from './inventory.service';
import { provideTestDefaults } from '../../testing/test-providers';

describe('InventoryService.addProduct', () => {
  it('sends local pickup and never a client-chosen emission level (PLAN.md 3.5)', () => {
    TestBed.configureTestingModule({ providers: provideTestDefaults() });
    const service = TestBed.inject(InventoryService);
    const http = TestBed.inject(HttpTestingController);

    service.addProduct('seller-1', { productName: 'Mug', shippingMethod: 'Express', localPickup: true, deliveryEmission: 'ZERO' }).subscribe();

    const req = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/products'));
    expect(req.request.body.localPickup).toBe(true);
    expect(req.request.body.deliveryEmission).toBeUndefined();
    req.flush({});
    http.verify();
  });
});
