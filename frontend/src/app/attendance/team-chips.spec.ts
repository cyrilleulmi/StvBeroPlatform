import { apiTeams, isDefault, lockedTeam, selectedTeams, teamOptions, toggleTeam } from './team-chips';

const OWN = ['t1', 't2'];
const ORDER = ['t1', 't2', 't3'];

describe('selectedTeams (E-111)', () => {
  it('starts with the own teams', () => {
    expect(selectedTeams(undefined, undefined, OWN)).toEqual(['t1', 't2']);
  });

  it('reads ?teams= as a list and ignores empty entries and doubles', () => {
    expect(selectedTeams('t3,,t1,t3', undefined, OWN)).toEqual(['t3', 't1']);
  });

  it('still reads the old ?team= of earlier links', () => {
    expect(selectedTeams(undefined, 't3', OWN)).toEqual(['t3']);
  });

  it('prefers ?teams= over ?team=', () => {
    expect(selectedTeams('t2', 't3', OWN)).toEqual(['t2']);
  });

  it('starts with no team for a person without teams', () => {
    expect(selectedTeams(null, null, [])).toEqual([]);
  });
});

describe('isDefault and apiTeams', () => {
  it('the own teams in any order are the default and ask the API without a filter', () => {
    expect(isDefault(['t2', 't1'], OWN)).toBe(true);
    expect(apiTeams(['t2', 't1'], OWN)).toEqual([]);
  });

  it('another choice asks the API for exactly these teams', () => {
    expect(isDefault(['t1'], OWN)).toBe(false);
    expect(apiTeams(['t1', 't3'], OWN)).toEqual(['t1', 't3']);
  });
});

describe('toggleTeam', () => {
  it('switching a team off writes the rest into ?teams= and removes the old ?team=', () => {
    expect(toggleTeam(['t1', 't2'], 't2', false, OWN, ORDER)).toEqual({ teams: 't1', team: null });
  });

  it('switching a team on keeps the order of the chips', () => {
    expect(toggleTeam(['t3'], 't1', true, OWN, ORDER)).toEqual({ teams: 't1,t3', team: null });
  });

  it('back at the own teams the URL carries nothing', () => {
    expect(toggleTeam(['t1'], 't2', true, OWN, ORDER)).toEqual({ teams: null, team: null });
  });

  it('the last team cannot be switched off', () => {
    expect(toggleTeam(['t1'], 't1', false, OWN, ORDER)).toBeNull();
    expect(lockedTeam(['t1'], OWN)).toBe('t1');
    expect(lockedTeam(['t1', 't2'], OWN)).toBeNull();
  });

  it('a person without teams can switch the last one off and is back at the default', () => {
    expect(lockedTeam(['t1'], [])).toBeNull();
    expect(toggleTeam(['t1'], 't1', false, [], ORDER)).toEqual({ teams: null, team: null });
  });

  it('keeps a chosen team that is not a chip', () => {
    expect(toggleTeam(['x'], 't1', true, OWN, ORDER)).toEqual({ teams: 't1,x', team: null });
  });
});

describe('teamOptions', () => {
  const choices = [
    { id: 't2', name: 'Team 2' },
    { id: 't1', name: 'Team 1' },
  ];

  it('sorts the chips by name', () => {
    expect(teamOptions(choices, [], [], 'Anderes Team').map((t) => t.name)).toEqual(['Team 1', 'Team 2']);
  });

  it('adds a chosen team that is not among them, with the name the events give it', () => {
    expect(teamOptions(choices, ['t3'], [{ id: 't3', name: 'Team 3' }], 'Anderes Team').at(-1)).toEqual({
      id: 't3',
      name: 'Team 3',
    });
    expect(teamOptions(choices, ['t4'], [], 'Anderes Team').at(-1)).toEqual({ id: 't4', name: 'Anderes Team' });
  });
});
