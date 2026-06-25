package com.study.app.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.study.app.domain.command.AddItemCommand;
import com.study.app.domain.command.CheckItemCommand;
import com.study.app.domain.command.CreateListCommand;
import com.study.app.domain.command.RenameItemCommand;
import com.study.app.domain.command.UncheckItemCommand;
import com.study.app.domain.error.NotFoundException;
import com.study.app.domain.event.ItemAddedEvent;
import com.study.app.domain.event.ItemCheckedEvent;
import com.study.app.domain.event.ItemRenamedEvent;
import com.study.app.domain.event.ItemUncheckedEvent;
import com.study.app.domain.event.ListCreatedEvent;

/**
 * The collaborative todo list. One aggregate instance per listId is the single
 * consistency boundary: the platform serialises all commands sharing this lockId
 * (= listId), so completion is decided exactly once even under a concurrent final
 * check. All operations are idempotent and re-applying an event is a no-op.
 */
@Aggregate
public class TodoListAggregate {

    // ---- create ------------------------------------------------------------

    @AggregateCommandHandler(init = true)
    ListCreatedEvent handle(CreateListCommand cmd) {
        // A replay (same listId) is rejected by the framework with
        // AggregateInitializedError, which the edge treats as an idempotent success;
        // the existing list is never reset.
        return new ListCreatedEvent(cmd.getListId(), cmd.getName());
    }

    @EventSourcingHandler
    TodoListState on(ListCreatedEvent e, TodoListState state) {
        if (state == null) state = new TodoListState();
        state.setName(e.getName());
        return state;
    }

    // ---- add item ----------------------------------------------------------

    @AggregateCommandHandler
    ItemAddedEvent handle(AddItemCommand cmd, TodoListState state) {
        requireList(state);
        // Idempotent: re-adding the same item is a harmless no-op downstream
        // (read models insert-if-absent; state uses putIfAbsent).
        return new ItemAddedEvent(cmd.getListId(), cmd.getItemId(), cmd.getContent());
    }

    @EventSourcingHandler
    TodoListState on(ItemAddedEvent e, TodoListState state) {
        state.getItems().putIfAbsent(e.getItemId(), false);
        return state;
    }

    // ---- check item --------------------------------------------------------

    @AggregateCommandHandler
    ItemCheckedEvent handle(CheckItemCommand cmd, TodoListState state) {
        requireList(state);
        requireItem(state, cmd.getItemId());

        boolean wasComplete = state.isComplete();
        boolean already = Boolean.TRUE.equals(state.getItems().get(cmd.getItemId()));
        long uncheckedAfter = state.uncheckedCount() - (already ? 0 : 1);
        boolean nowComplete = !state.getItems().isEmpty() && uncheckedAfter == 0;

        boolean transition = !wasComplete && nowComplete;
        long seq = transition ? state.getCompletions() + 1 : 0L;
        return new ItemCheckedEvent(cmd.getListId(), cmd.getItemId(), transition, seq);
    }

    @EventSourcingHandler
    TodoListState on(ItemCheckedEvent e, TodoListState state) {
        state.getItems().put(e.getItemId(), true);
        if (e.isCompletedTransition()) state.setCompletions(e.getCompletionSeq());
        return state;
    }

    // ---- uncheck item ------------------------------------------------------

    @AggregateCommandHandler
    ItemUncheckedEvent handle(UncheckItemCommand cmd, TodoListState state) {
        requireList(state);
        requireItem(state, cmd.getItemId());
        return new ItemUncheckedEvent(cmd.getListId(), cmd.getItemId());
    }

    @EventSourcingHandler
    TodoListState on(ItemUncheckedEvent e, TodoListState state) {
        state.getItems().put(e.getItemId(), false);
        return state;
    }

    // ---- rename item -------------------------------------------------------

    @AggregateCommandHandler
    ItemRenamedEvent handle(RenameItemCommand cmd, TodoListState state) {
        requireList(state);
        requireItem(state, cmd.getItemId());
        return new ItemRenamedEvent(cmd.getListId(), cmd.getItemId(), cmd.getContent());
    }

    @EventSourcingHandler
    TodoListState on(ItemRenamedEvent e, TodoListState state) {
        // content is not part of the aggregate's decision state; handler must exist
        return state;
    }

    // ---- helpers -----------------------------------------------------------

    private static void requireList(TodoListState state) {
        if (state == null) throw new NotFoundException("NOT_FOUND list");
    }

    private static void requireItem(TodoListState state, String itemId) {
        if (!state.hasItem(itemId)) throw new NotFoundException("NOT_FOUND item " + itemId);
    }
}
