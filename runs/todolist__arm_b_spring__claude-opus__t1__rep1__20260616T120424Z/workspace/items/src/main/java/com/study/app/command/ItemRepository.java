package com.study.app.command;

import com.study.app.domain.Item;
import com.study.app.domain.ItemKey;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, ItemKey> {

    List<Item> findByKeyListId(UUID listId);

    long countByKeyListId(UUID listId);

    long countByKeyListIdAndCheckedTrue(UUID listId);

    /** Targeted column update so a concurrent rename and check never lose each other. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Item i set i.checked = :checked where i.key.listId = :listId and i.key.itemId = :itemId")
    int setChecked(@Param("listId") UUID listId, @Param("itemId") UUID itemId, @Param("checked") boolean checked);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Item i set i.content = :content where i.key.listId = :listId and i.key.itemId = :itemId")
    int setContent(@Param("listId") UUID listId, @Param("itemId") UUID itemId, @Param("content") String content);

    @Query("select count(i) from Item i")
    long totalItems();

    @Query("select count(i) from Item i where i.checked = true")
    long checkedItems();
}
