# Decisions

## Iteration 1.1

- Status: Accepted
- Date: 2026-11-07
- Updated:


# 0001. All services live in one repository (monorepo)

## Context
The platform will grow to several services (billing, payments, AI gateway) built and maintained by one developer.
The services share infrastructure (docker-compose, Terraform, CI workflows), and changes to event formats affect
both the service that publishes an event and the services that consume it.

## Decision
All services live in one repository with a multi-module build. Each service builds and deploys on its own, owns its
own database schema, and shares only event contracts with other services. There is no shared "common" library.

## Alternatives considered
- One repository per service: rejected because one developer would maintain several CI setups, and a change to an
  event would need coordinated releases across repositories.

## Consequences
- Positive: a change to an event updates the publisher and its consumers in one commit; infrastructure, CI and
  documentation live in one place; reviewers see the whole system from one link.
- Negative: CI needs path filters so each service builds and deploys independently, and service boundaries are kept
  by discipline, since nothing technically stops one service from importing another's code.


# 0002. Flyway owns the schema; Hibernate only validates it

## Context
The schema must be identical in every environment (local, CI, AWS), and every change must be versioned and
reviewable in Git.

Hibernate can create and update the schema from entity annotations (ddl-auto), but those changes happen implicitly at
startup, are not visible in code review, and can alter or drop columns in ways that are hard to undo. It also cannot
express parts of our schema: CHECK constraints, the case-insensitive unique index on lower(email), and uuidv7()
defaults.

We need a single source of truth for the schema.

## Decision
Flyway applies versioned SQL migrations and is the only thing that changes the schema. Hibernate runs with
`ddl-auto=validate`, so the application fails at startup if the entities do not match the schema.

## Alternatives considered
- Hibernate `ddl-auto=update`: rejected because changes are implicit, unreviewed, and cannot express the constraints
  and indexes above.
- Liquibase: rejected because its XML/YAML changelogs add a layer we do not need when plain SQL is enough.

## Consequences
- Positive: every environment gets the same schema from the same migrations; Git history shows what changed, when and
  by whom; Flyway's history table shows which migrations each database has applied.
- Negative: every entity change needs a hand-written migration, and a migration cannot be edited once applied; fixes
  require a new migration (or a local database reset during development).


# 0003. Store money as NUMERIC(19,4) with a currency code

## Context
The platform stores prices and invoice amounts. Amounts must be exact, because rounding errors in billing become real
money disputes.

## Decision
We store amounts as NUMERIC(19,4) with a separate 3-letter currency column, and use BigDecimal in Java.

## Alternatives considered
- double/float: cannot represent most decimals exactly (0.1 + 0.2 ≠ 0.3).
- Integer minor units (cents): exact and fast, but each currency has a different number of decimal places, which adds
  conversion logic.

## Consequences
- Positive: exact arithmetic; amounts read naturally in SQL.
- Negative: BigDecimal is more verbose than primitives, and comparisons must use compareTo(), not equals().


# 0004. Record ids are UUIDv7

## Context
Record ids appear in URLs, API responses and documents such as invoices. Sequential numbers reveal how many records
exist and let anyone guess neighbouring ids. The platform also needs one consistent id type across all tables and,
later, across services.

Unguessable ids are an extra layer of protection only; authorization checks remain the real defense.

## Decision
Every table uses a UUID primary key in UUIDv7 format. UUIDv7 values start with a timestamp, so new ids are
time-ordered. The application generates the ids (see 0005); each column also has `DEFAULT uuidv7()` as a fallback
for rows inserted by manual SQL.

## Alternatives considered
- BIGINT identity: smaller and faster, but sequential and guessable, and harder to keep unique across services.
- Mixing BIGINT and UUID by table: rejected because two id types add inconsistency for little gain.
- Random UUIDv4 (`gen_random_uuid()`): rejected because random ids scatter inserts across the index, which slows
  inserts as tables grow.

## Consequences
- Positive: one id type everywhere; ids are hard to guess; time-ordered ids keep index inserts sequential; rows
  inserted by manual SQL get valid ids automatically.
- Negative: 16 bytes per id instead of 8, so larger indexes; the creation time can be read from the id; less
  randomness than v4; the `uuidv7()` fallback default requires PostgreSQL 18 (alternative: `gen_random_uuid()`).


# 0005. Hibernate generates UUIDv7 ids with @UuidGenerator

## Context
Ids are UUIDv7 (see 0004). JPA must know whether it creates the id itself or reads it back from the database, because
this affects when the INSERT runs and how Spring Data's `save()` decides between insert and update.

## Decision
The id is mapped with `@Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7)`. Hibernate generates the id in the
JVM when the entity is persisted and includes it in the INSERT. The database default only covers manual inserts.

Verified by: the INSERT includes an `id` whose version digit is 7, `save()` returns the entity with its id set, and
no SELECT runs before the INSERT.

## Alternatives considered
- Database-generated id with `@GeneratedValue(strategy = GenerationType.IDENTITY)`: rejected because support for
  IDENTITY with UUID columns depends on the Hibernate version, while `@UuidGenerator` is documented for this case.
- `@UuidGenerator(style = TIME)`: rejected because it produces version 1 UUIDs, not version 7.
- Assigning the id in code before `save()`: rejected because Spring Data then treats new entities as existing, calls
  `merge`, and runs an extra SELECT before every insert.

## Consequences
- Positive: the id is null until persist, so `save()`'s null-means-new rule works without extra code; inserts can be
  batched; no dependency on PostgreSQL to create ids.
- Negative: the INSERT waits until flush (usually commit), so catching constraint violations inside a service method
  needs `saveAndFlush()`; `equals()` and `hashCode()` must not depend on the id, since it changes from null to a value
  when the entity is saved; manual SQL inserts and application inserts use different clocks to build the timestamp
  part of the id.


# 0006. The application owns the audit timestamps

## Context
For auditing and troubleshooting, the platform needs to know when each record was created and last updated. Either
the application or the database can set these values.

## Decision
Every table has `created_at` and `updated_at` columns. The application sets both values; the database default
(`CURRENT_TIMESTAMP`) is only a fallback for rows inserted by manual SQL.

## Alternatives considered
- Database-owned timestamps (defaults plus an update trigger): rejected to keep the logic visible in the application
  code and avoid triggers for now.
- Full change history with Hibernate Envers (revision tables): solves a different problem, recording every change
  rather than only the latest; possible later if the compliance needs grow.

## Consequences
- Positive: every record shows when it was created and last changed, which can be matched against logs and metrics
  for troubleshooting and audits.
- Negative: updates made by manual SQL do not change `updated_at`; the JVM clock and the database clock can differ
  slightly; only the latest change is known, not the history.


# 0007. Hibernate annotations fill the audit timestamps

## Context
The application owns the audit timestamps (see 0006). Hibernate, Spring Data and plain JPA each offer a way to fill
them automatically.

## Decision
Entities use Hibernate's `@CreationTimestamp` and `@UpdateTimestamp` on `Instant` fields. `created_at` is mapped with
`updatable = false`, so no update can overwrite it.

## Alternatives considered
- Spring Data auditing (`@CreatedDate`, `@LastModifiedDate` with `@EnableJpaAuditing`): more setup, but it can also
  record who made a change with `@CreatedBy`. A likely revisit once the platform has authenticated users.
- JPA lifecycle methods (`@PrePersist`, `@PreUpdate`): no extra dependency, but the same code must be written in every
  entity.

## Consequences
- Positive: simple, no configuration, one annotation per field.
- Negative: ties the entities to Hibernate; uses the JVM clock unless `source = SourceType.DB` is set; records when a
  change happened but not who made it.