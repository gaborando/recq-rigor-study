package com.study.app.query;

import com.study.app.domain.Events.ItemAddedEvent;
import com.study.app.domain.Events.ItemCheckedEvent;
import com.study.app.domain.Events.ItemRenamedEvent;
import com.study.app.domain.Events.ItemUncheckedEvent;
import com.study.app.domain.Events.ListCreatedEvent;
import com.study.app.query.Queries.GetListViewQuery;
import com.study.app.query.Queries.GetStatsQuery;
import com.study.app.query.Queries.ItemResponse;
import com.study.app.query.Queries.ListViewResponse;
import com.study.app.query.Queries.StatsResponse;
import java.util.List;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

/**
 * Read model for the lists service: list status + items (content & checked) and
 * the aggregate statistics. Driven by a single-threaded tracking processor that
 * consumes the global event stream in order, so the per-list status it derives
 * is deterministic and converges with the items it stores.
 */
@Component
@ProcessingGroup("list-projection")
public class ListProjection {

    private static final String ACTIVE = "ACTIVE";
    private static final String COMPLETED = "COMPLETED";

    private final ListRepository lists;
    private final ItemRepository items;

    public ListProjection(ListRepository lists, ItemRepository items) {
        this.lists = lists;
        this.items = items;
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void on(ListCreatedEvent e) {
        if (!lists.existsById(e.listId())) {
            lists.save(new ListEntity(e.listId(), e.name(), ACTIVE));
        }
    }

    @EventHandler
    public void on(ItemAddedEvent e) {
        if (!items.existsById(e.itemId())) {
            items.save(new ItemEntity(e.itemId(), e.listId(), e.content(), false));
        }
        recomputeStatus(e.listId());
    }

    @EventHandler
    public void on(ItemCheckedEvent e) {
        items.findById(e.itemId()).ifPresent(it -> {
            it.setChecked(true);
            items.save(it);
        });
        recomputeStatus(e.listId());
    }

    @EventHandler
    public void on(ItemUncheckedEvent e) {
        items.findById(e.itemId()).ifPresent(it -> {
            it.setChecked(false);
            items.save(it);
        });
        recomputeStatus(e.listId());
    }

    @EventHandler
    public void on(ItemRenamedEvent e) {
        items.findById(e.itemId()).ifPresent(it -> {
            it.setContent(e.content());
            items.save(it);
        });
    }

    /** A list is COMPLETED iff it has >=1 item and every item is checked. */
    private void recomputeStatus(String listId) {
        long total = items.countByListId(listId);
        long checked = items.countByListIdAndCheckedTrue(listId);
        String status = (total >= 1 && checked == total) ? COMPLETED : ACTIVE;
        lists.findById(listId).ifPresent(l -> {
            if (!status.equals(l.getStatus())) {
                l.setStatus(status);
                lists.save(l);
            }
        });
    }

    // --------------------------------------------------------------- queries

    @QueryHandler
    public ListViewResponse handle(GetListViewQuery q) {
        return lists.findById(q.listId())
                .map(l -> {
                    List<ItemResponse> itemResponses = items.findByListId(l.getListId()).stream()
                            .map(it -> new ItemResponse(it.getItemId(), it.getContent(), it.isChecked()))
                            .toList();
                    return new ListViewResponse(l.getListId(), l.getName(), l.getStatus(), itemResponses);
                })
                .orElse(null);
    }

    @QueryHandler
    public StatsResponse handle(GetStatsQuery q) {
        long completed = lists.countByStatus(COMPLETED);
        long active = lists.countByStatus(ACTIVE);
        long totalItems = items.count();
        long checkedItems = items.countByCheckedTrue();
        return new StatsResponse(active, completed, totalItems, checkedItems);
    }
}
