import { returnableOrders } from './order-returns';
import { anOrder } from '../../testing/fixtures';
import { OrderStatus } from './order.model';

describe('returnableOrders', () => {
  const now = Date.parse('2026-11-07T12:00:00Z');
  const item = (isReturned = false) => ({ productId: 'p-1', productName: 'Basket', quantity: 1, price: 10, isReturned });

  it('keeps delivered and completed orders inside the server deadline', () => {
    const orders = [
      anOrder({ id: 'a', status: OrderStatus.DELIVERED, returnDeadline: '2026-11-20T00:00:00Z', items: [item()] }),
      anOrder({ id: 'b', status: OrderStatus.COMPLETED, returnDeadline: '2026-11-08T00:00:00Z', items: [item()] }),
    ];
    expect(returnableOrders(orders, now).map((o) => o.id)).toEqual(['a', 'b']);
  });

  it('drops orders past the deadline, without one, not yet received, or fully returned', () => {
    const orders = [
      anOrder({ id: 'late', status: OrderStatus.DELIVERED, returnDeadline: '2026-11-07T11:59:59Z', items: [item()] }),
      anOrder({ id: 'none', status: OrderStatus.DELIVERED, returnDeadline: null, items: [item()] }),
      anOrder({ id: 'shipped', status: OrderStatus.SHIPPED, returnDeadline: '2026-12-01T00:00:00Z', items: [item()] }),
      anOrder({ id: 'done', status: OrderStatus.DELIVERED, returnDeadline: '2026-12-01T00:00:00Z', items: [item(true)] }),
    ];
    expect(returnableOrders(orders, now)).toEqual([]);
  });
});
