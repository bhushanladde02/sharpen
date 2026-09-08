package io.sharpen.service;

/** The numbers shown publicly on the landing page and in the app shell. */
public record CommunityStats(long members, long companies, long sessions, long reports) {}
