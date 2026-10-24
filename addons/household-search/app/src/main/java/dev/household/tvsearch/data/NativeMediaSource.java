package dev.household.tvsearch.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.Log;

import dev.household.tvsearch.domain.MediaEntity;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read on a worker thread. No selection or privileged EPG access is needed. */
public final class NativeMediaSource {
    public static final String READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";
    public static final Uri WATCH_NEXT = Uri.parse("content://android.media.tv/watch_next_program");
    public static final Uri PREVIEW = Uri.parse("content://android.media.tv/preview_program");
    private static final int MAX_ROWS = 2048;
    private final Context context;

    public NativeMediaSource(Context context) { this.context = context.getApplicationContext(); }

    public NativeMediaSnapshot load() {
        List<String> notices = new ArrayList<>();
        List<MediaEntity> apps;
        try {
            apps = apps();
        } catch (SecurityException e) {
            Log.w("HouseholdSearch", "Installed app query was refused", e);
            notices.add("Installed apps could not be read. Check application visibility and refresh.");
            apps = new ArrayList<>();
        }
        boolean permission = context.checkSelfPermission(READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED;
        List<MediaEntity> resume = new ArrayList<>(), previews = new ArrayList<>();
        if (permission) {
            notices.add("TV listings permission is needed to read content published by other apps.");
        } else {
            read(WATCH_NEXT, MediaEntity.Source.WATCH_NEXT, resume, notices);
            read(PREVIEW, MediaEntity.Source.PREVIEW, previews, notices);
        }
        return new NativeMediaSnapshot(resume, previews, apps, String.join("\n", notices), permission);
    }

    private List<MediaEntity> apps() {
        PackageManager pm = context.getPackageManager();
        List<MediaEntity> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo match : pm.queryIntentActivities(query, 0)) {
                if (match.activityInfo == null || match.activityInfo.applicationInfo == null
                        || !match.activityInfo.exported || !match.activityInfo.enabled
                        || !match.activityInfo.applicationInfo.enabled) continue;
                String pkg = match.activityInfo.packageName;
                if (context.getPackageName().equals(pkg) || !seen.add(pkg)) continue;
                Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category)
                        .setClassName(pkg, match.activityInfo.name);
                apps.add(new MediaEntity.Builder("app:" + pkg, MediaEntity.Type.APP,
                        String.valueOf(match.loadLabel(pm)), MediaEntity.Source.INSTALLED_APP)
                        .provider(String.valueOf(match.loadLabel(pm)), pkg)
                        .playback(launch.toUri(Intent.URI_INTENT_SCHEME)).build());
            }
        }
        Collator collator = Collator.getInstance();
        apps.sort((left, right) -> collator.compare(left.title, right.title));
        return apps;
    }

    private void read(Uri uri, MediaEntity.Source source, List<MediaEntity> out, List<String> notices) {
        List<TvProgramMapper.Row> rows = new ArrayList<>();
        int skipped = 0;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null) {
                notices.add(source + " did not return a readable cursor. Try refreshing.");
                Log.w("HouseholdSearch", "Null TV provider cursor: " + source);
                return;
            }
            int scanned = 0;
            while (!Thread.currentThread().isInterrupted() && cursor.moveToNext()) {
                if (++scanned > MAX_ROWS) {
                    notices.add(source + " scan limited to " + MAX_ROWS + " entries.");
                    break;
                }
                TvProgramMapper.Row row = readRow(cursor);
                if (source == MediaEntity.Source.WATCH_NEXT && !TvProgramMapper.isResume(row)) continue;
                if (row.id <= 0 || row.title == null || row.title.trim().isEmpty()
                        || row.packageName == null || row.packageName.trim().isEmpty()) {
                    skipped++;
                    continue;
                }
                rows.add(row);
            }
        } catch (SecurityException | IllegalArgumentException | SQLiteException e) {
            Log.w("HouseholdSearch", "TV provider read failed: " + source, e);
            notices.add(source + " is unavailable or access was refused. Check TV listings permission and refresh.");
            return;
        }
        rows.sort(Comparator.comparingLong((TvProgramMapper.Row row) -> row.engagementMillis).reversed());
        Set<List<String>> seen = new HashSet<>();
        for (TvProgramMapper.Row row : rows) {
            MediaEntity entity = TvProgramMapper.map(row, source, providerLabel(row.packageName));
            List<String> key = entity.playbackIntent == null ? java.util.Collections.singletonList(entity.id)
                    : java.util.Arrays.asList(entity.providerPackage, entity.playbackIntent,
                    entity.title, entity.season, entity.episode);
            if (seen.add(key)) out.add(entity);
        }
        if (skipped > 0) {
            Log.w("HouseholdSearch", "Ignored incomplete TV programs: " + skipped);
            notices.add(source + ": " + skipped + " incomplete entries could not be displayed.");
        }
    }

    public static TvProgramMapper.Row readRow(Cursor cursor) {
        TvProgramMapper.Row row = new TvProgramMapper.Row();
        Long id = number(cursor, "_id");
        row.id = id == null ? 0 : id;
        row.title = text(cursor, "title");
        row.packageName = text(cursor, "package_name");
        row.episodeTitle = text(cursor, "episode_title");
        row.season = text(cursor, "season_display_number");
        row.episode = text(cursor, "episode_display_number");
        row.posterUri = text(cursor, "poster_art_uri");
        row.intentUri = text(cursor, "intent_uri");
        row.positionMillis = number(cursor, "last_playback_position_millis");
        row.durationMillis = number(cursor, "duration_millis");
        Long type = number(cursor, "type"), next = number(cursor, "watch_next_type");
        row.programType = type == null ? -1 : type.intValue();
        row.watchNextType = next == null ? -1 : next.intValue();
        Long browsable = number(cursor, "browsable");
        row.browsable = browsable == null || browsable != 0;
        Long engagement = number(cursor, "last_engagement_time_utc_millis");
        row.engagementMillis = engagement == null ? 0 : engagement;
        return row;
    }

    private String providerLabel(String pkg) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            Log.i("HouseholdSearch", "A published program's owning app is no longer visible");
            return pkg;
        }
    }

    private static String text(Cursor c, String name) {
        int index = c.getColumnIndex(name);
        return index < 0 || c.isNull(index) ? null : c.getString(index);
    }

    private static Long number(Cursor c, String name) {
        int index = c.getColumnIndex(name);
        return index < 0 || c.isNull(index) ? null : c.getLong(index);
    }
}
