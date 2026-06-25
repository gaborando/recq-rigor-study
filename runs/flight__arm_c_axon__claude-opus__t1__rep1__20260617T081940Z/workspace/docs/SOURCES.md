# Sources — Axon Framework pack

- Target: **Axon Framework 4.13.1** via `axon-spring-boot-starter`, embedded JPA
  event store on PostgreSQL (no Axon Server — `axon-server-connector` excluded),
  Spring Boot 3.5.14, Java 25.
- Compiled: 2026-06-07.

## Official documentation (Axon Reference Guide)

- Reference Guide (4.11 — closest published reference line to 4.13.x;
  package layout and annotations are unchanged in 4.13) —
  https://docs.axoniq.io/axon-framework-reference/4.11/
- Aggregates / modeling (`@Aggregate`, `@AggregateIdentifier`, `@CommandHandler`,
  `AggregateLifecycle.apply`, `@EventSourcingHandler`) —
  https://docs.axoniq.io/axon-framework-reference/4.11/axon-framework-commands/modeling/aggregate/
- Command handlers / routing (`@TargetAggregateIdentifier`) —
  https://docs.axoniq.io/axon-framework-reference/4.13/axon-framework-commands/command-handlers/
- Command dispatchers (`CommandGateway.send` / `sendAndWait`) —
  https://docs.axoniq.io/axon-framework-reference/4.11/axon-framework-commands/command-dispatchers/
- Query handling / dispatchers (`@QueryHandler`, `QueryGateway`, `ResponseTypes`) —
  https://docs.axoniq.io/axon-framework-reference/4.11/queries/query-dispatchers/
  https://docs.axoniq.io/axon-framework-reference/4.11/queries/query-handlers/
- Event handlers / processing groups / event processors —
  https://docs.axoniq.io/axon-framework-reference/4.11/events/event-handlers/
  https://docs.axoniq.io/axon-framework-reference/4.11/events/event-processors/
- Event Bus & Store (JPA storage engine) —
  https://docs.axoniq.io/axon-framework-reference/4.11/events/infrastructure/
- Sagas (`@Saga`, `@StartSaga`, `@EndSaga`, `@SagaEventHandler`, `SagaLifecycle`) —
  https://docs.axoniq.io/axon-framework-reference/4.11/sagas/implementation/
  https://docs.axoniq.io/axon-framework-reference/4.11/sagas/associations/
- Spring Boot integration / auto-configuration / processor properties —
  https://docs.axoniq.io/axon-framework-reference/4.11/spring-boot-integration/
- Releases (4.13.x line; Spring Boot 4+ support) —
  https://github.com/AxonFramework/AxonFramework/releases
- API docs (ResponseTypes package `org.axonframework.messaging.responsetypes`) —
  https://apidocs.axoniq.io/latest/org/axonframework/messaging/responsetypes/ResponseTypes.html

## Verified package/import facts
- `@Aggregate` → `org.axonframework.spring.stereotype.Aggregate` (Spring stereotype).
- `@AggregateIdentifier`, `@TargetAggregateIdentifier`, `AggregateLifecycle`,
  `AggregateNotFoundException` → `org.axonframework.modelling.command`.
- `@CommandHandler` → `org.axonframework.commandhandling.CommandHandler`;
  `CommandGateway` → `org.axonframework.commandhandling.gateway`;
  `CommandExecutionException` → `org.axonframework.commandhandling`.
- `@EventSourcingHandler` → `org.axonframework.eventsourcing`.
- `@EventHandler` → `org.axonframework.eventhandling`.
- `@QueryHandler`, `QueryGateway` → `org.axonframework.queryhandling`;
  `ResponseTypes` → `org.axonframework.messaging.responsetypes`.
- `@Saga` → `org.axonframework.spring.stereotype`; `@StartSaga`/`@EndSaga`/
  `@SagaEventHandler`/`SagaLifecycle` → `org.axonframework.modelling.saga`.
- `@ProcessingGroup` → `org.axonframework.config`.

## Could not fully verify (flagged)
- **4.13-specific reference pages:** AxonIQ publishes the reference under 4.11 (and
  some 4.13 command pages); 4.13.1's docs are largely the 4.11 line. Annotation
  packages above are stable across 4.x and were cross-checked against API docs,
  but exact 4.13.1 wording of some pages was inferred from 4.11.
- **Processor property keys** (`axon.eventhandling.processors.<group>.mode/source/
  thread-count`) match the 4.x Spring Boot integration; confirm against the
  generated `spring-configuration-metadata.json` on the actual classpath.
- **`@ProcessingGroup` location:** `org.axonframework.config.ProcessingGroup` in
  4.x; in some builds it is re-exported — verify on the classpath if the import
  fails.

## Approx token count
- GUIDE.md ~17.1 KB → ~4,280 tokens (chars / 4)
- EXAMPLE.md ~13.0 KB → ~3,260 tokens
- SOURCES.md ~4.3 KB → ~1,060 tokens
- Pack total ≈ **8,600 tokens** (well under the 25k / 100 KB budget).
