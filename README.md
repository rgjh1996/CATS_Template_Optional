# CATS: core + all CA optional features

Java 21, Spring Boot 4.1.1, Thymeleaf, Spring Data JPA and Spring Security. Implements the mandatory workflows and all eight optional feature areas in the supplied **SA63 CA Instructions v2.0, pages 8–9**.

This repository extends [CATS_Template](https://github.com/rgjh1996/CATS_Template) commit `b62d1c18eec1d67116413e0696dac43f29af1592`. [CORE_REFERENCE.md](CORE_REFERENCE.md) preserves that version's README for comparison; its scope statements describe the core version only.

## Run locally

Install JDK 21 and MySQL 8. Run [database/mysql-setup.sql](database/mysql-setup.sql) in MySQL Workbench, replacing its example password. Then set the app's database credentials and start:

```powershell
$env:DB_USER='cats'
$env:DB_PASSWORD='your-local-password'
$env:CATS_SEED='true' # empty demonstration database only
.\mvnw.cmd spring-boot:run
```

On macOS/Linux export the same variables and use `sh mvnw spring-boot:run`. Open [employee login](http://localhost:8080/employee/login) or [admin login](http://localhost:8080/admin/login). **MySQL is the default**, using `jdbc:mysql://localhost:3306/cats_optional`. Override `DB_URL` if needed. On an empty database, `CATS_SEED=true` creates the demo accounts and data.

For a quick H2 demonstration without MySQL, explicitly run `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"`. That profile uses a separate database under `data/cats-optional`. Automated tests use isolated H2 by default and support MySQL through `CATS_TEST_DB_URL`, `CATS_TEST_DB_USER` and `CATS_TEST_DB_PASSWORD`. The test database must be disposable: tests recreate its tables.

| Username | Access | Password |
|---|---|---|
| alice, charlie | Employee reporting to Bob | DemoPass123! |
| bob | Manager + employee reporting to Carol | DemoPass123! |
| carol | Manager + employee | DemoPass123! |
| admin | Administrator; use admin login | DemoPass123! |

These are public demo credentials. Enable seeding only for local demonstration databases. Carol needs an assigned manager before applying. Use separate databases and a different server port to run the core and optional projects simultaneously.

## Optional features

| CA optional area | Implementation | Entry point |
|---|---|---|
| Administration | Create/edit employees, deactivate access, assign roles and managers; maintain categories, providers, catalogue, holidays, annual days and budget | `/admin` |
| REST + client | Own application GET/POST endpoints, Spring Data REST catalogue repository, JavaScript client using both | `/rest-client` |
| Claims + ledger | Self-paid completed courses, receipt/certificate uploads, reasoned manager decisions, reimbursement recording, annual ledger | `/claims`, `/ledger` |
| Reporting + CSV | Attendance by period/category, claims and annual budget for all/one subordinate, three CSV exports | `/manager/reports` |
| Monthly calendar | Approved courses across employees, previous/next month and dropdown | `/calendar` |
| Pagination | Personal/subordinate history, approvals, calendar and REST; selectable 10/20/25 application rows | List pages |
| Email | Transactional outbox for submission and approval/rejection, reason + login link, SMTP delivery and retry | `/admin/mail` |
| Spring Security | Session authentication, role/account checks, BCrypt, CSRF, attachment authorization | `SecurityConfig` |

See the [implementation guide](docs/OPTIONAL_FEATURES.md) and [editable UML](docs/cats-optional.puml) and [rendered diagram](docs/CATS_Optional.svg).

## Walkthrough

1. **Admin:** edit a user's email, roles, manager and entitlement. Create a provider and course; maintain category labels and availability.
2. **Alice:** open **More → Course catalogue & REST client**. Use a catalogue course to prefill an application, or submit from the REST form on a future working date.
3. **Bob:** review **Team approvals**, then approve/reject with a reason. Inspect both messages in the admin email outbox.
4. **Alice:** open the seeded **Completed certification for fee claim**. Choose **Claim course fee**, confirm self-payment, and attach a receipt and certificate (PDF/PNG/JPEG, up to 5 MB each).
5. **Bob:** open **More → Team fee claims** and approve/reject with a reason.
6. **Admin:** open **Reimbursements** and record an approved claim as reimbursed. This records an externally made payment; it does not transfer money.
7. **Alice:** inspect the ledger. **Bob:** filter and export reports. **Any account:** browse the monthly calendar.

## Email

The default profile records messages in the outbox. Set `CATS_MAIL_ENABLED=true` to enable delivery. The Docker stack below delivers to **Mailpit**, a local mailbox viewer at [localhost:8025](http://localhost:8025).

For another SMTP server, configure:

```powershell
$env:CATS_MAIL_ENABLED='true'
$env:MAIL_HOST='your-smtp-host'
$env:MAIL_PORT='587'
$env:MAIL_AUTH='true'
$env:MAIL_STARTTLS='true'
$env:MAIL_USERNAME='your-smtp-user'
$env:MAIL_PASSWORD='your-smtp-password'
$env:MAIL_FROM='cats@your-domain.example'
$env:CATS_PUBLIC_URL='http://localhost:8080'
.\mvnw.cmd spring-boot:run
```

Use deliverable employee addresses for real email; seeded `example.test` addresses are for Mailpit/testing. The worker retries every 30 seconds, up to five attempts; admins can requeue failures. Messages are queued in the application transaction. Delivery is at least once: interruption after SMTP acceptance but before database commit can duplicate a message.

## MySQL + Docker

Copy `.env.example` to `.env`, replace both database passwords, then run:

```sh
docker compose up --build
```

This starts the app on port 8080, MySQL 8.4 with a persistent volume, and Mailpit on localhost:8025. Alternatively, run MySQL yourself and set `SPRING_PROFILES_ACTIVE=mysql`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, and `CATS_SEED=true` for an empty demo database.

Hibernate manages the learning-project schema. Use an independent database for the optional version. With seeding disabled, accounts and reference data require provisioning. Do not commit `.env` or database files.

## Verification

```powershell
.\mvnw.cmd verify
java -jar target/cats-2.0.0.jar
```

GitHub Actions has separate H2 and MySQL 8.4 verification jobs. Tests cover core transitions, concurrency, validation, ownership, optional claims, attachments, ledger, administration, reports, CSV escaping, REST, pagination, role changes, CSRF, outbox transactions, retries and real SMTP delivery to a local test socket.

## Policies and demo data

- Pending applications reserve allowance; approved/completed applications commit it. Cancellation releases fees. Reimbursement is recorded separately and does not deduct the fee twice.
- Days are allocated per calendar year. Fees and reimbursement entries belong to the course start year.
- One claim per application, for the full approved fee, with self-payment confirmation and both attachments. Rejected claims remain in history; resubmission is outside this policy.
- Employee deletion disables access and preserves history. Reassign subordinates before disabling/demoting their manager. Hierarchy cycles and removal of your own admin access are rejected.
- The three CA category codes remain enums; administrators edit labels and availability. Applications preserve their own details when catalogue records change.
- Holidays overlapping pending/approved/completed courses cannot be added, moved or deleted because usage is calculated from the calendar. Description edits are allowed. Configure holidays before accepting applications.
- Demo holidays include only New Year's Day and Christmas Day for three years. **Load the full organisation-approved holiday calendar before using real dates.** Entitlements do not roll over automatically.
- Attendance reports select overlapping approved/completed courses and show full course totals. Claims use submission date. Budget reports show annual totals for years touched by the period; category filters apply to attendance/claims. Missing entitlements are labelled.
- Attachment validation checks file signatures and size; authorized downloads use attachment disposition. This is not malware scanning.

## Attribution

Based on the supplied CA and course materials. Controller → Service → Repository → Entity layering follows the lecturer's [SA63 demo](https://github.com/suriarasai/SA63). Maven wrapper files retain their Apache license headers. Course PDFs are not redistributed. This AI-assisted implementation is a reference for review, learning and adaptation under the course requirements.
