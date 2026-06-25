# Sources

- **Framework version:** Evento Framework 2.1.0 (`evento-bundle:2.1.0`)
- **Date compiled:** 2026-06-07
- **Target stack:** Java 17+, Spring Boot 3.5.x

Every signature, annotation attribute, and base-class convention in GUIDE.md and
EXAMPLE.md was read directly from the source files below (no API was invented).

## Annotations (`evento-common`)

- `…/modeling/annotations/component/Aggregate.java` — `int snapshotFrequency() default -1`
- `…/modeling/annotations/component/Projector.java` — `int version()` (required)
- `…/modeling/annotations/component/Projection.java` — no attributes
- `…/modeling/annotations/component/Saga.java` — `int version()` (required)
- `…/modeling/annotations/component/Service.java` — no attributes
- `…/modeling/annotations/component/Observer.java` — `int version()` (required)
- `…/modeling/annotations/component/Invoker.java` — no attributes
- `…/modeling/annotations/component/Component.java` — meta-annotation
- `…/modeling/annotations/handler/AggregateCommandHandler.java` — `boolean init() default false`
- `…/modeling/annotations/handler/CommandHandler.java` — no attributes
- `…/modeling/annotations/handler/EventHandler.java` — `int retry() default -1; int retryDelay() default 1000`
- `…/modeling/annotations/handler/EventSourcingHandler.java` — no attributes
- `…/modeling/annotations/handler/QueryHandler.java` — no attributes
- `…/modeling/annotations/handler/SagaEventHandler.java` — `boolean init() default false; String associationProperty(); int retry() default -1; int retryDelay() default 1000`
- `…/modeling/annotations/handler/InvocationHandler.java` — no attributes
- `…/modeling/annotations/handler/Handler.java` — meta-annotation

(Base path: `evento-common/src/main/java/com/evento/common`)

## Payload / query / state base types (`evento-common`)

- `…/modeling/messaging/payload/Payload.java`, `TrackablePayload.java`
- `…/modeling/messaging/payload/Command.java` — `getAggregateId()`, `getLockId()`
- `…/modeling/messaging/payload/DomainCommand.java` — `getLockId()` defaults to `getAggregateId()`
- `…/modeling/messaging/payload/ServiceCommand.java` — `getLockId()` default null, `getAggregateId()` returns `getLockId()`
- `…/modeling/messaging/payload/Event.java`, `DomainEvent.java`, `ServiceEvent.java` — `getContext()`/`setContext()`
- `…/modeling/messaging/payload/Query.java` — `Class<T> getResponseType()`
- `…/modeling/messaging/payload/View.java`, `Invocation.java`
- `…/modeling/messaging/query/QueryResponse.java`, `Single.java` (`Single.of`), `Multiple.java` (`Multiple.of` collection + varargs)
- `…/modeling/state/AggregateState.java` — `isDeleted()/setDeleted()`
- `…/modeling/state/SagaState.java` — `isEnded()/setEnded()`, `setAssociation/getAssociation/getAssociations`

## Gateways (`evento-common`)

- `…/messaging/gateway/CommandGateway.java` — `send(...)` overloads → `CompletableFuture` (no `sendAndWait`)
- `…/messaging/gateway/QueryGateway.java` — `query(...)` overloads → `CompletableFuture`
- `…/messaging/gateway/Gateway.java`, `CommandGatewayImpl.java`, `QueryGatewayImpl.java`

## Bundle / runtime (`evento-bundle`)

- `…/application/EventoBundle.java` — `Builder` (setBasePackage / setBundleId / setBundleVersion / setEventoServerMessageBusConfiguration / setInjector / setComponentContexts / setConsumerEngineConfigBuilder / strictConfinement), `getInvoker(Class<T extends InvokerWrapper>)`, `start()`
- `…/application/proxy/InvokerWrapper.java` — `getCommandGateway()/getQueryGateway()` (throw outside `@InvocationHandler`)
- `…/application/consumer/ConsumerEngineConfig.java` — record `(processor, stateStore, deadEventQueue)`; `inMemory(...)` static factory; lock/checkpoint/DLQ/saga-store/dedupe + virtual-thread observer executor
- `…/application/scanner/…/ConfinementScanner.java` — startup sweep flagging gateway calls outside component classes (warn, or fail under `strictConfinement`)
- `…/application/reference/AggregateReference.java`, `SagaReference.java` — confirmed handler parameter injection by type (payload, state, `CommandGateway`, `QueryGateway`, `Metadata`)
- `…/application/manager/AggregateManager.java` — confirmed single-event return wrapped in one `DomainEventMessage`; snapshot frequency check

(Base path: `evento-bundle/src/main/java/com/evento`)

## Reference applications

- `evento-lab/src/main/java/com/evento/lab/**` — single-bundle reference (LabAggregate, LabAggregateState, LabService, LabProjector, LabProjection, LabSaga, LabSagaState, LabObserver, api command/event/query/view); primary adaptation source for EXAMPLE.md
- `evento-lab-microservices/**` — Spring Boot integration pattern: `evento-lab-ms-command` (OrderAggregate, OrderService, EventoConfiguration), `evento-lab-ms-query` (OrderProjector, OrderProjection, OrderViewStore, EventoConfiguration with `ConsumerEngineConfig::inMemory`), `evento-lab-ms-saga` (OrderSaga sending commands via injected `CommandGateway`), `evento-lab-ms-observer` (OrderObserver sending commands)

## Skeleton (study harness)

- `skeletons/arm_a_evento/src/main/java/com/study/app/App.java` — `@SpringBootApplication`
- `skeletons/arm_a_evento/src/main/java/com/study/app/config/EventoConfiguration.java` — pre-wired bundle bean (mirrored verbatim)
- `skeletons/arm_a_evento/src/main/resources/application.properties` — `evento.server.*`, `evento.bundle.id=order-app`, Postgres datasource
- `skeletons/arm_a_evento/pom.xml` — `spring-boot-starter-parent:3.5.14`, `evento-bundle:2.1.0`, web + actuator + data-jpa + postgresql

## GitBook docs repo (cross-check)

- `evento-doc/` — RECQ component pattern pages referenced from annotation javadoc (docs.eventoframework.com/recq-patterns/recq-component-pattern/*). Used only to confirm role semantics; all API signatures taken from source.

---

## Pack size (estimate: chars / 4)

| File | Chars | ~Tokens |
|------|-------|---------|
| GUIDE.md | 22,429 | ~5,610 |
| EXAMPLE.md | 19,733 | ~4,930 |
| SOURCES.md | ~3,900 | ~975 |
| **Total** | **~46,000** | **~11,500** |

Well within the ≤ 25k-token / ≤ 100 KB budget.
