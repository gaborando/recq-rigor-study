package com.study.app.command;

import com.study.app.command.Commands.CreateListCommand;
import com.study.app.domain.Events.ListCreatedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * Write-side identity of a list. The client supplies the {@code listId};
 * re-issuing the create command is an idempotent no-op (never a second list,
 * never an item reset), enforced by the aggregate's serialized command stream.
 * Item membership and completion are derived on the read side from the item
 * event stream (see {@code ListProjection}).
 */
@Aggregate
public class TodoListAggregate {

    @AggregateIdentifier
    private String listId;

    protected TodoListAggregate() {
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public void handle(CreateListCommand cmd) {
        if (listId != null) {
            return; // idempotent create
        }
        apply(new ListCreatedEvent(cmd.listId(), cmd.name()));
    }

    @EventSourcingHandler
    public void on(ListCreatedEvent e) {
        this.listId = e.listId();
    }
}
