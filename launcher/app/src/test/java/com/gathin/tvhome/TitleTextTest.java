package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

/** The cards' text lines against design_preview/src/ui/widgets.ts (titleMeta, provenance) and core/dom.ts. */
public class TitleTextTest {

    private final MockCatalog catalog = new MockCatalog();

    @Test
    public void everyTitleMatchesThePrototype() throws Exception {
        for (PrototypeTitles.Row row : PrototypeTitles.rows()) {
            Title t = catalog.get(row.id);
            assertEquals(row.id, row.meta, TitleText.meta(t));
            assertEquals(row.id, row.provenance, TitleText.provenance(t));
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
        assertEquals("My list · Sci-fi", TitleText.provenance(catalog.get("arrival")));
        assertEquals("Friends · Animation", TitleText.provenance(catalog.get("mitchells")));
        assertEquals("Catalogue · Drama", TitleText.provenance(catalog.get("carnivale")));
        Title noGenre = new Title("x", Kind.MOVIE, "X", 2020, Collections.emptyList(), 90, "PG", 7, "", "moon",
                null, Arrays.asList(ListId.HOUSEHOLD, ListId.MY), null, null, null, 0, 0);
        assertEquals("Household", TitleText.provenance(noGenre));
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
}
