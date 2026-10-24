package dev.household.tvsearch;

import dev.household.tvsearch.data.FakeCatalogService;
import dev.household.tvsearch.data.NativeMediaSnapshot;
import dev.household.tvsearch.data.TvProgramMapper;
import dev.household.tvsearch.domain.MediaEntity;
import dev.household.tvsearch.domain.MediaTitle;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class NativeMediaTest {
    private TvProgramMapper.Row row(long id, String pkg, String episode) {
        TvProgramMapper.Row row = new TvProgramMapper.Row();
        row.id = id;
        row.title = "Original Test Series";
        row.packageName = pkg;
        row.season = "1";
        row.episode = episode;
        row.episodeTitle = "The next chapter";
        row.intentUri = "original://resume/" + episode;
        row.watchNextType = 0;
        return row;
    }

    private MediaEntity entity(TvProgramMapper.Row row, MediaEntity.Source source) {
        return TvProgramMapper.map(row, source, "Test provider");
    }

    private NativeMediaSnapshot snapshot(List<MediaEntity> resume, List<MediaEntity> previews, List<MediaEntity> apps) {
        return new NativeMediaSnapshot(resume, previews, apps, "", false);
    }

    @Test public void movieAndTvCatalogModelsRemainDistinctAndRetainMetadata() throws Exception {
        List<MediaTitle> titles = new FakeCatalogService().search("Lantern");
        MediaEntity movie = MediaEntity.fromTitle(titles.get(0)), tv = MediaEntity.fromTitle(titles.get(1));
        assertEquals(MediaEntity.Type.MOVIE, movie.type);
        assertEquals(MediaEntity.Type.TV, tv.type);
        assertNotEquals(movie.id, tv.id);
        assertSame(titles.get(0), movie.catalogTitle);
        assertEquals(MediaEntity.Source.FIXTURE, movie.source);
        assertNull(movie.tmdbId);
    }

    @Test public void tmdbIdentityIsTypedButNeverTreatedAsAProviderPlaybackId() {
        MediaTitle title = new MediaTitle("tmdb", "42", MediaTitle.Type.TV, "Test", null, null,
                null, null, null, null, null, Collections.emptyList());
        MediaEntity entity = MediaEntity.fromTitle(title);
        assertEquals(Long.valueOf(42), entity.tmdbId);
        assertEquals("tmdb:tv:42", entity.id);
        assertNull(entity.playbackIntent);
        assertNull(entity.providerPackage);
    }

    @Test public void nativeEpisodePreservesOriginalIntentAndDisplayNumbers() {
        TvProgramMapper.Row row = row(7, "test.provider", "Special");
        row.positionMillis = 120_000L;
        row.durationMillis = 480_000L;
        MediaEntity entity = entity(row, MediaEntity.Source.WATCH_NEXT);
        assertEquals(MediaEntity.Type.EPISODE, entity.type);
        assertEquals("Special", entity.episode);
        assertEquals("S1 | ESpecial | The next chapter", entity.subtitle);
        assertEquals(row.intentUri, entity.playbackIntent);
        assertEquals("test.provider", entity.providerPackage);
        assertEquals(Integer.valueOf(25), entity.progressPercent());
    }

    @Test public void progressIsUnknownWithoutBothValidMeasurements() {
        TvProgramMapper.Row row = row(1, "test.provider", "1");
        assertNull(entity(row, MediaEntity.Source.WATCH_NEXT).progressPercent());
        row.positionMillis = 5L;
        row.durationMillis = 0L;
        assertNull(entity(row, MediaEntity.Source.WATCH_NEXT).progressPercent());
        row.positionMillis = -1L;
        row.durationMillis = 10L;
        assertNull(entity(row, MediaEntity.Source.WATCH_NEXT).progressPercent());
    }

    @Test public void knownZeroAndCompleteProgressAreNotArtificiallyClamped() {
        TvProgramMapper.Row row = row(1, "test.provider", "1");
        row.positionMillis = 0L;
        row.durationMillis = 100L;
        assertEquals(Integer.valueOf(0), entity(row, MediaEntity.Source.WATCH_NEXT).progressPercent());
        row.positionMillis = 100L;
        assertEquals(Integer.valueOf(100), entity(row, MediaEntity.Source.WATCH_NEXT).progressPercent());
        row.positionMillis = Long.MAX_VALUE;
        assertEquals(Integer.valueOf(100), entity(row, MediaEntity.Source.WATCH_NEXT).progressPercent());
    }

    @Test public void onlyContinueAndNextWatchNextTypesAreResumable() {
        TvProgramMapper.Row row = row(1, "test.provider", "1");
        for (int type = -1; type <= 4; type++) {
            row.watchNextType = type;
            assertEquals(type == 0 || type == 1, TvProgramMapper.isResume(row));
        }
        row.watchNextType = 0;
        row.browsable = false;
        assertFalse(TvProgramMapper.isResume(row));
    }

    @Test public void incompleteOptionalMetadataStillNormalizes() {
        TvProgramMapper.Row row = new TvProgramMapper.Row();
        row.id = 1;
        row.title = "Movie without artwork";
        row.packageName = "test.provider";
        row.programType = 0;
        MediaEntity entity = entity(row, MediaEntity.Source.PREVIEW);
        assertEquals(MediaEntity.Type.MOVIE, entity.type);
        assertNull(entity.artworkUri);
        assertNull(entity.playbackIntent);
        assertNull(entity.progressPercent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void ownerlessRowsCannotBecomePlaybackItems() {
        entity(row(1, null, "1"), MediaEntity.Source.WATCH_NEXT);
    }

    @Test public void previewsAreSearchableButNeverBecomeContinueWatching() {
        MediaEntity preview = entity(row(1, "test.provider", "1"), MediaEntity.Source.PREVIEW);
        NativeMediaSnapshot snapshot = snapshot(Collections.emptyList(), Collections.singletonList(preview),
                Collections.emptyList());
        assertTrue(snapshot.continueWatching.isEmpty());
        assertEquals(Collections.singletonList(preview), snapshot.search("test SERIES"));
    }

    @Test public void searchKeepsDifferentEpisodesAndDifferentProviders() {
        MediaEntity one = entity(row(1, "test.one", "1"), MediaEntity.Source.WATCH_NEXT);
        MediaEntity two = entity(row(2, "test.one", "2"), MediaEntity.Source.WATCH_NEXT);
        MediaEntity three = entity(row(1, "test.two", "1"), MediaEntity.Source.WATCH_NEXT);
        assertEquals(3, snapshot(Arrays.asList(one, two, three), Collections.emptyList(),
                Collections.emptyList()).search("Original").size());
        assertNotEquals(one.id, three.id);
    }

    @Test public void duplicateNativePlaybackPrefersWatchNextOverPreview() {
        MediaEntity resume = entity(row(1, "test.provider", "1"), MediaEntity.Source.WATCH_NEXT);
        MediaEntity preview = entity(row(2, "test.provider", "1"), MediaEntity.Source.PREVIEW);
        List<MediaEntity> found = snapshot(Collections.singletonList(resume), Collections.singletonList(preview),
                Collections.emptyList()).search("chapter");
        assertEquals(1, found.size());
        assertSame(resume, found.get(0));
    }

    @Test public void appSearchWorksEvenWhenTvListingsAreUnavailable() {
        MediaEntity app = new MediaEntity.Builder("app:test.app", MediaEntity.Type.APP, "Test cinema",
                MediaEntity.Source.INSTALLED_APP).provider("Test cinema", "test.app").build();
        NativeMediaSnapshot snapshot = new NativeMediaSnapshot(Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(app), "TV listings unavailable", true);
        assertEquals(Collections.singletonList(app), snapshot.search("CINEMA"));
        assertTrue(snapshot.needsPermission);
        assertEquals("TV listings unavailable", snapshot.notice);
        assertTrue(snapshot.search(" ").isEmpty());
        assertTrue(snapshot.search(null).isEmpty());
    }

    @Test public void controllerPreservesPartialResultsAndRecoverableNotice() {
        MediaEntity app = new MediaEntity.Builder("app:test", MediaEntity.Type.APP, "Test",
                MediaEntity.Source.INSTALLED_APP).build();
        SearchController controller = new SearchController(query -> new SearchController.Batch(
                Collections.singletonList(new SearchController.Result(app, Collections.emptyList())),
                "Native content unavailable; refresh"), Runnable::run, Runnable::run, state -> { });
        controller.search("Test");
        assertEquals(SearchController.Status.RESULTS, controller.state().status);
        assertNull(controller.state().error);
        assertTrue(controller.state().notice.contains("refresh"));
        assertSame(app, controller.state().results.get(0).entity);
        assertNull(controller.state().results.get(0).title);
    }
}
