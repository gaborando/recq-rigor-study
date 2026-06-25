package com.study.app.query;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<ItemEntity, String> {

    List<ItemEntity> findByListId(String listId);

    long countByListId(String listId);

    long countByListIdAndCheckedTrue(String listId);

    long countByCheckedTrue();
}
