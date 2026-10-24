package dev.household.tvsearch.domain;

import java.util.Objects;

/** Common identity and playback metadata; catalog details remain reusable, not duplicated. */
public final class MediaEntity {
    public enum Type { MOVIE, TV, EPISODE, APP }
    public enum Source { FIXTURE, TMDB, WATCH_NEXT, PREVIEW, INSTALLED_APP }

    public final String id;
    public final Long tmdbId;
    public final Type type;
    public final String title;
    public final String subtitle;
    public final String artworkUri;
    public final String provider;
    public final String providerPackage;
    public final String playbackIntent;
    public final Long positionMillis;
    public final Long durationMillis;
    public final String season;
    public final String episode;
    public final Source source;
    public final MediaTitle catalogTitle;

    private MediaEntity(Builder b) {
        if (b.id == null || b.id.trim().isEmpty() || b.title == null || b.title.trim().isEmpty())
            throw new IllegalArgumentException("Media identity and title are required");
        id = b.id;
        title = b.title.trim();
        type = Objects.requireNonNull(b.type);
        source = Objects.requireNonNull(b.source);
        tmdbId = b.tmdbId;
        subtitle = b.subtitle;
        artworkUri = b.artworkUri;
        provider = b.provider;
        providerPackage = b.providerPackage;
        playbackIntent = b.playbackIntent;
        positionMillis = b.positionMillis != null && b.positionMillis >= 0 ? b.positionMillis : null;
        durationMillis = b.durationMillis != null && b.durationMillis > 0 ? b.durationMillis : null;
        season = b.season;
        episode = b.episode;
        catalogTitle = b.catalogTitle;
    }

    public Integer progressPercent() {
        if (positionMillis == null || durationMillis == null) return null;
        return (int) Math.round(Math.min(1d, (double) positionMillis / durationMillis) * 100);
    }

    public static MediaEntity fromTitle(MediaTitle title) {
        Builder b = new Builder(title.id, title.type == MediaTitle.Type.MOVIE ? Type.MOVIE : Type.TV,
                title.title, title.id.startsWith("tmdb:") ? Source.TMDB : Source.FIXTURE);
        b.catalogTitle = title;
        b.artworkUri = title.artwork;
        if (b.source == Source.TMDB) {
            b.tmdbId = Long.valueOf(title.id.substring(title.id.lastIndexOf(':') + 1));
            if (b.tmdbId <= 0) throw new IllegalArgumentException("TMDB IDs must be positive");
        }
        return b.build();
    }

    public static final class Builder {
        private final String id;
        private final Type type;
        private final String title;
        private final Source source;
        private Long tmdbId;
        private String subtitle, artworkUri, provider, providerPackage, playbackIntent, season, episode;
        private Long positionMillis, durationMillis;
        private MediaTitle catalogTitle;

        public Builder(String id, Type type, String title, Source source) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.source = source;
        }

        public Builder subtitle(String value) { subtitle = value; return this; }
        public Builder artwork(String value) { artworkUri = value; return this; }
        public Builder provider(String label, String packageName) {
            provider = label; providerPackage = packageName; return this;
        }
        public Builder playback(String value) { playbackIntent = value; return this; }
        public Builder progress(Long position, Long duration) {
            positionMillis = position; durationMillis = duration; return this;
        }
        public Builder episode(String seasonNumber, String episodeNumber) {
            season = seasonNumber; episode = episodeNumber; return this;
        }
        public MediaEntity build() { return new MediaEntity(this); }
    }
}
