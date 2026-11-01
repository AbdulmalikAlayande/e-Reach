# Shared

Code that both servers, or several modules of one server, need. Something belongs here only when more than one place uses it; anything one module needs stays in that module (ARCHITECTURE.md, rule 14; ADR-019).

| Package | What it holds |
| --- | --- |
| `id` | `Ids.newId()` (UUIDv7), the typed IDs `PersonId`, `FacilityId`, `UserId`, `ClinicId`, and `SystemActors`, the fixed IDs of the four system accounts that V3 inserts |
| `money` | `Money`, whole kobo in a `long`, with the PRD's rounding rules |
| `entities` | `BaseEntity`: `row_version` with `@Version`, and the four author columns read back with Hibernate's `@Generated` |
| `converters` | JPA converters for the typed IDs |
| `errors` | The RFC 9457 error module: `ProblemType`, `CommonProblem`, `EReachException`, `RecordNotFoundException`, `EReachProblems` |
| `errors.web` | `EReachExceptionHandler`, which turns every exception from a controller into a problem |
| `config` | `ErrorHandlingAutoConfiguration`, listed in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, so any Spring Boot application that depends on `shared` gets the handler without scanning this package |

## Adding a problem type

A module that needs its own errors adds an enum that implements `ProblemType` (see `facility-server`'s `iam.errors.IamProblem`) and thin exceptions that extend `EReachException`. `CommonProblem` is only for problems any module, in either server, can report.

## Tests

`EreachMoneyModuleTests` and `UuidModuleTests` hold TC-004-01 to TC-004-21. Run them from `server/` with `.\mvnw.cmd -pl shared test`.
