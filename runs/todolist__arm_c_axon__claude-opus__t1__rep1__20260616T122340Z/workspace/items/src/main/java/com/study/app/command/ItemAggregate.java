package com.study.app.command;

import com.study.app.command.Commands.AddItemCommand;
import com.study.app.command.Commands.CheckItemCommand;
import com.study.app.command.Commands.RenameItemCommand;
import com.study.app.command.Commands.UncheckItemCommand;
import com.study.app.domain.Events.ItemAddedEvent;
import com.study.app.domain.Events.ItemCheckedEvent;
import com.study.app.domain.Events.ItemRenamedEvent;
import com.study.app.domain.Events.ItemUncheckedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * The write-side authority for a single todo item, identified by the
 * client-supplied {@code itemId}. All operations are idempotent on
 * {@code itemId}; commands targeting an item that was never added fail with
 * {@link ItemNotFoundException} (mapped to HTTP 404 at the edge).
 */
@Aggregate
public class ItemAggregate {

    @AggregateIdentifier
    private String itemId;
    private String listId;
    private boolean checked;
    private String content;

    protected ItemAggregate() {
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public void handle(AddItemCommand cmd) {
        if (itemId != null) {
            return; // idempotent re-add: do not duplicate or reset content/checked
        }
        apply(new ItemAddedEvent(cmd.itemId(), cmd.listId(), cmd.content()));
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public void handle(CheckItemCommand cmd) {
        requireExists(cmd.itemId());
        if (checked) {
            return; // idempotent: checking an already-checked item is a no-op
        }
        apply(new ItemCheckedEvent(itemId, listId));
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public void handle(UncheckItemCommand cmd) {
        requireExists(cmd.itemId());
        if (!checked) {
            return; // idempotent: unchecking an already-unchecked item is a no-op
        }
        apply(new ItemUncheckedEvent(itemId, listId));
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public void handle(RenameItemCommand cmd) {
        requireExists(cmd.itemId());
        if (content != null && content.equals(cmd.content())) {
            return; // idempotent: same content, no event
        }
        apply(new ItemRenamedEvent(itemId, listId, cmd.content()));
    }

    private void requireExists(String requestedId) {
        if (itemId == null) {
            // CREATE_IF_MISSING instantiated an empty aggregate for a never-added
            // item. Throwing before applying any event leaves nothing persisted.
            throw new ItemNotFoundException(requestedId);
        }
    }

    @EventSourcingHandler
    public void on(ItemAddedEvent e) {
        this.itemId = e.itemId();
        this.listId = e.listId();
        this.content = e.content();
        this.checked = false;
    }

    @EventSourcingHandler
    public void on(ItemCheckedEvent e) {
        this.checked = true;
    }

    @EventSourcingHandler
    public void on(ItemUncheckedEvent e) {
        this.checked = false;
    }

    @EventSourcingHandler
    public void on(ItemRenamedEvent e) {
        this.content = e.content();
    }
}
