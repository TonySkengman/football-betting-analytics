# Football Betting Analytics

A fullstack portfolio project for football statistics and betting analysis.
This first step contains project scaffolding only: no statistics, predictions,
API endpoints, database entities, or external API requests are implemented.

## Stack

- Frontend: React, Vite, JavaScript, Tailwind CSS, Lucide React, Recharts.
- Backend: Java 25, Spring Boot 4.1.1, Maven, Spring Data JPA, PostgreSQL driver.
- Planned football data source: football-data.org.

## Structure

```text
frontend/     React application and Vite configuration
backend/      Spring Boot application and Maven configuration
AGENTS.md     Project conventions for coding assistants
```

## Requirements

- Node.js 24 LTS and npm.
- JDK 25.
- Maven 3.6.3 or newer (or IntelliJ's bundled Maven).
- Internet access for the first dependency installation.
- PostgreSQL is only needed when using the optional database profile.

## Start the frontend

From the project root:

```sh
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The page is a minimal dark setup placeholder.
Lucide React and Recharts are declared dependencies, reserved for future features.
Vite forwards future `/api` requests to http://localhost:8080 during development.
Production API routing will need configuration when deployment is added.

## Start the backend

In a separate terminal, from the project root:

```sh
cd backend
mvn spring-boot:run
```

If `mvn` is unavailable and IntelliJ IDEA is installed at the standard macOS path:

```sh
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" spring-boot:run
```

The backend listens on http://localhost:8080. No endpoints have been implemented,
so visiting `/` will return HTTP 404. Stop either process with Ctrl+C.

The default `setup` profile disables database auto-configuration so that the empty
application can start without PostgreSQL. JPA and the PostgreSQL driver are
already declared in the Maven dependencies.

## Optional PostgreSQL configuration

When database work begins, create a PostgreSQL user and an empty database named
`football_betting_analytics`, then run the following in the backend terminal:

```sh
export DB_URL=jdbc:postgresql://localhost:5432/football_betting_analytics
export DB_USERNAME=your_database_user
export DB_PASSWORD=your_database_password
mvn spring-boot:run -Dspring-boot.run.profiles=database
```

Use the same bundled Maven path above if Maven is not on your PATH. The database
profile validates the schema and does not create or modify tables. No entities
or schema migrations are included yet.

For future API integration, set `FOOTBALL_DATA_API_KEY` in the backend environment.
It is not needed for this setup. Never put the key in frontend code or a `VITE_`
variable. Spring Boot does not automatically load `.env` files.

## Build

Run in `frontend/`:

```sh
npm run build
```

Run in `backend/`:

```sh
mvn verify
```

The frontend output goes to `frontend/dist/`. The executable backend JAR goes
to `backend/target/`. No tests are included yet because no behavior is implemented.
Commit the generated `frontend/package-lock.json` after a successful installation.

## Initial verification status

Setup was attempted in a restricted environment:

- `npm install` failed with `ENOTFOUND registry.npmjs.org`. Offline installation
  also failed because required packages were not cached. No lockfile was generated.
- The frontend build could not run because Vite was not installed.
- Maven verification using IntelliJ's bundled Maven failed while resolving the
  Spring Boot parent POM: `repo.maven.apache.org` could not be resolved.
- `git init -b main .` was denied with `.git: Operation not permitted`.

Neither build nor application startup has been verified. Retry installation and
both builds from a normal terminal with network access. Dependency versions may
need adjustment if the package registry reports compatibility errors.

## Git

The setup environment prevented creation of the project repository. Before
staging files, run this from the project root in your terminal:

```sh
git init -b main .
git rev-parse --show-toplevel
```

The reported root must be this project's folder. Git previously detected an
ancestor repository; do not stage files until this project has its own repository.
No commits, pushes, or remote repositories have been created.
