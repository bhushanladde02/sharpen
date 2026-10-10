package io.sharpen.service;

/**
 * The numbers shown publicly: the landing page shows {@code visits} (one browser counted once a day, bots
 * excluded, summed over every day since counting began), {@code pageViews} and {@code countries} (visitors'
 * countries, shown from three); the app shell shows {@code members} and {@code sessions}.
 */
public record CommunityStats(long members, long companies, long sessions, long reports,
                             long visits, long pageViews, long countries) {}
