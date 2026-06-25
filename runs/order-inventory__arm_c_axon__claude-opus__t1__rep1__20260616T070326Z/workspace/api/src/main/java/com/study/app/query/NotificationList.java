package com.study.app.query;

import java.util.List;

/**
 * Wrapper so the query response carries its element type through the Jackson
 * serializer. A bare List loses its generic type over the wire, which breaks
 * MultipleInstancesResponseType conversion across services.
 */
public record NotificationList(List<NotificationDto> notifications) {}
