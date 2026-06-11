# Sources — Spring Boot pack

- Target: Spring Boot **3.5.14** (Spring Framework 6.2.x, Hibernate ORM 6.x,
  Jakarta Persistence 3.x), Java 25, PostgreSQL.
- Compiled: 2026-06-07.

## Official documentation

- Spring Boot reference — https://docs.spring.io/spring-boot/index.html
- Spring Boot data / SQL databases —
  https://docs.spring.io/spring-boot/reference/data/sql.html
- Spring Framework — Data access / `@Transactional` —
  https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- Spring Framework — Transaction-bound events / `@TransactionalEventListener` —
  https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
- Spring Framework — Application events (`ApplicationEventPublisher`) —
  https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events
- Spring Framework — `@Async` / TaskExecutor —
  https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Data JPA — `@Modifying` queries / locking —
  https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
  https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html
- Spring MVC — `@RestController`, `ResponseStatusException` —
  https://docs.spring.io/spring-framework/reference/web/webmvc.html
- Spring Web — `ResponseStatusException` API —
  https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/server/ResponseStatusException.html
- Spring DAO — `DataIntegrityViolationException` API —
  https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/dao/DataIntegrityViolationException.html
- Jakarta Persistence — `LockModeType`, `@Version` —
  https://jakarta.ee/specifications/persistence/3.1/
- HikariCP configuration —
  https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby
- PostgreSQL transaction isolation / SELECT FOR UPDATE —
  https://www.postgresql.org/docs/current/transaction-iso.html
  https://www.postgresql.org/docs/current/explicit-locking.html#LOCKING-ROWS

## Verification notes
- `@TransactionalEventListener` default phase = `AFTER_COMMIT`; to persist in the
  listener use `@Transactional(propagation = REQUIRES_NEW)` — verified against
  Spring Framework reference (transaction event docs).
- `@Async` method runs on a separate thread with no inherited transaction;
  proxy self-invocation bypasses both `@Async` and `@Transactional` — verified
  against Spring scheduling / transaction reference.
- `LockModeType.PESSIMISTIC_WRITE` maps to `SELECT ... FOR UPDATE` on PostgreSQL.

## Approx token count
- GUIDE.md ~16.2 KB → ~4,050 tokens (chars / 4)
- EXAMPLE.md ~11.7 KB → ~2,930 tokens
- SOURCES.md ~2.2 KB → ~550 tokens
- Pack total ≈ **7,500 tokens** (well under the 25k budget).
