// Fictional fixtures and statistics for UI demonstration only.
export const teams = [
  { name: 'Arsenal', code: 'ARS', color: '#ef6262', played: 8, points: 20, scored: 19, conceded: 6, form: 'WWDWW' },
  { name: 'Manchester City', code: 'MCI', color: '#83c9ef', played: 8, points: 19, scored: 22, conceded: 8, form: 'WDWWW' },
  { name: 'Liverpool', code: 'LIV', color: '#fa7777', played: 8, points: 18, scored: 18, conceded: 7, form: 'WWLWW' },
  { name: 'Chelsea', code: 'CHE', color: '#7097ff', played: 8, points: 16, scored: 16, conceded: 9, form: 'DWWLW' },
  { name: 'Tottenham', code: 'TOT', color: '#c9d2ed', played: 8, points: 14, scored: 17, conceded: 12, form: 'WLWDW' },
  { name: 'Newcastle', code: 'NEW', color: '#b7c6c0', played: 8, points: 13, scored: 13, conceded: 10, form: 'LWWDD' },
];
export const matches = [
  { id: 1, home: teams[0], away: teams[1], date: 'Sat, 24 Oct', time: '17:30', venue: 'Emirates Stadium', result: [42, 26, 32], over: 68, btts: 72 },
  { id: 2, home: teams[2], away: teams[3], date: 'Sun, 25 Oct', time: '16:30', venue: 'Anfield', result: [57, 24, 19], over: 64, btts: 59 },
  { id: 3, home: teams[5], away: teams[4], date: 'Sun, 25 Oct', time: '14:00', venue: "St James’ Park", result: [38, 28, 34], over: 61, btts: 66 },
];
export const goalTrend = [
  { week: 'MW 1', scored: 12, conceded: 8 }, { week: 'MW 2', scored: 16, conceded: 10 },
  { week: 'MW 3', scored: 13, conceded: 7 }, { week: 'MW 4', scored: 21, conceded: 12 },
  { week: 'MW 5', scored: 18, conceded: 9 }, { week: 'MW 6', scored: 24, conceded: 13 },
  { week: 'MW 7', scored: 20, conceded: 10 }, { week: 'MW 8', scored: 27, conceded: 14 },
];
