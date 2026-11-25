import { km } from './driver-list';

describe('km', () => {
  it('shows one decimal', () => {
    expect(km(23.4)).toBe('23.4');
    expect(km(23)).toBe('23.0');
  });

  it('treats a missing value as 0', () => {
    expect(km(null)).toBe('0.0');
    expect(km(undefined)).toBe('0.0');
  });
});
