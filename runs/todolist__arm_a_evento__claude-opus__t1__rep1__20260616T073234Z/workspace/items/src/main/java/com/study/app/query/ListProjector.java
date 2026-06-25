package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.ItemAddedEvent;
import com.study.app.domain.event.ItemCheckedEvent;
import com.study.app.domain.event.ItemRenamedEvent;
import com.study.app.domain.event.ItemUncheckedEvent;
import com.study.app.domain.event.ListCreatedEvent;
import com.study.app.query.store.ItemEntity;
import com.study.app.query.store.ItemKey;
import com.study.app.query.store.ItemRepository;
import com.study.app.query.store.ListEntity;
import com.study.app.query.store.ListRepository;

/**
 * Builds the durable list/item read model in this service's PostgreSQL. A single
 * active consumer applies events in store order, so every handler is a simple
 * idempotent upsert (safe under at-least-once redelivery and restart replay).
 */
@Projector(version = 1)
public class ListProjector {

    private final ListRepository lists;
    private final ItemRepository items;

    public ListProjector(ListRepository lists, ItemRepository items) {
        this.lists = lists;
        this.items = items;
    }

    @EventHandler
    void on(ListCreatedEvent e) {
        if (!lists.existsById(e.getListId())) {
            lists.save(new ListEntity(e.getListId(), e.getName()));
        }
    }

    @EventHandler
    void on(ItemAddedEvent e) {
        ItemKey key = new ItemKey(e.getListId(), e.getItemId());
        if (!items.existsById(key)) {
            items.save(new ItemEntity(e.getListId(), e.getItemId(), e.getContent(), false));
        }
    }

    @EventHandler
    void on(ItemCheckedEvent e) {
        items.findById(new ItemKey(e.getListId(), e.getItemId())).ifPresent(it -> {
            it.setChecked(true);
            items.save(it);
        });
    }

    @EventHandler
    void on(ItemUncheckedEvent e) {
        items.findById(new ItemKey(e.getListId(), e.getItemId())).ifPresent(it -> {
            it.setChecked(false);
            items.save(it);
        });
    }

    @EventHandler
    void on(ItemRenamedEvent e) {
        items.findById(new ItemKey(e.getListId(), e.getItemId())).ifPresent(it -> {
            it.setContent(e.getContent());
            items.save(it);
        });
    }
}
