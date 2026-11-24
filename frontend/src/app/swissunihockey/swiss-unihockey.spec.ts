import { suTeamUrl } from './swiss-unihockey';

describe('suTeamUrl', () => {
  it('links to the team at swissunihockey.ch', () => {
    expect(suTeamUrl(427913)).toBe('https://www.swissunihockey.ch/de/team-detail/?team_id=427913');
  });
});
