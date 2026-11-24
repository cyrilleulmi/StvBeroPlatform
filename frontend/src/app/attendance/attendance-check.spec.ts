import { attendanceKey } from './attendance-check';

describe('attendanceKey', () => {
  it('writes the status in lowercase for data attributes and test IDs', () => {
    expect(attendanceKey('PRESENT')).toBe('present');
    expect(attendanceKey('ABSENT')).toBe('absent');
    expect(attendanceKey('EXCUSED')).toBe('excused');
  });
});
