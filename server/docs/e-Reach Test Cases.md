# e-Reach Test Cases

Oct 17, 2026 · @Abdulmalik Alayande

This is the acceptance book for e-Reach. Every ticket gets its test cases here before any code is written, and a ticket is finished only when every one of its cases passes as an automated test.

## How this works

The product owner writes what "done" looks like; the developer builds until it is true; nobody argues afterwards about whether the work is finished, because the test cases already said. In this project Claude plays product owner and reviewer, and Abdulmalik plays developer and tester.

Every ticket goes through the same six steps:

1. **Cases written.** Before any code, the ticket's test cases appear in this book, each traced to the PRD rule or feature it proves.
2. **Cases**** challenged.** The developer reads every case and pushes back on anything unclear, wrong or missing, in a comment on that case. This is the "three amigos" conversation: business (what problem?), development (how to build it?) and testing (what could go wrong?) agree before work starts. A case that survives the challenge is fixed for the ticket.
3. **Tests first.** Each case becomes an automated test whose display name starts with its ID, for example `@DisplayName("TC-004-12 co-payment follows the PRD formula")`. Run it and watch it fail. A test that has never failed has never proved anything.
4. **Build until green.** Write the smallest code that makes each failing test pass, then tidy it while the tests stay green.
5. **Hand back.** The developer comes back only when `.\mvnw.cmd -B verify` passes with every case for the ticket, and sets each case's status to Passing. Bring the `verify` output and the commit or pull request link.
6. **Review.** Claude reviews the code against the cases and PRD section 8. Accepted means the ticket's status in the PRD moves to Done.

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
| TC-004-01 | The shared module is part of the build | `server/pom.xml` lists `shared`, and `facility-server` depends on `shared` | `.\mvnw.cmd validate` runs in `server/` | The reactor lists e-Reach, then ereach-shared, then facility-server, all SUCCESS | Build | PRD section 7 | Passing |
| TC-004-02 | The shared module stays small | The shared module's `pom.xml` | `.\mvnw.cmd -pl shared dependency:tree` runs in `server/` | Compile scope holds only `uuid-creator`; everything else is test scope | Build | PRD section 7 | Passing |
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

## Ticket ER-006, part 2: proving the guard

**Goal:** prove, on every build, that the database itself keeps each facility's rows apart, and that the platform tables refuse bad data. These cases run against `V2__platform.sql` alone; the transaction hook, the entities and the API come later in ER-006.

**Classes:** `ng.ereach.facilityserver.platform.FacilityIsolationTests` holds TC-006-01 to TC-006-13, the guard itself. `ng.ereach.facilityserver.platform.PlatformSchemaRulesTests` holds TC-006-14 to TC-006-28, the table rules. Both extend `PostgresIntegrationTest`.

**Shared setup.** Each test starts from the migrated schema. As `ereach_owner`, insert states LA and OG with one LGA each, the way `R__` files will later. As `ereach_app`, insert facility A (prefix AH, national code NG-001) and facility B (prefix BH, no national code); then, with A set, clinics GOPD and ANC and the setting `lab.verification_required`; then, with B set, clinic PAED and the same setting. "A set" means the transaction began with `SELECT set_config('app.facility_id', '<A>', true)`. Every case connects as `ereach_app` unless it says otherwise.

