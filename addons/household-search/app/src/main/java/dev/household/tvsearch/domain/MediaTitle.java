package dev.household.tvsearch.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** API-independent identity includes its source namespace and media type. */
public final class MediaTitle {
    public enum Type { MOVIE, TV }

    public final String id;
    public final Type type;
    public final String title;
    public final String originalTitle;
    public final String artwork;
    public final Integer year;
    public final Double rating;
    public final String ratingSource;
    public final Integer runtimeMinutes;
    public final String description;
    public final List<String> genres;

    public MediaTitle(String namespace, String sourceId, Type type, String title,
                      String originalTitle, String artwork, Integer year, Double rating,
                      String ratingSource, Integer runtimeMinutes, String description, List<String> genres) {
        if (namespace == null || namespace.isEmpty() || sourceId == null || sourceId.isEmpty()
                || title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title identity and name are required");
        }
        this.type = Objects.requireNonNull(type);
        this.id = namespace + ":" + type.name().toLowerCase(java.util.Locale.ROOT) + ":" + sourceId;
        this.title = title;
        this.originalTitle = originalTitle;
        this.artwork = artwork;
        this.year = year;
        this.rating = rating;
        this.ratingSource = ratingSource;
        this.runtimeMinutes = runtimeMinutes;
        this.description = description;
        this.genres = Collections.unmodifiableList(new ArrayList<>(genres));
    }
}
