# CATS — Course Application Tracking System

A complete server-rendered Spring Boot application for the mandatory SA63 CATS workflow: login, applications, annual allowances, employee history, manager decisions, cancellation and attendance. Java 21, Spring Boot 4.1.1, Thymeleaf and Spring Data JPA. MySQL is supported; H2 provides a quick local demonstration.

## Run locally

Install JDK 21 and ensure `java` is on PATH. Maven is downloaded by the included wrapper on first use.

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```sh
sh mvnw spring-boot:run
```

Open http://localhost:8080/employee/login. The default `demo` profile uses a persistent H2 database in `data/` and seeds an empty database once.

| Account | Role | Password |
|---|---|---|
| alice | Employee reporting to Bob | DemoPass123! |
| charlie | Employee reporting to Bob | DemoPass123! |
| bob | Manager and employee reporting to Carol | DemoPass123! |
| carol | Manager and employee; no manager assigned | DemoPass123! |
| admin | Administrator, via /admin/login | DemoPass123! |

Passwords above are deliberately public demo credentials and are stored as BCrypt hashes. Do not enable demo seeding on an exposed installation. Carol cannot submit until a manager is assigned. Optional user-management screens are outside this release.

### Demonstration walkthrough

1. Sign in as Alice. Inspect allowance cards and the seeded pending application.
2. Submit another course on a non-overlapping future working day. Internal training must cost zero; external/certification courses require positive fees and full days.
3. Sign out and sign in as Bob. Open **Team approvals** and select Alice's application.
4. Review allowance usage and Charlie's overlapping approved course; approve or reject with a reason.
5. Return as Alice to see the reason. Cancel an approved future application or mark the seeded past approved course attended with experience comments.

## MySQL

Create a database named `cats` and a database user with permissions on it. Configure environment variables:

```powershell
$env:SPRING_PROFILES_ACTIVE='mysql'
$env:DB_URL='jdbc:mysql://localhost:3306/cats'
$env:DB_USER='cats'
$env:DB_PASSWORD='your-local-password'
$env:CATS_SEED='true' # demo database only
.\mvnw.cmd spring-boot:run
```

Alternatively, copy `.env.example` to `.env`, replace both passwords, then run `docker compose up --build`. The database stays in a named volume. The Compose app uses the MySQL profile. Docker is optional.

For this learning project Hibernate creates/updates tables. Use versioned database migrations and managed account provisioning before a production rollout. With seeding disabled, provision users, roles, manager relationships, entitlements and holidays before login/submission. Never commit database credentials or `.env`.

## Build and tests

```powershell
.\mvnw.cmd verify
java -jar target/cats-1.0.0.jar
```

On macOS/Linux replace `.\mvnw.cmd` with `sh mvnw`. GitHub Actions runs `verify` with Java 21. Integration tests use an isolated in-memory H2 database, a fixed clock, real repositories/services and rendered MVC pages. They exercise lifecycle transitions, validation, budget reservations, working days, ownership, stale edits, concurrent submissions, login and CSRF. MySQL/Docker deployment requires its own environment verification.

## Architecture

```text
Browser / Thymeleaf
        ↓
MVC controllers + form validation
        ↓
Transactional business services
        ↓
Spring Data JPA repositories
        ↓
H2 (demo) / MySQL
```

| Package | Contents |
|---|---|
| model | User, CourseApplication, AnnualEntitlement, PublicHoliday |
| model.enums | Role, Designation, CourseCategory, ApplicationStatus, DaySession |
| repository | Four JpaRepository interfaces, history/overlap queries, employee lock |
| service | AuthenticationService, CourseApplicationService, ApprovalService, EntitlementService, TrainingCalendarService |
| controller | LoginController, EmployeeController, CourseApplicationController, ManagerController, shared PageAdvice |
| dto.form | LoginForm, ApplicationForm, DecisionForm, CompletionForm |
| dto.view | AnnualUsage, ApprovalContext |
| exception | BusinessException, NotFoundException, MvcExceptionHandler |
| config | Clock/password configuration, session and CSRF interceptor, demo data |

Controllers return views and delegate business rules to services. Forms cannot bind application ownership or approval status. Mutations lock the employee row before validating capacity/overlap and changing state. `@Version` and submitted version checks detect stale edits. Deleted applications retain their history. Query methods fetch associations needed by pages; open-in-view is disabled.

## Implemented rules and policy decisions

- Start date is in the future at submission/edit. Same-day courses are allowed. Endpoints must be working days.
- Exclude weekends and dates stored in PublicHoliday. Internal training permits half-day boundaries; other categories require AM start and PM end.
- APPLIED and UPDATED reserve allowance. APPROVED and COMPLETED count as committed usage. Cancelled, rejected and deleted records consume none.
- Date-level overlap with APPLIED/UPDATED/APPROVED is rejected, even when different half-day sessions are selected.
- Updates exclude the current application from previous usage and overlap, then validate the proposed values once.
- Cross-year days are allocated to each calendar year; the full fee is charged to the start year. All touched years need entitlement records.
- Personal and subordinate history shows courses intersecting the current year.
- Pending applications can be updated/deleted or approved/rejected. Both decisions need a reason. Only approved applications can be cancelled/completed. Completion requires today to be after the end date and experience comments.
- Working-day and allowance values are recomputed from applications and the configured calendar. Changing historical holidays can affect computed usage; calendar maintenance/versioning is outside this core release.
- Session identity, owner/manager checks, session renewal at login, BCrypt passwords and CSRF tokens are implemented. A full Spring Security integration remains an optional extension.
- Training provider is required in this implementation. Annual limits are data, not hardcoded service constants.

Reservation statuses, cross-year allocation and half-day overlap are explicit proposed defaults, not wording dictated by the brief. Agree these policies with the team/lecturer.

### Holiday data

The demo seed includes only New Year's Day and Christmas Day for the previous/current/next year. This is an **illustrative calendar, not the complete Singapore holiday list**. Load the full organisation-approved calendar, including observed holidays, before using real training dates. Tests insert their own holiday fixtures. Demo entitlements are seeded for the same three years; seeding runs only on an empty user table and does not automatically roll over years.

## Scope

Mandatory employee/manager workflows and separate admin login are implemented. The admin landing page explicitly identifies the scope. Optional administration CRUD, course catalogue, claims/ledger, reports, monthly calendar, email, REST client/API and pagination are not implemented. Their design remains in the blueprint; this release follows the agreed core package structure.

## Reference and attribution

Designed against the supplied SA63 CA Instructions v2.0 and course materials. Layering follows examples from https://github.com/suriarasai/SA63, particularly its `demo` project. Maven wrapper scripts originate from that demo and retain their Apache license headers; the Windows wrapper includes a null-safe directory-link check. Course PDFs are not redistributed in this repository. The implementation was generated with AI assistance and should be reviewed, understood and adapted by the project team according to course requirements.
