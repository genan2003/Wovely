import { classifyDelivery, withinCeiling } from './delivery-emission';

/** Mirrors EDeliveryEmissionTest in the products service. */
describe('classifyDelivery', () => {
  it.each([
    ['Local Pickup', 'High', null, 'ZERO'],
    ['Bicycle Courier', 'Medium', false, 'ZERO'],
    ['Express', 'High', true, 'ZERO'],
    ['Carbon Neutral', 'High', false, 'LOW'],
    ['Electric Van', 'Medium', null, 'LOW'],
    ['Standard', ' low ', null, 'LOW'],
    ['Express', 'High', false, 'STANDARD'],
    [null, null, null, 'STANDARD'],
  ] as const)('%s / %s / pickup=%s → %s', (method, co2, pickup, expected) => {
    expect(classifyDelivery(method, co2, pickup)).toBe(expected);
  });

  it('applies the filter ceiling', () => {
    expect(withinCeiling('ZERO', 'LOW')).toBe(true);
    expect(withinCeiling('LOW', 'ZERO')).toBe(false);
    expect(withinCeiling('STANDARD', '')).toBe(true);
    expect(withinCeiling('STANDARD', 'LOW')).toBe(false);
  });
});
