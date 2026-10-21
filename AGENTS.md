# Project conventions

## Scope and structure
- Frontend: React, Vite, JavaScript, Tailwind CSS, Lucide React, Recharts.
- Backend: Java 25, Spring Boot, Maven, PostgreSQL, Spring Data JPA.
- Keep frontend code in `frontend/` and backend code in `backend/`.
- Keep code beginner-friendly. Prefer small functions and straightforward solutions.
- Add application features only when requested. Do not add unnecessary dependencies.

## Language and style
- Use English for code, comments, user-facing text, documentation, and commit messages.
- Use functional React components, PascalCase component files, and camelCase variables.
- Use Java package `com.tonyle.footballbettinganalytics` and standard Java naming conventions.
- Use Tailwind for styling. Preserve the mobile-reference sports theme: charcoal backgrounds and panels, deep red headers, charcoal navigation, white text, and muted red accents.
- Add comments only when they explain a non-obvious decision.

## Configuration and data
- Keep credentials and football-data.org API calls on the backend.
- Never commit API keys, passwords, `.env` files, or build output.
- Use environment variables for secrets. Spring Boot does not automatically load `.env` files.
- The default `setup` profile starts without PostgreSQL. Use `database` for JPA work.
- Do not use automatic schema updates or introduce mock data without discussing the need.

## Verification and Git
- Run `npm run build` in `frontend/` after frontend changes.
- Run `mvn verify` in `backend/` after backend changes.
- Add meaningful tests for behavior when features are implemented.
- Report failed or blocked checks honestly; do not claim unrun checks passed.
- Do not commit, push, or create a remote repository without the user's approval.
