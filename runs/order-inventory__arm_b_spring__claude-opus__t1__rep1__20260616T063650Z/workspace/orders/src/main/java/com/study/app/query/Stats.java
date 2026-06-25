package com.study.app.query;

/** Aggregate statistics over all orders. */
public record Stats(long confirmed, long rejected, long revenue) {}
