import { Order, OrderStatus } from './order.model';

/**
 * Orders a buyer can still open a return for. The 30-day window (from delivery) is computed and
 * enforced by the server (`returnDeadline`); this only hides orders the server would refuse.
 */
export function returnableOrders(orders: readonly Order[], now: number = Date.now()): Order[] {
  return orders.filter((o) => {
    const received = o.status === OrderStatus.DELIVERED || o.status === OrderStatus.COMPLETED;
    const inWindow = !!o.returnDeadline && new Date(o.returnDeadline).getTime() > now;
    return received && inWindow && o.items.some((item) => !item.isReturned);
  });
}
