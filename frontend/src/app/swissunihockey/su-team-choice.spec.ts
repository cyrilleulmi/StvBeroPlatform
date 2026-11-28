import { chosenSuTeamId, defaultSuTeamId } from './su-team-choice';

const teams = [
  { id: 'b', name: 'Team B' },
  { id: 'a', name: 'Team A' },
  { id: 'c', name: 'Team C' },
];

describe('defaultSuTeamId', () => {
  it('prefers the team as a player over the team as a coach', () => {
    const memberships = [
      { teamRole: 'COACH', team: { id: 'a' } },
      { teamRole: 'PLAYER', team: { id: 'c' } },
    ];
    expect(defaultSuTeamId(teams, memberships)).toBe('c');
  });

  it('takes the coached team without a playing team', () => {
    expect(defaultSuTeamId(teams, [{ teamRole: 'COACH', team: { id: 'b' } }])).toBe('b');
  });

  it('starts with the first team by name without an own team', () => {
    expect(defaultSuTeamId(teams, [])).toBe('a');
  });

  it('ignores memberships of teams that cannot be chosen', () => {
    expect(defaultSuTeamId([teams[0]], [{ teamRole: 'PLAYER', team: { id: 'x' } }])).toBe('b');
  });

  it('is null without teams', () => {
    expect(defaultSuTeamId([], [])).toBeNull();
  });
});

describe('chosenSuTeamId', () => {
  const memberships = [{ teamRole: 'PLAYER', team: { id: 'c' } }];

  it('takes the requested team', () => {
    expect(chosenSuTeamId(teams, memberships, 'b')).toBe('b');
  });

  it('falls back to the current team for an unknown or foreign id', () => {
    expect(chosenSuTeamId(teams, memberships, 'zzz')).toBe('c');
    expect(chosenSuTeamId([teams[2]], memberships, 'a')).toBe('c');
  });

  it('falls back without a request', () => {
    expect(chosenSuTeamId(teams, memberships, undefined)).toBe('c');
    expect(chosenSuTeamId(teams, memberships, '')).toBe('c');
  });
});