| ID | Checks | Given | When | Then | Level | Traces to | Class | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-006-01 | Nothing set, nothing read | No facility set | clinic and facility\_setting are counted | Both counts are 0 | Integration | F1, rule 1 | FacilityIsolationTests | Not written |
| TC-006-02 | An empty setting counts as none | app.facility\_id set to the empty text '' | clinic and facility\_setting are counted | Both counts are 0 | Integration | Rule 1 | FacilityIsolationTests | Not written |
| TC-006-03 | Facility A sees only its own rows | A set | clinics are listed and settings counted | Exactly GOPD A and ANC A; 1 setting | Integration | F1, rule 1, ADR-003 | FacilityIsolationTests | Not written |
| TC-006-04 | An unknown facility sees nothing | app.facility\_id set to a UUID that no facility has | clinics are counted | The count is 0 | Integration | Rule 1 | FacilityIsolationTests | Not written |
| TC-006-05 | A malformed setting fails loudly | app.facility\_id set to 'not-a-uuid' | clinics are selected | The query fails with an invalid-UUID error; no rows are returned | Integration | Rule 1 | FacilityIsolationTests | Not written |
| TC-006-06 | The setting ends with its transaction | One connection: A set, clinics counted, transaction committed | clinics are counted again on the same connection | 2 inside the transaction, 0 after it | Integration | F1, rule 1 | FacilityIsolationTests | Not written |
| TC-006-07 | No row can be written for another facility | A set | a clinic stamped with B is inserted | Refused: new row violates row-level security policy | Integration | Rule 1, ADR-003 | FacilityIsolationTests | Not written |
| TC-006-08 | No row can be written with no facility set | No facility set | a clinic stamped with A is inserted | Refused: new row violates row-level security policy | Integration | Rule 1 | FacilityIsolationTests | Not written |
| TC-006-09 | A row cannot be moved to another facility | A set | GOPD A's facility\_id is updated to B | Refused: new row violates row-level security policy | Integration | Rule 1 | FacilityIsolationTests | Not written |
| TC-006-10 | Another facility's row cannot be changed by its ID | A set | PAED B is renamed by its id | 0 rows updated; read as B, its name is unchanged | Integration | F1, rule 1 | FacilityIsolationTests | Not written |
| TC-006-11 | Nothing is hard-deleted | A set | DELETE FROM clinic runs | Refused: permission denied for table clinic | Integration | Rule 7 | FacilityIsolationTests | Not written |
| TC-006-12 | The owner is guarded too | Connected as ereach\_owner, no facility set | clinics are counted | The count is 0, because the tables are forced | Integration | Rule 1, rule 18 | FacilityIsolationTests | Not written |
| TC-006-13 | Every facility table has its guard | The migrated schema | the catalogue is read: every table in public with a facility\_id column | Each one has row-level security enabled and forced and at least one policy; the list of offenders is empty | Integration | F1, ADR-003 | FacilityIsolationTests | Not written |
| TC-006-14 | Clinic codes are unique within a facility | A set | a second GOPD is inserted at A | Refused: ux\_clinic\_code | Integration | F1 | PlatformSchemaRulesTests | Not written |
| TC-006-15 | Two facilities may use the same clinic code | A has GOPD; B set | B inserts its own GOPD | Accepted; A still sees one GOPD, B sees its own | Integration | F1 | PlatformSchemaRulesTests | Not written |
| TC-006-16 | A soft-deleted clinic frees its code | A set | ANC A gets deleted\_at, then a new ANC is inserted | Accepted; exactly 1 ANC without deleted\_at | Integration | Rule 7 | PlatformSchemaRulesTests | Not written |
| TC-006-17 | Clinic codes follow the format | A set | codes 'gopd', 'G' and one of 21 letters are inserted | All three refused: clinic\_code\_check | Integration | Batch 2 V2 | PlatformSchemaRulesTests | Not written |
| TC-006-18 | A clinic needs a real facility | app.facility\_id set to a UUID that no facility has | a clinic stamped with that UUID is inserted | Refused: clinic\_facility\_id\_fkey | Integration | Rule 1 | PlatformSchemaRulesTests | Not written |
| TC-006-19 | The facility list is readable before a facility is chosen | No facility set | facilities are counted | The count is 2 (facility has no row-level security, so sign-in can list memberships' facilities) | Integration | F1, F2, Batch 2 V2 | PlatformSchemaRulesTests | Not written |
| TC-006-20 | Hospital number prefix rules hold | Facility A has prefix AH | facilities with prefixes 'gh', 'ABCDEFG' and 'AH' are inserted | All three refused: two by the format check, one by ux\_facility\_prefix | Integration | F1 | PlatformSchemaRulesTests | Not written |
| TC-006-21 | The national facility code is optional and unique | A has code NG-001; B has none | facility C with NG-001 and facility D with no code are inserted | C refused by ux\_facility\_nhfr\_code; D accepted | Integration | F1 | PlatformSchemaRulesTests | Not written |
| TC-006-22 | Blank codes are refused | None | a facility with national facility code '   ' is inserted | Refused: facility\_nhfr\_facility\_code\_check | Integration | F1 | PlatformSchemaRulesTests | Not written |
| TC-006-23 | Registration number and agency come together | None | facilities are inserted with a number only, an agency only, and both | The first two refused by facility\_check; the third accepted | Integration | F1 | PlatformSchemaRulesTests | Not written |
| TC-006-24 | A facility's LGA lies in its state | States LA and OG, each with one LGA | a facility with state LA and an Ogun LGA is inserted | Refused: fk\_facility\_lga | Integration | F1, Batch 2 V2 | PlatformSchemaRulesTests | Not written |
| TC-006-25 | Facility type is a closed list | None | a facility of type 'hospital' is inserted | Refused: facility\_type\_check | Integration | F1 | PlatformSchemaRulesTests | Not written |
| TC-006-26 | Unknown setting keys are refused | A set | a setting with key 'foo.bar' is inserted | Refused: facility\_setting\_key\_check | Integration | F1 | PlatformSchemaRulesTests | Not written |
| TC-006-27 | One value per setting key per facility | A set; A already has lab.verification\_required | a second lab.verification\_required is inserted | Refused: ux\_facility\_setting\_key | Integration | F1 | PlatformSchemaRulesTests | Not written |
| TC-006-28 | Reference tables are read-only for the application | None | the application inserts into state and updates lga | Both refused: permission denied | Integration | F1, Batch 2 V2 | PlatformSchemaRulesTests | Not written |

Every Then above was checked by running it on a real PostgreSQL before it was written here.

## Traceability

Every PRD rule or feature a ticket touches should be proved by at least one case. A rule with no case is a gap; a case with no rule is either a missing requirement or a case that should not exist.

| PRD source | What it requires | Proved by |
| --- | --- | --- |
| Section 7, repository layout | `shared` is a module of the build, holding production code only | TC-004-01, TC-004-02 |
| Rule 4 | IDs are UUIDv7, made in the application, wrapped in a typed record per entity | TC-004-03 to TC-004-10 |
| Rule 8 | Money is whole kobo in a `long`, never floating point | TC-004-11, TC-004-12, TC-004-15, TC-004-18 to TC-004-21 |
| F13, pricing step 6 | Co-payment is `(amount × bps + 5000) / 10000` | TC-004-13, TC-004-14, TC-004-16, TC-004-20 |
| F13, pricing step 7 | Patient share plus payer share equals the amount | TC-004-17 |

## Sources

- [Gherkin reference](https://cucumber.io/docs/gherkin/reference/), Cucumber: Given, When, Then, Scenario Outline and Examples tables
- [Given When Then](https://martinfowler.com/bliki/GivenWhenThen.html), Martin Fowler: origin in behaviour-driven development and Specification by Example
- [Three Amigos](https://www.agilealliance.org/glossary/three-amigos/), Agile Alliance: business, development and testing agree on the work before it starts
- [Test case anatomy reference](https://tessl.io/registry/testland/test-case-anatomy-reference): the nine test-case fields it attributes to ISO/IEC/IEEE 29119-3 (the standard itself is paywalled and was not read directly)
- [Definition of Done, User Stories and Acceptance Criteria](https://agilepainrelief.com/blog/definition-of-done-user-stories-acceptance-criteria/), Agile Pain Relief: acceptance criteria per story versus one Definition of Done for all work
