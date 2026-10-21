# Football Betting Analytics

A React and Spring Boot portfolio application for Premier League football analytics.
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
