# Coding Standards — Java Spring Boot

## Language and Runtime
- Java 17+, Spring Boot 3.x, Maven (never Gradle).
- Use `var` for local variables only when the type is obvious from the right-hand side.

## Project Structure
- Package layout: `controller` → `service` → `repository`.
- No business logic in controllers. Controllers only validate input and delegate.
- DTOs for request/response; never expose entity classes directly in REST responses.

## Testing
- Unit tests: Spock (Groovy). File names must end in `Spec.groovy`.
- Place specs in `src/test/groovy` mirroring the main package.
- Every new public method in a service or repository must have at least one Spock spec.
- Do not use Mockito — use Spock's `Mock()` and `Stub()`.

## Database
- Flyway migrations in `src/main/resources/db/migration/`.
- Filename format: `V{next-number}__{snake_case_description}.sql`.
- Always use `IF NOT EXISTS` on `CREATE TABLE` and `CREATE INDEX`.
- Never modify existing migration files; always create a new one.

## Logging
- Use SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`) .
- Log at INFO for normal business events, WARN for recoverable issues, ERROR for failures.
- Never log: passwords, tokens, raw user input, PII.

## Dependencies
- Before adding any new Maven dependency, check `pom.xml` first.
- Never introduce a dependency already provided by Spring Boot's BOM.
- No snapshot or milestone versions in production code.

## Security
- Never hardcode credentials, tokens, or URLs. Use `${ENV_VAR}` or `@Value("${...}")`.
- Sanitize and validate all user input at the controller layer with Bean Validation (`@Valid`).

## Error Handling
- Use a `@ControllerAdvice` / `@RestControllerAdvice` for global exception handling.
- Return `ProblemDetail` (RFC 7807) for error responses.
- Never swallow exceptions silently.

## Commits
- Commit message format: `<JIRA-KEY>: <short imperative description>`
- Example: `PROJ-42: Add email validation to signup endpoint`
