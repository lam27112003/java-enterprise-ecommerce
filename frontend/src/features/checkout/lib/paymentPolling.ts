import type { Order } from '../../../shared/types/order';
import type { Payment } from '../../../shared/types/payment';
import { fetchOrder } from '../../orders/api/ordersApi';
import { fetchOrderPayment } from '../api/paymentApi';

export type PollResult = {
  order: Order;
  payment: Payment | null;
  paid: boolean;
  terminalUnpaid: boolean;
};

export type PollOptions = {
  orderId: string;
  intervalMs?: number;
  maxMs?: number;
  signal?: AbortSignal;
  onTick?: (result: PollResult) => void;
};

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'));
      return;
    }
    const timer = window.setTimeout(() => resolve(), ms);
    signal?.addEventListener(
      'abort',
      () => {
        window.clearTimeout(timer);
        reject(new DOMException('Aborted', 'AbortError'));
      },
      { once: true },
    );
  });
}

export async function pollUntilPaidOrTerminal(options: PollOptions): Promise<PollResult> {
  const started = Date.now();
  const maxMs = options.maxMs ?? 120_000;
  let interval = options.intervalMs ?? 2_000;

  // eslint-disable-next-line no-constant-condition
  while (true) {
    if (options.signal?.aborted) {
      throw new DOMException('Aborted', 'AbortError');
    }

    const order = await fetchOrder(options.orderId);
    let payment: Payment | null = null;
    try {
      payment = await fetchOrderPayment(options.orderId);
    } catch {
      payment = null;
    }

    const paid = order.status === 'PAID';
    const terminalUnpaid = order.status === 'CANCELLED' || order.status === 'PAYMENT_FAILED';
    const result: PollResult = { order, payment, paid, terminalUnpaid };
    options.onTick?.(result);

    if (paid || terminalUnpaid) {
      return result;
    }

    if (Date.now() - started >= maxMs) {
      return result;
    }

    await sleep(interval, options.signal);
    interval = Math.min(interval + 500, 5_000);
  }
}
