package com.study.app.query;

import java.util.UUID;

public record ItemView(UUID itemId, String content, boolean checked) {}
