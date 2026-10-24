package dev.household.tvsearch.data;

import dev.household.tvsearch.domain.MediaEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NativeMediaSnapshot {
    public final List<MediaEntity> continueWatching;
    public final List<MediaEntity> previews;
    public final List<MediaEntity> apps;
    public final String notice;
    public final boolean needsPermission;

    public NativeMediaSnapshot(List<MediaEntity> resume, List<MediaEntity> previews,
                               List<MediaEntity> apps, String notice, boolean needsPermission) {
        continueWatching = immutable(resume);
        this.previews = immutable(previews);
        this.apps = immutable(apps);
        this.notice = notice;
        this.needsPermission = needsPermission;
    }

    public List<MediaEntity> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return Collections.emptyList();
        List<MediaEntity> results = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        List<MediaEntity> all = new ArrayList<>(continueWatching);
        all.addAll(previews);
        all.addAll(apps);
        for (MediaEntity entity : all) {
            String text = entity.title + " " + value(entity.subtitle) + " " + value(entity.provider);
            if (!text.toLowerCase(Locale.ROOT).contains(needle)) continue;
            List<String> key = entity.playbackIntent != null && entity.type != MediaEntity.Type.APP
                    ? Arrays.asList(entity.providerPackage, entity.playbackIntent, entity.title,
                    entity.season, entity.episode)
                    : Collections.singletonList(entity.id);
            if (seen.add(key)) results.add(entity);
        }
        return Collections.unmodifiableList(results);
    }

    private static String value(String value) { return value == null ? "" : value; }
    private static List<MediaEntity> immutable(List<MediaEntity> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
