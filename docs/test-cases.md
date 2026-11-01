# e-Reach Test Cases

> This file in `docs/` is the original; the earlier shared copy stopped being updated on 27/10/2026. Statuses marked "Written" mean the test exists in the code but no run has been recorded here yet; the next `mvnw verify` decides them.

Oct 17, 2026 · @Abdulmalik Alayande

This is the acceptance book for e-Reach. Every ticket gets its test cases here before any code is written, and a ticket is finished only when every one of its cases passes as an automated test.

## How this works

The product owner writes what "done" looks like; the developer builds until it is true; nobody argues afterwards about whether the work is finished, because the test cases already said. In this project the reviewer plays product owner, and Abdulmalik plays developer and tester.

Every ticket goes through the same six steps:

1. **Cases written.** Before any code, the ticket's test cases appear in this book, each traced to the PRD rule or feature it proves.
2. **Cases challenged.** The developer reads every case and pushes back on anything unclear, wrong or missing, in a comment on that case. This is the "three amigos" conversation: business (what problem?), development (how to build it?) and testing (what could go wrong?) agree before work starts. A case that survives the challenge is fixed for the ticket.
3. **Tests first.** Each case becomes an automated test whose display name starts with its ID, for example `@DisplayName("TC-004-12 co-payment follows the PRD formula")`. Run it and watch it fail. A test that has never failed has never proved anything.
4. **Build until green.** Write the smallest code that makes each failing test pass, then tidy it while the tests stay green.
5. **Hand back.** The developer comes back only when `.\mvnw.cmd -B verify` passes with every case for the ticket, and sets each case's status to Passing. Bring the `verify` output and the commit or pull request link.
6. **Review.** The reviewer checks the code against the cases and PRD section 8. Accepted means the ticket's status in the PRD moves to Done.

Three rules keep this honest:

- **Never change a case to make it pass.** If a case is wrong, say so in a comment and agree a new version first.
- **A skipped test is a failing test.** No `@Disabled`, no commented-out assertions.
- **The Definition of Done in PRD section 14 still applies on top.** Test cases say what a ticket must do; the Definition of Done says how well every ticket must be built.

## How a test case is written

Each case is one concrete example of the behaviour, written so the product owner can read it and the developer can turn it straight into a test. The format joins two established practices.

The **content** follows the test-case anatomy of the ISO/IEC/IEEE 29119-3 testing standard: identifier, objective, preconditions, inputs, steps, expected result and traceability. The **wording** follows Given-When-Then from behaviour-driven development: Given sets up the state, When is the one action being tested, Then is the observable result. Where one rule needs checking with several values, a table of examples replaces copies of the same case, as Gherkin's Scenario Outline does.

| Column | Holds | From |
| --- | --- | --- |
| ID | `TC-<ticket number>-<nn>`, never reused | 29119-3 identifier |
| Checks | The objective, in one line | 29119-3 objective |
| Given | Preconditions and inputs | 29119-3 preconditions, inputs |
| When | The single action | 29119-3 steps |
| Then | The expected, observable result | 29119-3 expected result |
| Level | Unit, integration, API, build, or review (checked by reading code, not by a test) | Test pyramid in PRD rule 18 |
| Traces to | The PRD rule, feature or decision record it proves | 29119-3 traceability |
| Status | Not written, Failing or Passing, set by the developer |  |

We write Given-When-Then in this book but implement each case as a JUnit test, not as a Cucumber feature file. Cucumber would let the book run as code, but it adds a tool and a second language for one developer to maintain; the IDs in each test's display name give the same traceability for less.

## Ticket ER-004a: the shared module, typed IDs and Money

**Goal:** every later ticket can create an ID and handle an amount of money without thinking about it, and cannot get either wrong.

**In scope:** a new Maven module `server/shared` (artifact `shared`) that `facility-server` depends on; `Ids.newId()`; the typed IDs `PersonId`, `FacilityId` and `UserId`; the `Money` type. **Out of scope** (later, as ER-004b): the RFC 9457 error handler and the base entity with standard columns, because both need Spring and a database, and this ticket needs neither.

| ID | Checks | Given | When | Then | Level | Traces to | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| TC-004-01 | The shared module is part of the build | `server/pom.xml` lists `shared`, and `facility-server` depends on `shared` | `.\mvnw.cmd validate` runs in `server/` | The reactor lists e-Reach, then ereach-shared, then facility-server, all SUCCESS | Build | PRD section 7 | Passing (01/11/2026) |
| TC-004-02 | The shared module stays small (version 2, 01/11/2026, under D-003; version 1 allowed only `uuid-creator` in compile scope) | The shared module's `pom.xml` | `.\mvnw.cmd -pl shared dependency:tree` runs in `server/` | Of the dependencies `shared` declares itself, `uuid-creator` is the only required compile dependency, the Spring Boot starters are compile and optional, and everything else is test scope | Build | PRD section 7, D-003 | Passing (01/11/2026) |
| TC-004-03 | New IDs are UUIDv7 | Nothing | `Ids.newId()` is called | The UUID's `version()` is 7 and its `variant()` is 2 | Unit | Rule 4, ADR-006 | Passing |
| TC-004-04 | IDs from different milliseconds sort in creation order, the way PostgreSQL sorts them | ID `a` is created, then at least 2 ms pass | ID `b` is created | `a.toString()` sorts before `b.toString()` | Unit | Rule 4 | Passing |
| TC-004-05 | IDs are unique under load | Nothing | 100,000 IDs are created in a tight loop | All 100,000 values are distinct | Unit | Rule 4 | Passing |
| TC-004-06 | Typed IDs refuse null | Nothing | `new PersonId(null)` is called (and the same for `FacilityId`, `UserId`) | An `IllegalArgumentException` is thrown | Unit | Rule 4 | Passing |
| TC-004-07 | Typed IDs compare by value and by type | Two `PersonId`s wrapping the same UUID, and a `FacilityId` wrapping that UUID too | They are compared with `equals` and `hashCode` | The two `PersonId`s are equal with equal hash codes; the `PersonId` and the `FacilityId` are not equal | Unit | Rule 4 | Passing |
| TC-004-08 | A typed ID reads and prints as a plain UUID | A valid UUID string | `PersonId.of(text).toString()` is called | It returns exactly the same text, not `PersonId[value=...]` | Unit | Rule 4, section 10 | Passing |
| TC-004-09 | Bad ID text is refused | The text `not-a-uuid` | `PersonId.of(text)` is called | An `IllegalArgumentException` is thrown | Unit | Rule 4 | Passing |
| TC-004-10 | Each typed ID can make a fresh one | Nothing | `PersonId.newId()` is called | It returns a `PersonId` whose value is version 7 | Unit | Rule 4 | Passing |
| TC-004-11 | Money adds and subtracts exactly | 10 kobo and 20 kobo | They are added, then 20 kobo is subtracted from the sum | The sum is 30 kobo; the difference is 10 kobo | Unit | Rule 8 | Passing |
| TC-004-12 | Money never wraps around | `Money.ofKobo(Long.MAX_VALUE)` | 1 kobo is added | An `ArithmeticException` is thrown, never a negative amount | Unit | Rule 8 | Passing |
| TC-004-13 | Co-payment matches the PRD's worked example | 400,000 kobo (₦4,000) | `percentBps(1000)` is called (10%) | The result is 40,000 kobo | Unit | F13 step 6 | Passing |
| TC-004-14 | Co-payment rounds half up | Each row of the table below | `percentBps` is called | The result matches the table | Unit | F13 step 6 | Passing |
| TC-004-15 | Basis points stay between 0 and 10,000 | 1,000 kobo | `percentBps(-1)` and `percentBps(10001)` are called | Both throw `IllegalArgumentException` | Unit | Rule 8 | Passing |
| TC-004-16 | A percentage of a negative amount is refused | −100 kobo | `percentBps(1000)` is called | An `IllegalArgumentException` is thrown | Unit | F13 step 6 | Passing |
| TC-004-17 | Patient and payer shares always add back to the amount | 10,000 random pairs: an amount from 0 to 10¹² kobo and basis points from 0 to 10,000 | `copay = amount.percentBps(bps)` and `payer = amount.minus(copay)` | `copay.plus(payer)` equals the amount, and neither share is negative | Unit | F13 step 7 | Passing |
| TC-004-18 | Money compares by value | Two `Money.ofKobo(100)` | They are compared with `equals` and `hashCode` | They are equal with equal hash codes | Unit | Rule 8 | Passing |
| TC-004-19 | No floating point near money | The source of `Money` | It is read in review | No `float`, `double` or `BigDecimal` appears in any constructor, factory or method signature, and the stored value is a `long` | Review | Rule 8 | Passing |
| TC-004-20 | A percentage too large for a long is refused, never wrapped | Money.ofKobo(922\_337\_203\_685\_477) and Money.ofKobo(922\_337\_203\_685\_478) | percentBps(10\_000) is called on each | The first returns the same amount; the second throws ArithmeticException | Unit | Rule 8, F13 step 6 | Passing |
| TC-004-21 | Money never changes after it is made | Money.ofKobo(100) | plus, minus and percentBps are called on it | Each returns a new Money with the right value, and the original still holds 100 kobo | Unit | Rule 8 | Passing |

