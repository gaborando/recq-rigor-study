package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.view.ListView;

public class GetListQuery extends Query<Single<ListView>> {
    private String listId;

    public GetListQuery() {}
    public GetListQuery(String listId) { this.listId = listId; }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
}
