package com.study.app.query.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ItemRepository extends JpaRepository<ItemEntity, ItemKey> {

    List<ItemEntity> findByListId(String listId);

    long countByCheckedTrue();

    /** Number of lists that have at least one item and no unchecked item. */
    @Query("select count(distinct i.listId) from ItemEntity i " +
           "where i.listId not in (select i2.listId from ItemEntity i2 where i2.checked = false)")
    long countCompletedLists();
}
