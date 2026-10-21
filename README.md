# Football Betting Analytics

A React and Spring Boot portfolio application for Premier League and UEFA Champions League football analytics.
Matches and standings come from football-data.org API v4 through the backend.
The chart and prediction cards still use clearly labeled fictional samples; no
prediction model or odds integration has been implemented.

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