**Examples for TC-004-14.** The PRD's formula is `(amount × bps + 5000) / 10000` in whole-number division.

| Amount (kobo) | Basis points | Expected (kobo) | Why this row exists |
| --- | --- | --- | --- |
| 15 | 5000 | 8 | 7.5 rounds up |
| 14 | 5000 | 7 | 7.0 stays |
| 1 | 4999 | 0 | 0.4999 rounds down |
| 1 | 5000 | 1 | exactly half rounds up |
| 999 | 10000 | 999 | 100% returns the whole amount |
| 999 | 0 | 0 | 0% returns nothing |
| 0 | 10000 | 0 | nothing stays nothing |

## Ticket ER-004b: one error shape for every response

**Goal:** prove PRD section 10 and ADR-019: every error is an RFC 9457 problem with the e-Reach code, and nothing internal leaks. The cases run the shared handler against a small test controller, with no database, except TC-004B-06.

| ID | Checks | Given | When | Then | Level | Traces to | Class | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-004B-01 | An e-Reach problem has every member | A controller throws `EReachException(STALE_RECORD, …)` with the extension `currentRowVersion` | the request is made | 412; `application/problem+json`; `type` `https://ereach.ng/problems/stale-record`; the fixed title; `status` 412; the detail; `instance` starts `urn:uuid:`; `code` `STALE_RECORD`; `currentRowVersion` at the top level, and no `properties` object | API | Section 10, ADR-019 | ProblemDetailsTests | Not written |
| TC-004B-02 | Invalid fields come back together | A request body with two blank required fields | it is posted | 400 `VALIDATION_FAILED`, detail "2 fields are invalid.", an `errors` list of two field and message pairs, no rejected values | API | Section 10 | ProblemDetailsTests | Not written |
| TC-004B-03 | Unreadable JSON | A body that is not JSON | it is posted | 400 `MALFORMED_REQUEST`; the parser's message is not in the response | API | Section 10 | ProblemDetailsTests | Not written |
| TC-004B-04 | Nothing internal leaks | A controller throws an unexpected exception whose message holds an internal address | the request is made | 500 `INTERNAL_ERROR`; the detail holds the `urn:uuid:` reference and not the exception's message | API | Section 10, RFC 9457 section 5 | ProblemDetailsTests | Not written |
| TC-004B-05 | Framework errors keep about:blank | An existing path called with the wrong method | the request is made | 405; `type` `about:blank`; title "Method Not Allowed"; `code` `HTTP_405`; an `instance` | API | Section 10 | ProblemDetailsTests | Not written |
| TC-004B-06 | The handler is registered by itself | The facility server starts | the application context is loaded | It holds exactly one `EReachExceptionHandler` bean, created through `ErrorHandlingAutoConfiguration` | Integration | ADR-019 | ProblemDetailsTests or a context test | Not written |
| TC-004B-07 | Security errors share the shape | A controller throws Spring Security's `AccessDeniedException` | the request is made | 403 `ACCESS_DENIED` problem, not a 500 | API | Section 10, F3 | a test in `iam` | Not written |

## Ticket ER-006, part 2: proving the guard

**Goal:** prove, on every build, that the database itself keeps each facility's rows apart, and that the platform tables refuse bad data. These cases run against `V2__facility_and_clinic.sql` alone; the transaction hook, the entities and the API come later in ER-006.

**Classes:** `ng.ereach.facilityserver.platform.FacilityIsolationTests` holds TC-006-01 to TC-006-13, the guard itself. `ng.ereach.facilityserver.platform.PlatformSchemaRulesTests` holds TC-006-14 to TC-006-28, the table rules. Both extend `PostgresIntegrationTest`.

**Shared setup.** Each test starts from the migrated schema. As `ereach_owner`, insert states LA and OG with one LGA each, the way `R__` files will later. As `ereach_app`, insert facility A (prefix AH, national code NG-001) and facility B (prefix BH, no national code); then, with A set, clinics GOPD and ANC and the setting `lab.verification_required`; then, with B set, clinic PAED and the same setting. "A set" means the transaction began with `SELECT set_config('app.facility_id', '<A>', true)`. Every case connects as `ereach_app` unless it says otherwise.

