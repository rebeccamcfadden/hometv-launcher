package dev.household.tvsearch.data;

import dev.household.tvsearch.domain.MediaEntity;

import java.util.ArrayList;
import java.util.List;

/** Native cursor values stop here; no Android or provider objects reach the UI. */
public final class TvProgramMapper {
    private TvProgramMapper() { }

    public static final class Row {
        public long id;
        public String title, packageName, episodeTitle, season, episode, posterUri, intentUri;
        public Long positionMillis, durationMillis;
        public int programType = -1;
        public int watchNextType = -1;
        public boolean browsable = true;
        public long engagementMillis;
    }

    public static boolean isResume(Row row) {
        // Watch Next CONTINUE and NEXT, not NEW recommendations or WATCHLIST entries.
        return row.browsable && (row.watchNextType == 0 || row.watchNextType == 1);
    }

    public static MediaEntity map(Row row, MediaEntity.Source source, String providerLabel) {
        if (row.id <= 0 || clean(row.title) == null || clean(row.packageName) == null)
            throw new IllegalArgumentException("TV program needs its row ID, title and owning package");
        boolean episode = clean(row.season) != null || clean(row.episode) != null || row.programType == 3;
        MediaEntity.Type type = episode ? MediaEntity.Type.EPISODE
                : row.programType == 0 ? MediaEntity.Type.MOVIE : MediaEntity.Type.TV;
        List<String> detail = new ArrayList<>();
        if (clean(row.season) != null) detail.add("S" + row.season.trim());
        if (clean(row.episode) != null) detail.add("E" + row.episode.trim());
        if (clean(row.episodeTitle) != null) detail.add(row.episodeTitle.trim());
        String table = source == MediaEntity.Source.WATCH_NEXT ? "watch_next_program" : "preview_program";
        return new MediaEntity.Builder("tvprovider:" + table + ":" + row.packageName + ":" + row.id,
                type, row.title, source)
                .subtitle(String.join(" | ", detail))
                .provider(providerLabel, row.packageName)
                .artwork(clean(row.posterUri))
                .playback(clean(row.intentUri))
                .progress(row.positionMillis, row.durationMillis)
                .episode(clean(row.season), clean(row.episode))
                .build();
    }

    private static String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
