package com.study.app.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "item")
public class Item {

    @EmbeddedId
    private ItemKey key;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private boolean checked;

    protected Item() {}

    public Item(ItemKey key, String content) {
        this.key = key;
        this.content = content;
        this.checked = false;
    }

    public ItemKey getKey() { return key; }
    public String getContent() { return content; }
    public boolean isChecked() { return checked; }
}
