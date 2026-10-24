package dev.household.tvsearch;

import android.content.Context;

import dev.household.tvsearch.domain.MediaEntity;

import java.util.ArrayList;
import java.util.List;

final class MediaPresentation {
    private MediaPresentation() { }

    static String metadata(Context context, MediaEntity entity) {
        if (entity.catalogTitle != null) return TitlePresentation.metadata(entity.catalogTitle,
                context.getString(R.string.movie), context.getString(R.string.tv), context.getString(R.string.minutes));
        List<String> parts = new ArrayList<>();
        parts.add(context.getString(entity.type == MediaEntity.Type.APP ? R.string.app
                : entity.type == MediaEntity.Type.EPISODE ? R.string.episode
                : entity.type == MediaEntity.Type.MOVIE ? R.string.movie : R.string.native_tv));
        if (entity.subtitle != null && !entity.subtitle.isEmpty()) parts.add(entity.subtitle);
        return String.join(" | ", parts);
    }

    static String origin(Context context, MediaEntity entity) {
        int label = entity.source == MediaEntity.Source.WATCH_NEXT ? R.string.continue_watching
                : entity.source == MediaEntity.Source.PREVIEW ? R.string.channel_content : R.string.installed_app;
        return context.getString(label) + (entity.provider == null ? "" : " | " + entity.provider);
    }
}