| ID | Checks | Given | When | Then | Level | Traces to | Class | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-006-01 | Nothing set, nothing read | No facility set | clinic and facility\_setting are counted | Both counts are 0 | Integration | F1, rule 1 | FacilityIsolationTests | Passing |
| TC-006-02 | An empty setting counts as none | app.facility\_id set to the empty text '' | clinic and facility\_setting are counted | Both counts are 0 | Integration | Rule 1 | FacilityIsolationTests | Passing |
| TC-006-03 | Facility A sees only its own rows | A set | clinics are listed and settings counted | Exactly GOPD A and ANC A; 1 setting | Integration | F1, rule 1, ADR-003 | FacilityIsolationTests | Passing |
| TC-006-04 | An unknown facility sees nothing | app.facility\_id set to a UUID that no facility has | clinics are counted | The count is 0 | Integration | Rule 1 | FacilityIsolationTests | Passing |
| TC-006-05 | A malformed setting fails loudly | app.facility\_id set to 'not-a-uuid' | clinics are selected | The query fails with an invalid-UUID error; no rows are returned | Integration | Rule 1 | FacilityIsolationTests | Passing (01/11/2026; the test body was empty before) |
| TC-006-06 | The setting ends with its transaction | One connection: A set, clinics counted, transaction committed | clinics are counted again on the same connection | 2 inside the transaction, 0 after it | Integration | F1, rule 1 | FacilityIsolationTests | Passing |
| TC-006-07 | No row can be written for another facility | A set | a clinic stamped with B is inserted | Refused: new row violates row-level security policy | Integration | Rule 1, ADR-003 | FacilityIsolationTests | Passing |
| TC-006-08 | No row can be written with no facility set | No facility set | a clinic stamped with A is inserted | Refused: new row violates row-level security policy | Integration | Rule 1 | FacilityIsolationTests | Passing |
| TC-006-09 | A row cannot be moved to another facility | A set | GOPD A's facility\_id is updated to B | Refused: new row violates row-level security policy | Integration | Rule 1 | FacilityIsolationTests | Passing |
| TC-006-10 | Another facility's row cannot be changed by its ID | A set | PAED B is renamed by its id | 0 rows updated; read as B, its name is unchanged | Integration | F1, rule 1 | FacilityIsolationTests | Passing |
| TC-006-11 | Nothing is hard-deleted | A set | DELETE FROM clinic runs | Refused: permission denied for table clinic | Integration | Rule 7 | FacilityIsolationTests | Passing |
| TC-006-12 | The owner is guarded too | Connected as ereach\_owner, no facility set | clinics are counted | The count is 0, because the tables are forced | Integration | Rule 1, rule 18 | FacilityIsolationTests | Passing |
| TC-006-13 | Every facility table has its guard | The migrated schema | the catalogue is read: every table in public with a facility\_id column | Each one has row-level security enabled and forced and at least one policy; the list of offenders is empty | Integration | F1, ADR-003 | FacilityIsolationTests | Passing |
| TC-006-14 | Clinic codes are unique within a facility | A set | a second GOPD is inserted at A | Refused: ux\_clinic\_code | Integration | F1 | PlatformSchemaRulesTests | Passing |
| TC-006-15 | Two facilities may use the same clinic code | A has GOPD; B set | B inserts its own GOPD | Accepted; A still sees one GOPD, B sees its own | Integration | F1 | PlatformSchemaRulesTests | Passing |
| TC-006-16 | A soft-deleted clinic frees its code | A set | ANC A gets deleted\_at, then a new ANC is inserted | Accepted; exactly 1 ANC without deleted\_at | Integration | Rule 7 | PlatformSchemaRulesTests | Passing |
| TC-006-17 | Clinic codes follow the format | A set | codes 'gopd', 'G' and one of 21 letters are inserted | All three refused: clinic\_code\_check | Integration | Batch 2 V2 | PlatformSchemaRulesTests | Passing |
| TC-006-18 | A clinic needs a real facility | app.facility\_id set to a UUID that no facility has | a clinic stamped with that UUID is inserted | Refused: clinic\_facility\_id\_fkey | Integration | Rule 1 | PlatformSchemaRulesTests | Passing |
| TC-006-19 | The facility list is readable before a facility is chosen | No facility set | facilities are counted | The count is 2 (facility has no row-level security, so sign-in can list memberships' facilities) | Integration | F1, F2, Batch 2 V2 | PlatformSchemaRulesTests | Passing |
| TC-006-20 | Hospital number prefix rules hold | Facility A has prefix AH | facilities with prefixes 'gh', 'ABCDEFG' and 'AH' are inserted | All three refused: two by the format check, one by ux\_facility\_prefix | Integration | F1 | PlatformSchemaRulesTests | Passing |
| TC-006-21 | The national facility code is optional and unique | A has code NG-001; B has none | facility C with NG-001 and facility D with no code are inserted | C refused by ux\_facility\_nhfr\_code; D accepted | Integration | F1 | PlatformSchemaRulesTests | Passing |
| TC-006-22 | Blank codes are refused | None | a facility with national facility code '   ' is inserted | Refused: facility\_nhfr\_code\_check | Integration | F1 | PlatformSchemaRulesTests | Passing |
| TC-006-23 | Registration number and agency come together | None | facilities are inserted with a number only, an agency only, and both | The first two refused by facility\_check; the third accepted | Integration | F1 | PlatformSchemaRulesTests | Passing |
| TC-006-24 | A facility's LGA lies in its state | States LA and OG, each with one LGA | a facility with state LA and an Ogun LGA is inserted | Refused: fk\_facility\_lga | Integration | F1, Batch 2 V2 | PlatformSchemaRulesTests | Passing |
| TC-006-25 | Facility type is a closed list | None | a facility of type 'hospital' is inserted | Refused: facility\_type\_check | Integration | F1 | PlatformSchemaRulesTests | Passing |
| TC-006-26 | Unknown setting keys are refused | A set | a setting with key 'foo.bar' is inserted | Refused: facility\_setting\_key\_check | Integration | F1 | PlatformSchemaRulesTests | Passing (01/11/2026; before, the test used columns that do not exist) |
| TC-006-27 | One value per setting key per facility | A set; A already has lab.verification\_required | a second lab.verification\_required is inserted | Refused: ux\_facility\_setting\_key | Integration | F1 | PlatformSchemaRulesTests | Passing |
| TC-006-28 | Reference tables are read-only for the application | None | the application inserts into state and updates lga | Both refused: permission denied | Integration | F1, Batch 2 V2 | PlatformSchemaRulesTests | Passing |

Every Then above was checked by running it on a real PostgreSQL before it was written here.

## Ticket ER-006, part 3: the transaction hook

**Goal:** prove that every transaction gets the facility and user of its context, that no context means no rows, and that nothing leaks between transactions or threads. **Class:** `ng.ereach.facilityserver.platform.TransactionContextHookTest`, extending `PostgresIntegrationTest`; Primex (A) has CARD and ORTHO, Novia (B) has DENT and PSYCH.

| ID | Checks | Given | When | Then | Level | Traces to | Class | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-006-29 | Facility A's context reads only A | Context: facility A, user U | a transaction reads all clinics | Exactly CARD and ORTHO, both Primex | Integration | Rule 1, ADR-003 | TransactionContextHookTest | Passing |
| TC-006-30 | Facility B's context reads only B | Context: facility B, user U | a transaction reads all clinics | Exactly DENT and PSYCH, both Novia | Integration | Rule 1, ADR-003 | TransactionContextHookTest | Passing |
| TC-006-31 | The hook sets app.facility\_id | Context: facility A | the transaction reads current\_setting('app.facility\_id') | It equals A's ID as text | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-32 | The hook sets app.user\_id | Context: user U | the transaction reads current\_setting('app.user\_id') | It equals U's ID as text | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-33 | No context reads nothing | No context | a transaction reads all clinics | 0 rows, and no error | Integration | Rule 1, F1 | TransactionContextHookTest | Passing |
| TC-006-34 | No context leaves the setting empty | No context | current\_setting('app.facility\_id', true) is read | It is empty or NULL | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-35 | Sign-in context: a user and no facility | Context: user U only | a transaction reads both settings and the clinics | app.user\_id is U, app.facility\_id is '', and 0 clinics | Integration | Rule 1, F2 | TransactionContextHookTest | Passing |
| TC-006-36 | No context cannot write | No context | a clinic stamped with A is inserted | Refused with 42501 | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-37 | Writing in the active facility works | Context: facility A | a new clinic for A is inserted | Accepted, and readable under A | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-38 | Writing into another facility is refused | Context: facility A | a clinic stamped with B is inserted | Refused with 42501 | Integration | Rule 1, ADR-003 | TransactionContextHookTest | Passing |
| TC-006-39 | A row cannot be moved to another facility | Context: facility A | ORTHO's facility\_id is updated to B | Refused with 42501 | Integration | Rule 1 | TransactionContextHookTest | Failing |
| TC-006-40 | Another facility's rows cannot be changed or deleted | Context: facility A | B's PSYCH is renamed, then deleted | The rename affects 0 rows; the delete is refused with 42501; read as B, PSYCH is unchanged | Integration | Rule 1, rule 7 | TransactionContextHookTest | Passing |
| TC-006-41 | A committed context does not leak | A transaction with A has committed | the next transaction runs with no context | Both settings are empty and clinics return 0 rows | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-42 | A rolled-back context does not leak | A transaction with A rolled back after an exception | the next transaction runs with no context | app.facility\_id is empty | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-43 | A then B sees only B | A transaction with A has finished | the next transaction runs with B | Only DENT and PSYCH come back | Integration | Rule 1 | TransactionContextHookTest | Failing |
| TC-006-44 | The user does not leak | A transaction with user U has finished | the next transaction runs with no user | app.user\_id is empty | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-45 | The context ends with its block | The facility A block has ended | a transaction runs | Clinics return 0 rows | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-46 | Switching facility on one thread | A, then B, on the same thread | the second transaction reads clinics | It sees only B | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-47 | Other threads never see the context | Facility A on this thread | another thread reads the context | It sees no context | Unit | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-48 | Parallel threads stay apart | Two threads at once, one as A and one as B | each reads clinics 50 times | Every read sees only its own facility's clinics | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-49 | A joined transaction keeps the context | An outer transaction with A | an inner method joins it | Same transaction, app.facility\_id is A, only A's clinics | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-50 | A REQUIRES\_NEW transaction gets the context too | An outer transaction with A | an inner method starts a new transaction | A different transaction, still with A and only A's clinics | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-51 | A read-only transaction gets the context too | Context: facility A | a read-only transaction reads clinics | app.facility\_id is A, only A's clinics | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-52 | No transaction, no context | A in the holder, no transaction | clinics are read | 0 rows: the context only arrives through the transaction manager | Integration | Rule 1 | TransactionContextHookTest | Passing |
| TC-006-53 | The facility table needs no context | No context | the facility table is read | Both facilities come back (facility has no RLS) | Integration | F1, F2 | TransactionContextHookTest | Passing |
| TC-006-54 | The hook breaks nothing else | The hook is the application's transaction manager | mvnw verify runs the whole build | Every test passes | Build | Section 7 | The whole build | Passing (01/11/2026) |
| TC-006-55 | A failed context fails the transaction cleanly | The context statement fails | a transaction starts | CannotCreateTransactionException with the real cause inside; nothing left bound to the thread; the next transaction works | Integration | Rule 1 | TransactionContextHookTest | Failing |

Statuses come from the developer's run of 26/10/2026. V2 has changed since, so the next full run confirms them.

## Ticket ER-006, part 4: author columns

**Goal:** prove PRD section 8 rule 19 and ADR-018: the database fills `created_by`, `created_at`, `updated_by` and `updated_at`, and no write goes in without an author. Each case runs inside a context with a user, except TC-006-56.

| ID | Checks | Given | When | Then | Level | Traces to | Class | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-006-56 | No author, no write | No user in the context | a facility is inserted | Refused with 23502 | Integration | Rule 19, ADR-018 | AuthorColumnsTests | Passing (01/11/2026) |
| TC-006-57 | The database fills the author columns | User A in the context | a clinic is inserted | created\_by and updated\_by are A; created\_at equals updated\_at | Integration | Rule 19, ADR-018 | AuthorColumnsTests | Passing (01/11/2026) |
| TC-006-58 | Every update records its author | A clinic created by A | user B updates it | updated\_by is B and updated\_at is later; created\_by is still A | Integration | Rule 19, ADR-018 | AuthorColumnsTests | Passing (01/11/2026) |
| TC-006-59 | The creator cannot be rewritten | A clinic created by A | an update sets created\_by to B | created\_by is still A | Integration | Rule 19, ADR-018 | AuthorColumnsTests | Passing (01/11/2026) |
| TC-006-60 | System actor IDs are fixed | SystemActors in shared | its four IDs are read | Each equals its fixed value (…0001 to …0004, as inserted by V3); all four differ; all are version 7 | Unit | Rule 19, ADR-018 | SystemActorsTests (facility-server, `platform` package) | Passing (01/11/2026) |
| TC-006-61 | A setting survives a save and a load | A facility context with a user | a true/false setting and a whole-number setting are saved through JPA and read back in a new transaction | PostgreSQL holds a JSON boolean and a JSON number; the entity returns the same values and key; created\_by is the user; row\_version is 0 | Integration | F1, section 9 | FacilitySettingMappingTests | Passing (01/11/2026; settles the Hibernate and Jackson 3 JsonNode question) |

## Ticket ER-007, part 1: the first platform operator

**Goal:** prove item 1 of `docs/pending-features.md` (D-002): on a brand-new server, the person installing e-Reach opens the desktop app's first-run screen, gives the platform operator's name, username, email, optional phone, password and PIN, sets up MFA, and becomes the first operator, and this works only while no confirmed operator exists and only with the installer's one-time setup code. Sources: PRD F1 and F2, D-008, D-009, D-013, D-014, D-021 to D-030, ADR-007, ADR-018, ADR-020 and ADR-021, and the transcript of 01/11/2026 (the server draws the QR code; the issuer is plain `e-Reach`).

**Out of scope here:** signing in afterwards (items 2 to 4), adding further operators (item 8), the audit event for the new operator (ER-009), idempotency enforcement (ER-005; the requests below still send `Idempotency-Key`, as every write must), and the email and passkey methods themselves (D-026: refused with 501 until built).

**Decisions behind these cases** (01/11/2026, all agreed by the maintainer):

| # | Decision | Log |
| --- | --- | --- |
| A1 | The operator's label in the authenticator app is their username (`e-Reach:amina.bello`) | This section; email may replace it later |
| A2 | Nobody gets a temporary password; the new operator's `must_change_password` is false | D-023 |
| A3 | The step is finished only when the operator's first MFA code is confirmed; until then a new run soft-deletes the unconfirmed account and makes the new one | D-024 |
| A4 | A taken username is 409 `USERNAME_TAKEN` with the detail "That username is not available. Choose another." and never the value | D-025 |
| A5 | A username is stored exactly as typed and is unique ignoring case | D-021 |
| A6 | The installer offers all three MFA methods; email and passkey answer 501 `MFA_METHOD_NOT_AVAILABLE` until built; an unknown method is 400 `MFA_METHOD_NOT_SUPPORTED` | D-026 |
| A7 | The QR code is drawn, and read in the tests, with ZXing `core` 3.5.3 | D-027 |
| A8 | `mfa_required_by` for an operator is the moment the account is made (elevated roles need MFA at once) | D-028 |
| A9 | Every person has an email, stored in lower case and used by one live account | D-022 |
| A10 | The step is three endpoints under `/api/v1/setup/`, each needing the setup code in `X-Setup-Code`; 403 `ACCESS_DENIED` for a missing or wrong code; 404 once a confirmed operator exists or when no code is configured; `Cache-Control: no-store` on every answer | D-029 |

**Classes.** In `ng.ereach.facilityserver.iam`: `AuthenticatorSecretGeneratorTests` (part A, unit), `MfaSecretCipherTests` (part B, unit and one Spring context runner case), `UserAccountSchemaRulesTests` (part C, integration), `PlatformOperatorSetupTests` (parts D and E, API tests through MockMvc against the real context and PostgreSQL) and `PlatformOperatorSetupClosedTests` (TC-007-67, a context with no setup code).

**Shared setup for parts C, D and E.** As `ereach_owner`, empty `mfa_challenge, mfa_method, practitioner_profile, role_assignment, facility_membership, user_security_key, facility_setting, clinic, facility, lga, state`, then delete every `user_account` row of kind `person`, so the four system actors stay (D-017, D-030). Part C writes run as `ereach_app` inside `FacilityContext.forUserOnly(SystemActors.INSTALLER)`. Parts D and E run with `ereach.setup.code` set to a random code made by the test, and with a `Clock` bean fixed at 01/11/2026 09:00 UTC; every request sends that code (unless the case says otherwise), a new `Idempotency-Key` and a CSRF token. "The operator" is Amina Bello, username `amina.bello`, email `amina.bello@primexhospital.ng`, phone `+2348031234567`, a 16-character password and PIN `4821`. "The current code" is the authenticator code for the clock's time step, made from the setup response's manual key.

**RFC 6238 test secret.** Part A uses the secret of RFC 6238 Appendix B: the 20 ASCII bytes `12345678901234567890` for SHA-1, the 32 bytes `12345678901234567890123456789012` for SHA-256, and the 64 bytes `1234567890` repeated six times followed by `1234` for SHA-512.

### Part A: the TOTP building blocks

| ID | Checks | Given | When | Then | Level | Traces to | Class | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-007-01 | A secret is as long as the HMAC output, and random | Nothing | `generateSecret` is called twice for SHA-1, and once each for SHA-256 and SHA-512 | 20, 32 and 64 bytes; the two SHA-1 secrets differ | Unit | D-014, RFC 6238 R3 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-02 | Base32 follows RFC 4648 without padding | The RFC 4648 section 10 inputs (examples below) | each is encoded | `MY`, `MZXQ`, `MZXW6`, `MZXW6YQ`, `MZXW6YTB`, `MZXW6YTBOI`, never a `=` | Unit | D-014, RFC 4648 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-03 | Base32 decodes what users type | `mzxw6ytboi`, `MZXW 6YTB OI` and `MZXW6YTBOI======` | each is decoded | Each gives the bytes of `foobar` | Unit | D-014 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-04 | Base32 refuses foreign characters | `MZXW1` (1 is not in the alphabet) | it is decoded | IllegalArgumentException | Unit | D-014 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-05 | The manual entry key has a fixed shape | A new SHA-1 secret | it is encoded | Exactly 32 characters from A to Z and 2 to 7, with no `=` | Unit | D-014 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-06 | Codes match RFC 6238 (SHA-1, 8 digits) | The SHA-1 test secret and the times below | the code for each time step is made | The RFC values below | Unit | D-014, RFC 6238 Appendix B | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-07 | A six-digit code keeps its leading zero | The SHA-1 test secret at time 1111111109 | the 6-digit code is made | `081804` (the last six digits of `07081804`) | Unit | D-014 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-08 | The other two RFC algorithms work too | The SHA-256 and SHA-512 test secrets at time 59 | 8-digit codes are made | `46119246` and `90693936` | Unit | RFC 6238 Appendix B | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-09 | Time steps are 30 seconds from the epoch | Times 0, 29, 30, 59 and 60 | the step is computed | 0, 0, 1, 1 and 2 | Unit | D-014, RFC 6238 section 4 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-10 | The current code is accepted | The SHA-1 test secret at time 1111111111, no step used yet | `050471` is verified | Accepted, matching step 37037037 | Unit | D-014 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-11 | One step of drift either way is accepted | The same secret and time | the codes of steps 37037036 (`081804`) and 37037038 are verified | Both accepted, each returning its own step | Unit | D-014, RFC 6238 section 5.2 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-12 | Two steps of drift are refused | The same secret and time | the codes of steps 37037035 and 37037039 are verified | Both refused | Unit | D-014 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-13 | A used code never works twice | The current code, with the last accepted step equal to the current step | it is verified again | Refused; with the last accepted step one lower, accepted | Unit | F2, D-014 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-14 | A malformed code is refused, not thrown | `05047`, `0504711`, `05a471` and ` 50471` | each is verified | Each refused, with no exception | Unit | D-014 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-15 | The provisioning URI has the agreed shape | Issuer `e-Reach`, account `amina.bello`, secret `JBSWY3DPEHPK3PXP` | the URI is built | `otpauth://totp/e-Reach:amina.bello?secret=JBSWY3DPEHPK3PXP&issuer=e-Reach&algorithm=SHA1&digits=6&period=30` | Unit | D-014, Key Uri Format | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-16 | Label and issuer are percent-encoded | Issuer `My App & Co`, account `amina bello` | the URI is built | Spaces become `%20` (never `+`) and `&` becomes `%26`, in the label and in the issuer parameter | Unit | RFC 3986 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |
| TC-007-17 | Unusable URI inputs are refused | A colon in the issuer or the account, a blank issuer, 5 or 9 digits, a period of 0 | the URI is built | IllegalArgumentException each time | Unit | Key Uri Format, RFC 4226 R4 | AuthenticatorSecretGeneratorTests | Passing (01/11/2026) |

Examples for TC-007-02 (RFC 4648 section 10, padding removed): `f` → `MY`, `fo` → `MZXQ`, `foo` → `MZXW6`, `foob` → `MZXW6YQ`, `fooba` → `MZXW6YTB`, `foobar` → `MZXW6YTBOI`.

Examples for TC-007-06 (RFC 6238 Appendix B, SHA-1): 59 → `94287082`, 1111111109 → `07081804`, 1111111111 → `14050471`, 1234567890 → `89005924`, 2000000000 → `69279037`, 20000000000 → `65353130`.

### Part B: keeping the secret encrypted

| ID | Checks | Given | When | Then | Level | Traces to | Class | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-007-18 | A secret survives encryption | A cipher with a random 32-byte key and a 20-byte secret | it is encrypted, then decrypted | The same 20 bytes | Unit | F2, D-014 | MfaSecretCipherTests | Passing (01/11/2026) |
| TC-007-19 | The stored form hides the secret | The same | it is encrypted | 48 bytes (12 IV, 20 ciphertext, 16 tag), and the secret's bytes do not appear in them | Unit | D-014 | MfaSecretCipherTests | Passing (01/11/2026) |
| TC-007-20 | Every encryption uses a fresh IV | The same secret | it is encrypted twice | The two results differ, and both decrypt to the secret | Unit | D-014, NIST SP 800-38D | MfaSecretCipherTests | Passing (01/11/2026) |
| TC-007-21 | A changed byte is detected | A stored secret with one byte flipped | it is decrypted | IllegalStateException, never wrong bytes | Unit | D-014 | MfaSecretCipherTests | Passing (01/11/2026) |
| TC-007-22 | Another key cannot read it | A secret stored under key K | a cipher with another key decrypts it | IllegalStateException | Unit | D-014 | MfaSecretCipherTests | Passing (01/11/2026) |
| TC-007-23 | Only a 32-byte key is accepted | A base64 16-byte key, and text that is not base64 | the cipher is made | IllegalStateException naming the 32 bytes, and IllegalArgumentException | Unit | D-014 | MfaSecretCipherTests | Passing (01/11/2026) |
| TC-007-24 | No key, no start | A Spring context with `MfaSecretCipher` and no `ereach.mfa.encryption-key` | it starts | It fails, naming `ereach.mfa.encryption-key`; with a key it starts and holds the cipher | Integration | D-014, PRD rule 17 | MfaSecretCipherTests | Passing (01/11/2026) |

### Part C: the database rules for the operator account

| ID | Checks | Given | When | Then | Level | Traces to | Class | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-007-25 | An operator account is a person, active, flagged, and authored by the installer | The installer context | a person `amina.bello` with password and PIN hashes and `is_platform_operator` true is inserted, without author columns | Stored as kind `person`, status `active`, `is_platform_operator` true; `created_by` and `updated_by` are the INSTALLER | Integration | D-008, D-009, ADR-018 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-26 | A person must have a password | The installer context | a person with no password hash is inserted | Refused with 23514 by `ck_user_account_password` | Integration | F2, V3 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-27 | Usernames are 3 to 40 of `A-Z a-z 0-9 . _ -` | The installer context | persons named `ab`, 41 letters, `amina bello` and `amina!` are inserted, then `Amina.Bello` | The first four refused with 23514; `Amina.Bello` stored exactly as typed | Integration | F2, D-021, V3 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-28 | A username is used once | The system actor `system.installer` exists | a person with that username is inserted | Refused with 23505 by `ux_user_account_username` | Integration | F2, V3 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-29 | A phone number is in international form | The installer context | a person with phone `08031234567` is inserted, then one with `+2348031234567` | The first refused with 23514; the second stored | Integration | V3 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-30 | A system account is never an operator | The installer context | a system account flagged as operator is inserted, and the INSTALLER row is updated to be an operator | Both refused with 23514 by `ck_user_account_system_secrets` | Integration | F2, D-009 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-31 | No author, no account | No context at all | a person is inserted | Refused with 23502 naming `created_by` | Integration | Rule 19, ADR-018 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-32 | An operator sees no facility's data | A facility with a clinic, and another person's membership and role there | the operator's context (user only, no facility) counts clinics, memberships and role assignments | All three counts are 0 | Integration | D-009, ADR-021, rule 1 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-33 | `platform_operator` is no longer a facility role | A facility and a membership in it | a role assignment `platform_operator` is inserted | Refused with 23514 | Integration | D-009 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-62 | Two usernames cannot differ only by case | A person `Amina.Bello` exists | a person `amina.bello` is inserted | Refused with 23505 by `ux_user_account_username` | Integration | D-021 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-63 | A person must have an email | The installer context | a person with a password but no email is inserted | Refused with 23514 by `ck_user_account_person_email` | Integration | D-022 | UserAccountSchemaRulesTests | Passing (01/11/2026) |
| TC-007-64 | An email is lower case and used once | A person with `amina.bello@primexhospital.ng` | a person with `Chidi.Okafor@PrimexHospital.ng`, then one with Amina's address, is inserted | The first refused with 23514; the second with 23505 by `ux_user_account_email` | Integration | D-022, V3 | UserAccountSchemaRulesTests | Passing (01/11/2026) |

### Part D: the installer step

API tests: each case sends HTTP requests to the setup endpoints and checks the answer and the database. They fail with 404 until the endpoints exist.

| ID | Checks | Given | When | Then | Level | Traces to | Class | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-007-34 | The step creates the first operator | A new server (only the system actors exist) | `POST /api/v1/setup/platform-operator` with the operator's details | 201 with the username, full name and email; one live person account with that name, username, email and phone, `is_platform_operator` true, status `active`, `must_change_password` false (A2), `mfa_required_by` equal to the clock's now (A8), authored by the INSTALLER | API | F1, D-008, D-009, D-023, D-028 | PlatformOperatorSetupTests | Failing |
| TC-007-35 | A username is stored as typed | A new server | the step runs with username `Amina.Bello` | Stored as `Amina.Bello` (A5) | API | F2, D-021 | PlatformOperatorSetupTests | Failing |
| TC-007-36 | The password is kept only as an Argon2id hash | A new server | the step runs | `password_hash` starts with `$argon2id$`, is not the password, and `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` matches it to the password | API | F2, ADR-007 | PlatformOperatorSetupTests | Failing |
| TC-007-37 | The PIN is kept only as an Argon2id hash | A new server | the step runs | `pin_hash` starts with `$argon2id$`, is not the PIN, and the same encoder matches it to the PIN | API | F2, ADR-007 | PlatformOperatorSetupTests | Failing |
| TC-007-38 | A short password is refused | A new server | the step runs with a 9-character password | 400 `VALIDATION_FAILED` with `password` in `errors`, and the password nowhere in the answer; no account saved | API | F2, PRD section 10 | PlatformOperatorSetupTests | Failing |
| TC-007-39 | A PIN is 4 to 6 digits | A new server | the step runs with PINs `482`, `4821503` and `48a1` | Each 400 `VALIDATION_FAILED` with `pin` in `errors`, without the value; no account saved | API | F2 | PlatformOperatorSetupTests | Failing |
| TC-007-40 | Name, username and email are checked before saving | A new server | the step runs with a blank name, with username `amina bello`, with no email, and with email `amina` | Each 400 `VALIDATION_FAILED` naming `fullName`, `username`, `email` and `email`; no account saved, and never a 500 from a database check | API | F2, D-022, PRD section 10 | PlatformOperatorSetupTests | Failing |
| TC-007-41 | A taken username has its own answer, without the value | A new server | the step runs with username `System.Installer` | 409 `USERNAME_TAKEN` with the detail "That username is not available. Choose another.", and the username nowhere in the answer (A4, A5) | API | D-021, D-025 | PlatformOperatorSetupTests | Failing |
| TC-007-42 | No second operator once the first is confirmed | A confirmed operator | the step runs again with another username and email | 404; still exactly one operator | API | F1 acceptance criterion 1, D-008, D-029 | PlatformOperatorSetupTests | Failing |
| TC-007-43 | System accounts do not count as operators | A new server with its four system actors | the step runs | 201 | API | D-008, D-009 | PlatformOperatorSetupTests | Failing |
| TC-007-44 | Two runs at once leave one operator | A new server | two threads run the step at the same moment, with different usernames and emails | Both answer 201 or one answers 409; afterwards exactly one live operator account exists (A3) | API | D-008, D-024 | PlatformOperatorSetupTests | Failing |
| TC-007-45 | An abandoned run can be done again | The step ran, but MFA was never confirmed | the step runs again, with the same username and email | 201; the first account is soft-deleted (`deleted_at` set), the second is the only live operator (A3) | API | D-024 | PlatformOperatorSetupTests | Failing |
| TC-007-46 | Authenticator setup answers with a QR code and a key, nothing else | The operator has been created | `POST /api/v1/setup/platform-operator/mfa` with method `totp` | 200; method `totp`, status `PENDING_CONFIRMATION`, `qrCodeImage` starting `data:image/png;base64,`, `manualEntryKey` of 32 Base32 characters; no `codeSentTo`, `codeExpiresAt` or `passkeyOptions` | API | D-014, D-026, transcript 01/11/2026 | PlatformOperatorSetupTests | Failing |
| TC-007-47 | The QR code holds the operator's provisioning URI | The setup answer | the PNG is decoded with ZXing (A7) | Exactly `otpauth://totp/e-Reach:amina.bello?secret=<manual key>&issuer=e-Reach&algorithm=SHA1&digits=6&period=30` (A1) | API | D-014, D-027 | PlatformOperatorSetupTests | Failing |
| TC-007-48 | Setup stores the secret encrypted and pending | The setup answer | `mfa_method` is read | One live row of type `totp` for the operator; `secret_encrypted` decrypts to the bytes behind the manual key; `confirmed_at` and `last_used_step` are null; authored by the INSTALLER | API | F2, D-013, D-014 | PlatformOperatorSetupTests | Failing |
| TC-007-49 | Starting setup again replaces the pending secret | A pending authenticator | setup starts again | Still one live `mfa_method` row; a different manual key; the old key's current code is refused with 422 `INVALID_MFA_CODE` | API | D-014 | PlatformOperatorSetupTests | Failing |
| TC-007-50 | A confirmed authenticator cannot be set up again | The operator's authenticator is confirmed | setup starts again | 404 (the step is closed, A10); the stored secret is unchanged | API | F2, D-024, D-029 | PlatformOperatorSetupTests | Failing |
| TC-007-51 | The first correct code finishes the step | A pending authenticator | `POST /api/v1/setup/platform-operator/mfa/confirm` with method `totp` and the current code | 200, status `CONFIRMED`; `confirmed_at` and `last_used_at` equal the clock's now; `last_used_step` is the clock's step | API | D-013, D-014, D-024 | PlatformOperatorSetupTests | Failing |
| TC-007-52 | A wrong code leaves setup pending | A pending authenticator | a code that is not the current one is confirmed | 422 `INVALID_MFA_CODE`; `confirmed_at` and `last_used_step` still null; the step is still open | API | D-014 | PlatformOperatorSetupTests | Failing |
| TC-007-53 | Confirmation allows one step of drift, not two | A pending authenticator | the code from two steps ago is confirmed, then the code from the previous step | The first 422 `INVALID_MFA_CODE`; the second 200 `CONFIRMED` | API | D-014 | PlatformOperatorSetupTests | Failing |
| TC-007-54 | The confirming code cannot be used again | An authenticator confirmed with code C | C is verified at the same moment | 422 `INVALID_MFA_CODE` | API | F2, D-014 | Withdrawn | Withdrawn (01/11/2026): the setup endpoints close the moment the code is confirmed (D-024, D-029), so the replay can only be tried at sign-in; TC-007-13 proves the rule and item 4 tests it at sign-in |
| TC-007-55 | Confirming with no setup is a 404 | The operator has been created, with no MFA method | a code is confirmed | 404 `NOT_FOUND` | API | PRD section 10 | PlatformOperatorSetupTests | Failing |
| TC-007-56 | Every method can be asked for; only the built one works | The operator has been created | setup starts with `email`, `passkey` and `sms` | `email` and `passkey` 501 `MFA_METHOD_NOT_AVAILABLE` (A6); `sms` 400 `MFA_METHOD_NOT_SUPPORTED` | API | D-026 | PlatformOperatorSetupTests | Failing |
| TC-007-57 | Nothing secret reaches the logs | The whole step, from account to confirmation, with log output captured | the captured output is searched | None of: the password, the PIN, the setup code, the manual key, the provisioning URI, the QR image | API | F2 acceptance criterion, D-029 | PlatformOperatorSetupTests | Failing |
| TC-007-58 | The step's answers hold no secrets | The operator has been created | the creation answer is read | No password, PIN, either hash or setup code in it | API | F2 | PlatformOperatorSetupTests | Failing |

### Part E: the way in

| ID | Checks | Given | When | Then | Level | Traces to | Class | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-007-59 | Every setup endpoint closes after the first operator | A confirmed operator | each of the three setup endpoints is called with the right setup code | 404 from each | API | F1 acceptance criterion 1, D-029 | PlatformOperatorSetupTests | Failing |
| TC-007-60 | Setup answers are never cached | The step runs from account to confirmation | each answer is read | `Cache-Control: no-store` on each | API | D-029 | PlatformOperatorSetupTests | Failing |
| TC-007-61 | Refusals keep the shared problem shape | A taken username | the step runs | `application/problem+json`; `type` `https://ereach.ng/problems/username-taken`; title; status 409; detail; `instance` starting `urn:uuid:`; `code` `USERNAME_TAKEN` | API | PRD section 10, ADR-019 | PlatformOperatorSetupTests | Failing |
| TC-007-65 | No setup code, no setup | A new server | the step runs without `X-Setup-Code` | 403 `ACCESS_DENIED`; no account saved | API | D-029 | PlatformOperatorSetupTests | Failing |
| TC-007-66 | A wrong setup code is refused | A new server | the step runs with a wrong code | 403 `ACCESS_DENIED`, the expected code nowhere in the answer; no account saved | API | D-029 | PlatformOperatorSetupTests | Failing |
| TC-007-67 | Without a configured code the step does not exist | A server started with `ereach.setup.code` blank | the step runs with any code | 404; no account saved | API | D-029 | PlatformOperatorSetupClosedTests | Failing |

## Traceability

Every PRD rule or feature a ticket touches should be proved by at least one case. A rule with no case is a gap; a case with no rule is either a missing requirement or a case that should not exist.

| PRD source | What it requires | Proved by |
| --- | --- | --- |
| Section 7, repository layout | `shared` is a module of the build, holding production code only | TC-004-01, TC-004-02 |
| Rule 4 | IDs are UUIDv7, made in the application, wrapped in a typed record per entity | TC-004-03 to TC-004-10 |
| Rule 8 | Money is whole kobo in a `long`, never floating point | TC-004-11, TC-004-12, TC-004-15, TC-004-18 to TC-004-21 |
| F13, pricing step 6 | Co-payment is `(amount × bps + 5000) / 10000` | TC-004-13, TC-004-14, TC-004-16, TC-004-20 |
| F13, pricing step 7 | Patient share plus payer share equals the amount | TC-004-17 |
| Section 8, rule 1; F1 | Every facility table is guarded by row-level security, and every transaction carries its facility and user | TC-006-01 to TC-006-13, TC-006-18, TC-006-29 to TC-006-55 |
| F1, Batch 2 V2 | The facility, clinic, setting and reference tables enforce their own rules | TC-006-14 to TC-006-28 |
| Section 8, rule 19; ADR-018 | Every write has a named author, filled in by the database | TC-006-56 to TC-006-60 |
| F1, section 9 | Setting values are stored as JSON and read back unchanged | TC-006-61 |
| Section 10; ADR-019 | Every error is one RFC 9457 shape with a stable code, and nothing internal leaks | TC-004B-01 to TC-004B-07 |
| F1, installer step; D-008 | The first platform operator is created only while none exists, by the INSTALLER actor | TC-007-25, TC-007-34, TC-007-42 to TC-007-45, TC-007-59 |
| F2, passwords and PINs; ADR-007 | Passwords of 10 or more characters and 4 to 6 digit PINs, kept only as Argon2id hashes, never logged | TC-007-26, TC-007-36 to TC-007-39, TC-007-57, TC-007-58 |
| F2, usernames | Unique, stored lower case, 3 to 40 allowed characters | TC-007-27, TC-007-28, TC-007-35, TC-007-40, TC-007-41 |
| D-009, ADR-021 | Being an operator is an account flag; a system account never has it; an operator sees no facility data | TC-007-25, TC-007-30, TC-007-32, TC-007-33, TC-007-43 |
| D-013, D-014; F2, MFA | TOTP with SHA-1, 6 digits, 30 seconds, one step of drift, no code twice, secret encrypted with AES-256-GCM | TC-007-01 to TC-007-24, TC-007-46 to TC-007-56, TC-007-60 |
| D-021, D-022 | Usernames stored as typed and unique ignoring case; every person has a lower-case email used once | TC-007-27, TC-007-35, TC-007-40, TC-007-41, TC-007-62 to TC-007-64 |
| D-024, D-029 | The installer step is three setup endpoints behind a one-time setup code, closed once the operator's MFA is confirmed | TC-007-42, TC-007-44, TC-007-45, TC-007-50, TC-007-59 to TC-007-61, TC-007-65 to TC-007-67 |
| D-026 | All three MFA methods can be chosen; unbuilt ones answer 501 | TC-007-56 |

## Sources

- [Gherkin reference](https://cucumber.io/docs/gherkin/reference/), Cucumber: Given, When, Then, Scenario Outline and Examples tables
- [Given When Then](https://martinfowler.com/bliki/GivenWhenThen.html), Martin Fowler: origin in behaviour-driven development and Specification by Example
- [Three Amigos](https://www.agilealliance.org/glossary/three-amigos/), Agile Alliance: business, development and testing agree on the work before it starts
- [Test case anatomy reference](https://tessl.io/registry/testland/test-case-anatomy-reference): the nine test-case fields it attributes to ISO/IEC/IEEE 29119-3 (the standard itself is paywalled and was not read directly)
- [Definition of Done, User Stories and Acceptance Criteria](https://agilepainrelief.com/blog/definition-of-done-user-stories-acceptance-criteria/), Agile Pain Relief: acceptance criteria per story versus one Definition of Done for all work
