package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** The cards' text lines against design_preview/src/ui/widgets.ts (titleMeta, provenance) and core/dom.ts. */
public class TitleTextTest {

    private final MockCatalog catalog = new MockCatalog();
    /** The prototype's seed: My list holds the mock catalogue's "my" titles; Household and Friends are the mock lists. */
    private final ListMembership seed = HouseholdState.seed(catalog)::inMyList;

    @Test
    public void everyTitleMatchesThePrototype() throws Exception {
        for (PrototypeTitles.Row row : PrototypeTitles.rows()) {
            Title t = catalog.get(row.id);
            assertEquals(row.id, row.meta, TitleText.meta(t));
            assertEquals(row.id, row.provenance, TitleText.provenance(t, seed));
            assertEquals(row.id, row.kindYear, TitleText.kindYear(t));
        }
    }

    @Test
    public void meta() {
        assertEquals("1h 56m · PG-13 · 7.9/10", TitleText.meta(catalog.get("arrival")));
        assertEquals("1 season · TV-MA · 8.6/10", TitleText.meta(catalog.get("shogun")));
        assertEquals("2 seasons · TV-MA · 8.7/10", TitleText.meta(catalog.get("severance")));
        // toFixed(1) keeps the zero.
        assertEquals("1h 51m · PG-13 · 8.0/10", TitleText.meta(catalog.get("coda")));
    }

    @Test
    public void provenanceNamesTheFirstListOrTheCatalogue() {
        assertEquals("My list · Sci-fi", TitleText.provenance(catalog.get("arrival"), seed));
        assertEquals("Friends · Animation", TitleText.provenance(catalog.get("mitchells"), seed));
        assertEquals("Catalogue · Drama", TitleText.provenance(catalog.get("carnivale"), seed));
        Title noGenre = new Title("x", Kind.MOVIE, "X", 2020, Collections.emptyList(), 90, "PG", 7, "", "moon",
                null, Arrays.asList(ListId.HOUSEHOLD, ListId.MY), null, null, null, 0, 0);
        assertEquals("the mock My list flag doesn't count", "Household", TitleText.provenance(noGenre, seed));
        ListMembership mine = id -> id.equals("x");
        assertEquals("My list comes first in chip order", "My list", TitleText.provenance(noGenre, mine));
    }

    /** lists.ts provenance: the first list in chip order that holds the title, by its TMDB name, else the catalogue. */
    @Test
    public void provenanceFollowsTheShownListsOrder() {
        Household3 h = new Household3();
        Title moon = catalog.get("moon");
        assertEquals("Weekend movies · Sci-fi", TitleText.provenance(moon, h));
        h.mine = true;
        assertEquals("My list · Sci-fi", TitleText.provenance(moon, h));
        h.mine = false;
        h.lists = Arrays.asList(h.tips, h.weekend);
        assertEquals("Friends’ tips · Sci-fi", TitleText.provenance(moon, h));
        h.lists = Collections.singletonList(h.club);
        assertEquals("Catalogue · Sci-fi", TitleText.provenance(moon, h));
    }

    /** Three shown TMDB lists; moon is on Weekend movies and Friends' tips, and on My list when {@link #mine}. */
    private static final class Household3 implements ListMembership {
        final ShownList weekend = ShownList.tmdb(2, "", "Weekend movies");
        final ShownList tips = ShownList.tmdb(3, "", "Friends’ tips");
        final ShownList club = ShownList.tmdb(4, "", "Film club");
        List<ShownList> lists = Arrays.asList(weekend, tips, club);
        boolean mine;

        @Override
        public boolean inMyList(String id) {
            return mine;
        }

        @Override
        public List<ShownList> shownLists() {
            return lists;
        }

        @Override
        public boolean isOn(Title t, ShownList list) {
            if (list.isMy()) return mine;
            return list == weekend || list == tips;
        }
    }

    @Test
    public void kindAndYear() {
        assertEquals("Movie · 2016", TitleText.kindYear(catalog.get("arrival")));
        assertEquals("TV show · 2022", TitleText.kindYear(catalog.get("severance")));
    }

    @Test
    public void initials() {
        assertEquals("AA", TitleText.initials("Amy Adams"));
        assertEquals("Z", TitleText.initials("Zendaya"));
        assertEquals("BB", TitleText.initials("Baran bo Odar"));
        assertEquals("TC", TitleText.initials("Timothée Chalamet"));
        assertEquals("SH", TitleText.initials("  sandra   hüller "));
        assertEquals("K", TitleText.initials("K"));
        assertEquals("", TitleText.initials("   "));
        assertEquals("", TitleText.initials(null));
    }

    @Test
    public void seasonsAndScores() {
        assertEquals("1 season", TitleText.seasons(1));
        assertEquals("6 seasons", TitleText.seasons(6));
        assertEquals("9.3", TitleText.score(9.3));
        assertEquals("7.0", TitleText.score(7));
    }

    @Test
    public void listNames() {
        assertEquals("My list", ListNames.of(ListId.MY));
        assertEquals("Household", ListNames.of(ListId.HOUSEHOLD));
        assertEquals("Friends", ListNames.of(ListId.FRIENDS));
    }

    @Test
    public void whatIsNotKnownYetIsLeftOut() {
        // A TMDB summary: no runtime, seasons or rating yet.
        Title.Draft d = new Title.Draft("movie:1", Kind.MOVIE, "Summary");
        d.score = 7.6;
        d.detailed = false;
        assertEquals("7.6/10", TitleText.meta(d.build()));
        d.score = 0;
        assertEquals("", TitleText.meta(d.build()));
        assertEquals("Movie", TitleText.kindYear(d.build()));
        d.year = 2016;
        d.runtime = 116;
        d.rating = "PG-13";
        d.detailed = true;
        assertEquals("1h 56m · PG-13 · 0.0/10", TitleText.meta(d.build()));
        // Details without a US rating.
        d.rating = "";
        d.score = 7.6;
        assertEquals("1h 56m · 7.6/10", TitleText.meta(d.build()));
        Title.Draft show = new Title.Draft("tv:1", Kind.SHOW, "Show");
        show.seasons = Collections.singletonList(new Season(1, 8, Collections.emptyList()));
        show.rating = "TV-MA";
        show.score = 8.4;
        assertEquals("1 season · TV-MA · 8.4/10", TitleText.meta(show.build()));
    }
}
