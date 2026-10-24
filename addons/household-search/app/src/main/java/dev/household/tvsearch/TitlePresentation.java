package dev.household.tvsearch;

import dev.household.tvsearch.domain.MediaTitle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Presentation values are total even when optional catalog metadata is absent. */
final class TitlePresentation {
    private TitlePresentation() { }

    static String metadata(MediaTitle title, String movieLabel, String tvLabel, String minutesLabel) {
        List<String> parts = new ArrayList<>();
        parts.add(title.type == MediaTitle.Type.MOVIE ? movieLabel : tvLabel);
        if (title.year != null) parts.add(String.valueOf(title.year));
        if (title.rating != null) parts.add(String.format(Locale.getDefault(), "%.1f", title.rating)
                + (title.ratingSource == null ? "" : " " + title.ratingSource));
        if (title.type == MediaTitle.Type.MOVIE && title.runtimeMinutes != null)
            parts.add(title.runtimeMinutes + " " + minutesLabel);
        return String.join(" | ", parts);
    }
}
