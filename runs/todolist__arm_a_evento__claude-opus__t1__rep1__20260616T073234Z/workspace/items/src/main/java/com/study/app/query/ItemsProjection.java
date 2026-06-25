package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.error.NotFoundException;
import com.study.app.domain.query.GetListQuery;
import com.study.app.domain.query.GetStatsQuery;
import com.study.app.domain.view.ItemView;
import com.study.app.domain.view.ListView;
import com.study.app.domain.view.StatsView;
import com.study.app.query.store.ItemEntity;
import com.study.app.query.store.ItemRepository;
import com.study.app.query.store.ListEntity;
import com.study.app.query.store.ListRepository;

import java.util.List;
import java.util.stream.Collectors;

@Projection
public class ItemsProjection {

    private final ListRepository lists;
    private final ItemRepository items;

    public ItemsProjection(ListRepository lists, ItemRepository items) {
        this.lists = lists;
        this.items = items;
    }

    @QueryHandler
    Single<ListView> handle(GetListQuery q) {
        ListEntity list = lists.findById(q.getListId())
                .orElseThrow(() -> new NotFoundException("NOT_FOUND list " + q.getListId()));
        List<ItemEntity> rows = items.findByListId(q.getListId());
        List<ItemView> itemViews = rows.stream()
                .map(it -> new ItemView(it.getItemId(), it.getContent(), it.isChecked()))
                .collect(Collectors.toList());
        return Single.of(new ListView(list.getListId(), list.getName(), status(rows), itemViews));
    }

    @QueryHandler
    Single<StatsView> handle(GetStatsQuery q) {
        long totalLists = lists.count();
        long completed = items.countCompletedLists();
        long active = totalLists - completed;
        long totalItems = items.count();
        long checkedItems = items.countByCheckedTrue();
        return Single.of(new StatsView(active, completed, totalItems, checkedItems));
    }

    /** A list is COMPLETED iff it has at least one item and all items are checked. */
    private static String status(List<ItemEntity> rows) {
        if (rows.isEmpty()) return "ACTIVE";
        boolean allChecked = rows.stream().allMatch(ItemEntity::isChecked);
        return allChecked ? "COMPLETED" : "ACTIVE";
    }
}
