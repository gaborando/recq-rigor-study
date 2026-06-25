package com.study.app.domain.view;

import java.io.Serializable;

/** Item entry inside a {@link ListView}. Plain serializable POJO (Jackson). */
public class ItemView implements Serializable {
    private String itemId;
    private String content;
    private boolean checked;

    public ItemView() {}
    public ItemView(String itemId, String content, boolean checked) {
        this.itemId = itemId;
        this.content = content;
        this.checked = checked;
    }

    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
}
