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


## Simple probability and odds view

The dashboard lists real upcoming matches, with standings available separately.
Fictional dashboard predictions and trend charts are no longer displayed. Open a
match for experimental result, total-goals and both-teams-to-score probabilities.
Supporting form, charts, season statistics and model details sit in collapsed
“View analysis” sections. Sample sizes and coverage warnings remain visible.

You can enter optional decimal bookmaker odds greater than 1 for each outcome.
These inputs stay in React state for the current match page and are cleared when
leaving it; they are not sent to the backend or stored. No odds API is used.

- Fair odds = `1 / probability`, using the unrounded model probability.
- Expected return per unit staked = `probability * odds - 1`.
- Positive return is labeled “Potential value according to the model”, conditional
  on the estimate being accurate. High probability alone does not establish value.

Missing odds never produce a value label. Invalid or missing probabilities cannot
be compared. Zero probability is preserved: no finite fair odds are displayed,
and valid odds produce an expected return of -1 per unit. The estimates remain
experimental and unvalidated; expected return is not a guaranteed result or a
betting recommendation. The backend model and its thresholds are unchanged.

## Backend backtesting (explicit CLI only)

The `backtest` Spring profile runs a small evaluation and exits without starting
an HTTP server or database. Ordinary application startup does not evaluate
anything. It reuses `cross-competition-poisson-v2` without changing its model or
sample thresholds. There is no evaluation endpoint or frontend change.

From the project root, set `FOOTBALL_DATA_API_KEY` in the backend terminal, then:

```sh
cd backend
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" spring-boot:run \
  -Dspring-boot.run.profiles=backtest \
  -Dspring-boot.run.arguments="--backtest.competition=PL --backtest.from=2026-09-01 --backtest.to=2026-09-30 --backtest.max-matches=20 --backtest.request-budget=25 --backtest.output=backtest-reports/september.json"
```

If Maven is on your PATH, replace the quoted executable path with `mvn`.
The console summary is always produced. Omit `--backtest.output` for no file.
JSON output must be inside `backtest-reports/`, which Git ignores. The directory
is created when needed. Never put a token in command arguments or reports.

Configuration:

| Property | Meaning / default |
| --- | --- |
| `backtest.competition` | `PL` (default) or `CL` |
| `backtest.from`, `backtest.to` | Required inclusive UTC dates; end cannot be in the future; maximum span 366 days |
| `backtest.max-matches` | Earliest completed fixtures in the requested range, chronologically; default 20, range 1–100 |
| `backtest.request-budget` | Hard maximum of attempted provider calls, including failures; default 25, range 1–100 |
| `backtest.interval-ms` | Minimum interval between request starts; default/minimum 6500 ms |
| `backtest.output` | Optional path under `backtest-reports/` |

### Historical retrieval and safeguards

Official documentation checked for this implementation:

- [Team match filters](https://docs.football-data.org/general/v4/team.html): dateFrom/dateTo, FINISHED status and limit up to 500.
- [Competition match filters](https://docs.football-data.org/general/v4/competition.html): explicit season and status; omitted season defaults to the current season.
- [Access and throttling policies](https://docs.football-data.org/general/v4/policies.html): subscription-dependent access and free-plan limit of 10 requests/minute.

For PL/CL, July starts the season-year boundary used for retrieval. The tool
fetches each required competition season once, newest first, covering the
requested period and its preceding 365-day baseline window. Returned fixtures
are deduplicated by ID. Each distinct selected team's cross-competition history
is fetched once for the combined date window, with limit 500; histories are
reused in memory across all evaluation matches. Local filtering still applies
each match's exact timestamp, 365-day lookback, score rules and latest-20
selection before estimation. Same-time fixtures and the evaluated match itself
never contribute. Previous-season inputs remain allowed by the existing model.

An empty match list is valid; a missing/malformed body is not. A team response
reaching 500 is unavailable because truncation cannot be ruled out. The tool
stops further calls after a provider/network/access/rate-limit failure. It never
retries automatically. Exhausted budgets or failed slices are reported as partial
runs; incomplete competition retrieval disables baseline comparisons rather than
using a partial baseline. Missing team histories never become valid empty inputs.
The pacing assumes this runner is the only client using the key; other activity
can still cause a rate limit. A run can take several minutes.

The match limit bounds selected candidates, including those later skipped for
unknown regulation scores. The report distinguishes candidates outside that
limit from selected fixtures skipped during evaluation. If retrieval fails,
unknown missing candidates cannot be counted; reported totals cover only fetched
records. No claim of complete competition or historical coverage is made.

### Outcomes, baseline and paired comparisons

Only confirmed regulation-time scores (including stoppage time) are scored,
using the existing regularTime/duration rules. Shootout/extra-time totals are
never substituted. Outcomes are home win/draw/away win, over (>2 goals)/under,
and both teams scoring at least once (Yes/No).

The reference baseline is the unsmoothed empirical outcome frequency among the
same competition's completed regulation-score matches in the preceding 365 days,
strictly before each evaluated kickoff. It needs at least 30 eligible matches.
It can span seasons but does not use the selected fixture or later results.
It is a simple comparison, not a proposed replacement model.

Model and baseline scores and calibration tables use **exactly the same paired
matches** for all three markets. Model-available matches without a usable baseline
are counted separately and excluded from both metrics. Reports include paired
match IDs, model availability, skipped counts by reason, configuration, model
version, request usage and retrieval issues. Empty evaluations return null
metrics, never a misleading zero score. A partial run may still contain valid
paired results if unrelated team retrieval failed; it remains labeled partial.

### Metric definitions

Lower scores are better. Reported scores are arithmetic means over paired matches.

- Multiclass 1X2 Brier: `sum((p_i - y_i)^2)` across all three outcomes, with no
  division by class count (range 0–2).
- Binary Brier: `(p - y)^2` for Over and BTTS Yes respectively (range 0–1).
- Log loss: `-ln(max(1e-15, probability_of_actual_outcome))`, natural logarithm.
  The epsilon makes impossible observed outcomes finite for reporting; it does
  not alter model probabilities or Brier scores. Correct probability 1 has loss 0.
- Calibration: ten bins `[0.0,0.1)`, …, `[0.9,1.0]`. Show event count, mean
  probability and observed outcome frequency; empty bins are unavailable.
  Binary tables use Over/Yes; 1X2 pools all three outcome events (three events per
  match, not independent observations). Bins with fewer than 20 events are marked
  sparse. This threshold is a display warning, not statistical certainty.

### Interpretation and verification

Subscription access can exclude competitions and older seasons. Records use
current kickoff times, scores and FINISHED statuses, not archived information
as it existed on prediction day. Timestamp filtering prevents direct future-match
leakage but cannot reconstruct corrections, postponement knowledge or the precise
historical time a result became available. Mixed competitions, opponent strength,
small samples and selection/coverage bias still affect interpretation. Do not
infer accuracy or profitability from a few matches. No odds, betting returns or
model tuning are included.

Backend sources and tests compiled. All 39 backend test methods, including mocked
HTTP tests and nine new backtesting tests, passed through direct invocation.
Full `mvn verify` remains blocked here by the uncached Surefire JUnit platform
provider 3.5.6; this is not a successful full Maven test run. No authenticated
live backtest was performed in this environment. An unauthenticated CLI smoke
test confirmed that the opt-in profile starts without a server, exits, and writes
a JSON report correctly marked partial when the API key is absent.

## Candidate experiment: blending probabilities

The backtest now scores one fixed candidate, `equal-blend-experiment-v1`:

```text
candidate probability = 0.5 * current model probability + 0.5 * baseline probability
```

This is a simple attempt to reduce dependence on noisy team venue samples by
moving each outcome toward earlier competition frequencies. The weight is fixed
in advance for all markets; no weight search or fitting is performed. It is a
hypothesis to evaluate, not an established improvement. The production Poisson
model, its thresholds, expected goals and frontend estimates remain unchanged.

The candidate uses exactly the same paired matches, prior-only baseline, regulation
scores and unavailable-data rules as both existing approaches. It preserves sums,
complements and genuine shared zeros. A missing baseline or model never becomes
a candidate estimate. There are no additional provider calls. Console and JSON
market summaries now include `candidate` alongside `model` and `baseline`.

New reports include per-match `evaluation.cases` with match ID, kickoff, actual
outcomes, current-model probabilities and baseline probabilities. This allows
later offline comparisons without retrieving those fixtures again. Earlier JSON
reports contain only aggregates: the blended Brier/log loss cannot be recovered
from those aggregate scores, so a new run is necessary to measure the candidate.
These files remain local and ignored by Git.

For a diagnostic comparison on the previously evaluated January–April window,
run from `backend` with your API key already exported:

```sh
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" spring-boot:run \
  -Dspring-boot.run.profiles=backtest \
  -Dspring-boot.run.arguments="--backtest.competition=PL --backtest.from=2026-01-01 --backtest.to=2026-04-30 --backtest.max-matches=100 --backtest.request-budget=25 --backtest.output=backtest-reports/blend-comparison.json"
```

The existing reports will be preserved. Because this candidate was proposed after
reviewing those periods, the rerun is a development comparison, not independent
validation. Any decision to deploy a change should also consider a genuinely
unexamined period; do not claim validation or profitability from these reports.

Verification: main/test sources compiled and all 41 backend test methods passed
by direct invocation, including blend mathematics, paired scoring, unavailable
data and report cases. Full Maven verification is still blocked by the uncached
Surefire provider. The candidate has not yet been measured on live historical
data in this environment; no accuracy improvement is claimed.

## Odds API connection check

Set the separate key in the same terminal used to start Spring Boot:

```sh
export ODDS_API_KEY='your-actual-odds-api-key'
```

Restart the normal backend (`mvn spring-boot:run`, without the backtest profile).
The key is read only on the backend. It is never returned to the frontend or put
in browser URLs. Existing football-data.org credentials remain separate.

List available upcoming events (no paid odds lookup):

```sh
curl 'http://localhost:8080/api/odds/events?competition=PL'
```

Use `competition=CL` for Champions League. Choose an `eventId` from that response,
then request its actual market availability:

```sh
curl 'http://localhost:8080/api/odds/events/EVENT_ID_FROM_LIST?competition=PL'
```

Replace the placeholder with the returned 32-character event ID. Odds API IDs
are different from football-data.org match IDs; the backend never guesses a
cross-provider match. These endpoints support connection/coverage checks. Automatic matching and
frontend display use the match endpoint described below.

The response includes bookmaker names, decimal prices, source update timestamps,
fetch time, the provider's remaining-credit header when supplied, and missing
markets. `h2h` is match result, `totals` is filtered to exactly 2.5 goals, and
`btts` is both teams to score. Only supported market/outcome names and finite
prices greater than 1 are retained. A market can contain only some outcomes;
missing outcomes are never invented. The Swedish region (`regions=se`) is requested, with returned bookmaker keys checked against the provider’s documented Swedish list. Availability
is not guaranteed for every fixture, bookmaker or market. A missing timestamp
means the provider did not supply one; fetch time is not the quote update time.

Official documentation: https://the-odds-api.com/liveapi/guides/v4/

The `/events` discovery endpoint does not consume usage credits. The event odds
endpoint requests three markets for one Swedish region, normally up to three credits per
uncached lookup. Both discovery and successful odds results are cached for ten
minutes. Concurrent calls are serialized to avoid duplicate requests. An
in-process guard permits at most 20 paid lookup attempts per backend run (up to
60 credits at the documented rate), and blocks a lookup if the latest known
remaining quota is below three. Failed attempts count toward the guard. These
are local safeguards, not a shared account-level budget; restarting the backend
resets the attempt counter. Cache is bounded to 50 odds events; no database or
polling is involved. Cached remaining credits are a snapshot, not a live balance.

Access, quota, network and unreadable-response errors are returned safely without
upstream bodies or URLs, which could contain the key. Provider/network failures
pause further calls for 60 seconds. No automatic retries or calls at startup occur.
No historical odds or prediction-model changes are included. This stage does not
calculate value from provider odds or make betting recommendations.

## Bookmaker odds in match details

On an upcoming match's probability section, choose **Load bookmaker odds**.
Then select a bookmaker under **Odds source**. Each outcome shows the actual
price and provider update time and uses the existing fair-odds/expected-return
comparison. Missing bookmaker outcomes remain unavailable; they are never filled
with another bookmaker's price. Choose **Manual entry** to return to typing odds.
The source selection and entered odds are local to the current match page.

This uses `GET /api/odds/matches/{footballDataMatchId}`. The backend retrieves
canonical football-data.org metadata, derives PL/CL from that metadata, then
matches the Odds API event by both oriented team names and the exact kickoff
instant. Name normalization handles accents, punctuation and standalone FC/AFC;
a few explicitly documented aliases in `OddsMatchMatcher` handle Wolves, Bayern,
Inter and Sporting. No fuzzy matching or kickoff tolerance is used. Unknown names,
ambiguous matches and missing events return a safe error and allow manual entry.
Only upcoming SCHEDULED/TIMED matches are eligible for automatic odds.

Paid lookups occur only after a unique fixture match, through the existing cached,
budget-limited service. Changing bookmakers makes no request. Loading a match or
the dashboard does not fetch odds automatically. An explicit retry/check reuses
the ten-minute backend cache; it does not force a paid refresh. Each button action
also retrieves football-data.org match metadata, so avoid repeated checks.

Quotes older than 15 minutes, future-dated or missing a valid source timestamp
are shown but excluded from automated value comparisons. A local timer checks
quote age; it makes no network requests. Missing markets, coverage failures,
loading, empty results and retry remain visible without blocking manual entry or
model estimates. Exchange commission/account-specific conditions are not modeled;
prices do not imply eligibility or a betting recommendation. Model calculations
and sample thresholds are unchanged; all secrets remain backend-only.

### Football-data.org rate limits when loading odds

Odds loading verifies the selected match against football-data.org before spending
Odds API credits. These are separate providers with separate limits. Repeated
navigation can exhaust the football-data.org allowance even when Odds API credits
remain available.

Successful football-data.org match metadata, fixture lists and standings responses
are now cached for two minutes (maximum 32 request/type entries). Concurrent
identical requests reuse the result; performance and odds lookups share canonical
match metadata. Team histories keep their existing two-minute completeness-aware
cache. Provider failures are not stored as successful data. A football-data.org
429 starts a 60-second local cooldown for uncached requests; existing cached
responses remain available. This reduces duplicate requests but does not guarantee
that uncached navigation stays within the account-wide rate limit.

If the page reports a football-data rate limit, wait at least one minute before
retrying. Restart Spring Boot after installing these changes; avoid repeated
refreshes or simultaneous backtests using the same key.


### Swedish bookmaker filter

The backend requests `regions=se`, the provider's Swedish region, rather than
the broad European feed or a two-bookmaker subset. The returned keys are checked
against the current documented Swedish list: ATG, Betinia, BetMGM, Betsson,
CampoBet, Nya Expekt, Hajper, LeoVegas, Mr Green, NordicBet, 888sport, Svenska Spel
and Unibet. This represents this provider's supported Swedish entries, not a
claim that every Swedish operator or fixture is covered. Missing markets remain
unavailable with manual entry as a fallback. Restart the backend and reload odds
to clear the old cached response.

Official coverage: https://the-odds-api.com/sports-odds-data/bookmaker-apis.html#se-bookmakers

## Outcome overview

Match details now show **Most likely**, sorted by the unrounded experimental
probabilities, and **Potential value**, sorted by positive expected return for
the selected bookmaker. Both use existing responses and comparison helpers;
there are no additional API calls. Loading odds and choosing a bookmaker remain
explicit actions. Manual odds comparisons stay in the individual outcome cards.

Missing/invalid probabilities are excluded while genuine zeros are preserved.
Value rankings require an exact bookmaker/market/outcome match and a fresh source
quote; stale, future-dated, missing or invalid prices cannot enter the list.
A local timer removes aging quotes without polling the server. Empty comparisons,
loading and errors have explanatory messages. Quotes display their source update
time. Markets overlap, so the lists are not independent picks or a combined bet.
Positive expected return depends on model accuracy and is not a recommendation.

Verification for this frontend change: all 31 frontend tests and production build
passed. No backend changes were required; live browser verification was not run.


### Value ranking within a match

The overview ranks bet types for the selected match using only the selected
bookmaker's prices. The bet name and estimated expected-return percentage are
prominent; probability, odds, fair odds and update time are supporting details.
It never chooses another bookmaker's price or compares bookmakers. Changing the
selector recalculates the ranking locally without API calls. No selection,
missing/stale prices, or no positive expected returns produce explanatory empty
states. Manual comparisons remain in the individual cards. These are experimental
model estimates, not guarantees or recommendations.

### Diagnosing missing bookmaker markets

Odds responses now include safe `diagnostics`: each returned Swedish bookmaker's
market keys, returned outcome count, goal lines received before filtering, and
accepted price count. For a shorter view use:

```text
GET /api/odds/events/{eventId}/diagnostics?competition=PL
```

This shares the normal odds response cache and request safeguards. It makes no
additional call if the odds are already cached, otherwise it performs the normal
three-market lookup. No API key, upstream URL or raw error body is exposed.

For Svenska Spel, a `totals` entry with `goalLines: [3.5]` and `acceptedPrices: 0`
means the provider returned a different line, which cannot be used as 2.5 odds.
No `btts` entry means that market was absent in that response; it does not mean
the bookmaker's own site lacks it. Empty markets and rejected invalid outcomes
can also be distinguished by the counts. `missingMarkets` on the main response
is aggregated across bookmakers, so use the per-bookmaker diagnostic for this
investigation. `alternate_totals` is not requested and its availability cannot
be inferred from this diagnostic. No alternate-market request or increased quota
usage has been enabled without first checking the actual provider response.
