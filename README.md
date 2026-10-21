# Football Betting Analytics

A React and Spring Boot portfolio application for Premier League and UEFA Champions League football analytics.
Matches and standings come from football-data.org API v4 through the backend.
The dashboard chart and prediction cards remain clearly labeled fictional samples.
Match details additionally includes an experimental Poisson model. No odds integration
is implemented.

## Structure

- `frontend/src/components/`: dashboard, cards, table, chart, and async states.
- `frontend/src/services/footballApi.js`: HTTP requests and display adapters.
- `frontend/src/hooks/useFootballData.js`: independent loading, error, and retry state.
- `frontend/src/data/sampleData.js`: remaining chart and prediction demo data.
- `backend/src/main/java/com/tonyle/footballbettinganalytics/football/`: RestClient
  service, upstream response records, DTO mapper, controller, and safe API errors.
- `backend/src/test/`: mapping and mocked upstream service tests.
- `AGENTS.md`: project conventions.

## Requirements

- Node.js 24 LTS and npm.
- Java 25 and Maven 3.6.3+.
- A football-data.org API key with Premier League access.
- PostgreSQL is not required. Keep the default `setup` profile for this stage.

## Start the backend

Get your API token from https://www.football-data.org/ and set it in your backend
terminal (or your IDE's run configuration):

```sh
cd backend
export FOOTBALL_DATA_API_KEY='your-token-here'
mvn spring-boot:run
```

Do not share your token or commit it. Spring Boot does not automatically load
`.env` files. Never create a frontend `VITE_` variable for this token.

If Maven is not on your PATH, replace `mvn` with IntelliJ's bundled Maven:

```sh
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" spring-boot:run
```

Backend: http://localhost:8080. No root page exists; `/` returns 404.

## Start the frontend

In another terminal, from the project root:

```sh
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to http://localhost:8080, so no
permissive CORS configuration is needed. Production hosting must route `/api` to
the backend separately. Vite preview does not use the development proxy.

## Endpoints and response contracts

- `GET /api/matches`: upcoming current-season Premier League fixtures with status
  `SCHEDULED` or `TIMED`. The backend filters the provider's season response.
  Returns `[{id, utcDate, status, matchday, home, away}]`.
- `GET /api/standings`: current-season `TOTAL` league table.
  Returns `[{position, team, played, points, scored, conceded, goalDifference, form}]`.
- Team DTOs contain `{id, name, code, crest}`. Dates are ISO timestamps, displayed in UTC.
- Empty results return `[]`. Missing form is displayed as unavailable, never invented.
- Errors return `{ "message": "safe explanation" }` with HTTP 503 for missing or
  rejected credentials, 429 for rate limits, 504 for network/timeouts, and 502 for
  other provider errors or unreadable responses. Provider bodies are not exposed.

Requests use `X-Auth-Token` only between the backend and football-data.org.
Connection timeout is 5 seconds; response timeout is 15 seconds. There is no
polling or automatic retry. Each page load requests matches and standings once
(React Strict Mode may repeat initial requests in development). Manual retries
request only the failed resource. Provider plan limits apply; avoid rapid refreshes.
No API data is persisted and no database integration was added.

The dashboard displays three upcoming matches, and Matches shows all upcoming
season fixtures. Standings and matches load independently. Preview state controls
remain available for inspecting UI states; select API data to return to real results.

## Checks

```sh
cd frontend
npm test
npm run build
```

```sh
cd backend
mvn verify
```

Frontend tests use Node's built-in test runner, with no extra dependencies.
Backend tests use JUnit and Spring's mock HTTP server, with no real token or network.

Latest environment checks: frontend tests and production build passed. Backend
main and test compilation succeeded using cached dependencies, but Maven test
execution was blocked by the uncached Surefire JUnit platform provider and network
DNS restrictions. A live authenticated football-data.org call has not been verified.

Official API reference: https://docs.football-data.org/general/v4/competition.html

## Git

Do not commit or push automatically. Build outputs, dependencies, and local secrets
are ignored by `.gitignore`.

## Competition selector

Choose Premier League or UEFA Champions League in the dashboard header. Matches,
standings, and overview statistics reload for the selected competition. The API
accepts `competition=PL` (default) or `competition=CL` on both endpoints:

```text
/api/matches?competition=CL
/api/standings?competition=CL
```

Other codes return HTTP 400 before making an upstream request. Champions League
standings are shown as a league-phase table, not a knockout bracket. If the
provider returns 404 for its Champions League standings, this endpoint returns
an empty list; authorization, rate-limit, and other failures remain errors.
Goals and team counts refer to the returned table, not all knockout or qualifying
participants. Existing prediction and chart samples remain fictional Premier
League examples and are not recalculated by changing the competition.

## Match details

Click any upcoming match to open its details page. Hash links include the match ID
and competition, for example `/#match/123456/CL`, and support browser back/forward.
The backend endpoint `GET /api/matches/{id}` fetches an individual match using the
same server-side API key. Invalid IDs return 400; missing matches return 404.

Details include both teams and crests, UTC kickoff, status, provider score, and
venue when available. Available match statistics include possession, shots,
shots on target, corners, fouls and cards. Missing scores/statistics are never
replaced with zero: scheduled fixtures usually have neither. Coverage and API
plan affect which statistics the provider supplies. Team comparison uses the
selected competition's current standings, not historical standings at kickoff.
No automatic polling is performed. The existing sample predictions are unchanged.

## Recent team performance

Match details now includes the last five completed results for each team, newest
first, strictly before the selected fixture's kickoff. Results cover only the
selected competition and its season (not all competitions or previous seasons).

`GET /api/matches/{id}/performance` reads the fixture metadata, then fetches that
competition's finished season fixtures once for both teams. It returns a clean
response containing competition, season, cutoff, and each team's results/summary.
The key remains backend-only. The section loads independently and has its own
retry button; provider authentication, rate-limit and network errors use the
existing safe error handling. No automatic polling or retry is added.

Scores and W/D/L are from the team's perspective, including for away matches.
Missing scores remain unknown. Summaries and per-match goals scored/conceded
averages use only results with both scores available, and explicitly show that
sample size. Missing goal totals/averages display as unavailable, not zero.
Zero to four eligible matches are shown without filling gaps. Selection uses
provider FINISHED status and kickoff timestamps; no historical completion-time
snapshot is available. The selected fixture, later fixtures and equal kickoff
timestamps are excluded. Invalid dates are excluded. Scores follow the provider's
fullTime field; no separate penalty-shootout analysis is calculated.

The backend tests cover cutoff, ordering, five-result limit, perspective, missing
scores, summary calculations, and a shared competition request for both teams.
Frontend tests cover the new response handling and rate-limit/empty/error cases.

Recent-performance verification: all 10 frontend tests and the frontend production
build passed. Backend production and test sources compiled. Three pure backend
calculation tests also passed when invoked directly using cached JUnit assertions
(order/cutoff/limit, home-away calculations, and empty/missing values). Full Maven
verification and mocked HTTP test execution remain blocked by the uncached
Surefire JUnit platform runner. Live provider/browser verification was not possible
from this restricted environment.

## Season performance foundation

The existing `/api/matches/{id}/performance` response now includes a
`seasonPerformance` object alongside recent form. No additional frontend request
or upstream season-fixture request is required. It contains:

- `home` and `away`: selected team IDs with `overall`, `home`, and `away` records.
- Each record: `played` (complete-score sample size), `excluded` (eligible matches
  with incomplete/invalid scores), wins/draws/losses, goals scored/conceded, and
  per-match scored/conceded averages.
- `competition`: complete-score match count, excluded score count, and average
  home and away goals for that same competition, season and kickoff cutoff.

The match details page displays the home team's home record beside the away
team's away record, plus both teams' full split tables and the competition baseline.
The section shares the performance loading/error/retry states. A missing season
object is displayed as unavailable for compatibility with an older running backend.
Restart the backend after this update.

The shared eligible fixture set excludes the selected match, equal or later
kickoffs, unfinished fixtures, invalid dates and missing team identities. The
provider request selects competition and season; supplied fixture metadata is
also checked against both. Missing fixture metadata relies on the scoped provider
response. Statistics exclude incomplete or negative scores and disclose those
exclusions per split and for the competition. Empty samples keep played/W/D/L
counts at zero, but goal totals and averages are null and shown as unavailable.
Actual scoreless draws retain zero goals and zero averages. All averages use
`played`, not played plus excluded, as their denominator.

These figures provide inputs for a future Poisson model: home-team home attack
and defence, away-team away attack and defence, and competition home/away scoring
baselines. The experimental model below uses a separate regulation-only input
summary, so existing season/recent-form statistics retain their original provider
fullTime semantics. No model fitting, smoothing or odds integration is performed. Historical eligibility uses current provider
FINISHED status and kickoff timestamps, not archived completion-time snapshots.

Verification for this change: 15 frontend tests and the production build passed.
Backend main/test sources compiled. Six season/recent-form calculation tests passed
through direct invocation with cached JUnit assertions. Full `mvn verify` remains
blocked by the uncached Surefire JUnit runner; mocked HTTP tests and live browser/API
verification were not completed in this environment.


## Experimental Poisson match estimates

The existing `/api/matches/{id}/performance` response includes `prediction`,
calculated entirely on the backend. Model version: `cross-competition-poisson-v2`.
The frontend makes no additional request. Dashboard samples are unchanged.
Competition-specific recent form and season statistics remain separate and retain
their existing semantics; they are not the model's cross-competition inputs.

### History selection and coverage

Official team-match filters: https://docs.football-data.org/general/v4/team.html

For each team, the backend requests completed matches using `dateFrom`, `dateTo`,
`status=FINISHED` and `limit=500`, without a competition or season filter. The
window spans 365 days before the selected kickoff and can include the previous
season. Requests include the following calendar day to avoid losing earlier
same-day fixtures through date-only filtering. Locally, fixtures must have a
kickoff strictly before the selected match and within the 365-day window.
The selected fixture is excluded, IDs are deduplicated, and eligible scores are
validated **before** sorting newest first and selecting up to 20 matches per team.
Thus a historical fixture never uses the provider's latest 20 as a substitute
for the latest 20 before that fixture's cutoff.

Coverage depends on the API subscription and the records returned by the provider;
not every competition is necessarily covered. No missing matches are invented.
A response reaching the 500-match cap is treated as unavailable because complete
selection cannot be guaranteed. Empty histories produce insufficient data.
Current FINISHED status is used, not an archived completion-time snapshot.

The response and UI disclose each team's contributing competitions and counts,
date range, total/home/away samples, and exclusions for unusable regulation scores,
invalid metadata, outside-cutoff/window fixtures and duplicate IDs. Exclusion
counts describe the fetched history, not just the selected 20. Older eligible
matches beyond the target are simply not selected.

### Regulation scores

Official semantics: https://docs.football-data.org/general/v4/overtime.html

Complete, non-negative `regularTime` scores are preferred. Otherwise `fullTime`
is accepted only when `duration` is explicitly `REGULAR`. Extra-time/shootout
fixtures without confirmed regulation scores, partial scores and unknown durations
are excluded. No scores are inferred or subtracted. Regulation includes stoppage
time; knockout qualification and aggregate results are not modeled.

### Inputs and formulas

Each team's selected history is split by venue. Let:

- HS and HC = home team's average goals scored/conceded in its home fixtures.
- AS and AC = away team's average goals scored/conceded in its away fixtures.

```text
lambda_home = (HS + AC) / 2
lambda_away = (AS + HC) / 2
```

This is a direct, equally weighted blend of attack and opponent concession rates
on the same goals-per-match scale. Unlike v1, it does not divide mixed-competition
rates by a selected-competition baseline or calculate relative strength ratios.
No league-strength, opponent-strength, recency or previous-season adjustment is
applied. Teams can face very different opposition; a consistent arithmetic scale
does not make those samples equally representative. Lambdas are model goal means,
not measured shot-based xG.

Independent Poisson probabilities use `P(k; lambda) = exp(-lambda) * lambda^k / k!`
with recurrence. The score grid sums home-win, draw and away-win cells.

```text
L = lambda_home + lambda_away
Under 2.5 = exp(-L) * (1 + L + L^2 / 2)
Over 2.5  = 1 - Under 2.5
BTTS Yes  = (1 - exp(-lambda_home)) * (1 - exp(-lambda_away))
BTTS No   = 1 - BTTS Yes
```

### Minimum samples and unavailable data

The provisional minimum is eight eligible matches per team, including at least
three home fixtures for the home team and three away fixtures for the away team.
Eight total fixtures give some broader history while three relevant-venue fixtures
allow earlier availability than v1's five-plus-five and 30 league fixtures.
Only the relevant venue fixtures determine the rates. These thresholds are a
usability compromise, not evidence of predictive accuracy. Fewer than five
relevant venue fixtures, fewer than 20 total fixtures and mixed competitions are
explicit warnings; there is no invented confidence score.

Insufficient samples return `insufficient_data`; failed/incomplete history requests
return `history_unavailable`. Inputs, means and probabilities are null in both
cases. Successfully loaded team provenance remains visible when the other team
fails. Existing competition statistics remain available when team-history loading
fails. Network, access/subscription and rate-limit failures use the existing safe
backend error messages. After a rate-limit failure for the first team, the second
history request is skipped. There are no automatic retries or polling.

Genuine zero rates are supported, including two zero means producing a draw
probability of one. No competition-baseline division is required. Negative,
non-finite or means above 20 return unavailable calculations, not capped inputs.
Each Poisson marginal retains all but at most 1e-12 probability mass, with a
200-goal guard. The joint omitted mass is returned as `omittedMass`; 1X2 is
normalized only if that mass is at most 3e-12. Goals and BTTS use untruncated
analytical formulas. Probabilities use 0–1; rounded UI percentages can sum to
99.9% or 100.1%.

### Request reuse and limitations

Successful raw team histories are cached in memory for two minutes, keyed by team
and requested date window, with at most 32 entries. Selection is reapplied for
each exact kickoff. Concurrent identical history requests are coalesced. Failed
responses are not cached, allowing manual retry. Each uncached performance request
normally adds two team-history calls to the existing match and season requests;
API subscription rate limits still apply. No persistence or API key is exposed.

The model is experimental and unvalidated. Previous seasons and mixed competitions
improve availability but can introduce stale team strength and unequal opposition.
A bounded one-year history may still be insufficient. Independent constant scoring
rates, small venue samples and genuine zero rates can produce extreme estimates.
There is no calibration, smoothing, backtesting, injury/lineup adjustment,
bookmaker comparison or betting recommendation. Estimates remain pre-match even
when viewing a match that has already started.

Verification: 16 frontend tests and the production build passed. Backend main and
test sources compiled; 15 model/history/season/recent-form calculation tests passed
through direct invocation with cached JUnit assertions. Full Maven verification
is blocked by the uncached Surefire JUnit runner (`surefire-junit-platform:3.5.6`);
mocked HTTP tests and live provider/browser verification were not run successfully
in this environment.
