package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The household store (design_preview/src/core/store.ts): defaults, toggles, recents and the JSON it is kept as. */
public class HouseholdStateTest {

    private final MockCatalog catalog = new MockCatalog();

    private HouseholdState fresh() {
        return HouseholdState.defaults(catalog);
    }

    @Test
    public void defaultsAreThePrototypeSeed() {
        HouseholdState s = fresh();
        assertEquals(Arrays.asList("arrival", "dune", "past-lives", "oppenheimer", "maestro", "american-fiction",
                "severance", "the-bear", "fallout", "wheel-of-time", "blue-planet"), s.myListIds());
        assertTrue(s.isSeen("knives-out"));
        assertTrue(s.isSeen("budapest"));
        assertFalse(s.isSeen("arrival"));
        assertTrue(s.isFavorite("arrival"));
        assertFalse(s.isFavorite("dune"));
        assertEquals(Arrays.asList("dune", "amy adams", "sci-fi", "carnival row"), s.recent());
        assertEquals(Arrays.asList("arrival", "dune", "past-lives", "coda", "moon", "severance"), s.viewed());
    }

    @Test
    public void myListSeedIsEveryTitleOnTheMyList() {
        HouseholdState s = fresh();
        for (Title t : catalog.all()) assertEquals(t.id, t.isOn(ListId.MY), s.inMyList(t.id));
    }

    @Test
    public void togglesReturnTheNewState() {
        HouseholdState s = fresh();
        assertFalse(s.toggleMyList("arrival"));
        assertFalse(s.inMyList("arrival"));
        assertTrue(s.toggleMyList("arrival"));
        assertTrue(s.inMyList("arrival"));
        // Added back at the end, as a JavaScript Set does.
        List<String> ids = s.myListIds();
        assertEquals("arrival", ids.get(ids.size() - 1));
        assertTrue(s.toggleSeen("moon"));
        assertTrue(s.isSeen("moon"));
        assertFalse(s.toggleSeen("moon"));
        assertFalse(s.toggleFavorite("arrival"));
        assertTrue(s.toggleFavorite("dune"));
        assertTrue(s.isFavorite("dune"));
    }

    @Test
    public void recentSearchesAreTrimmedLowerCasedAndCapped() {
        HouseholdState s = fresh();
        assertTrue(s.pushRecent("  Arrival "));
        assertEquals(Arrays.asList("arrival", "dune", "amy adams", "sci-fi", "carnival row"), s.recent());
        assertTrue(s.pushRecent("SCI-FI"));
        assertEquals(Arrays.asList("sci-fi", "arrival", "dune", "amy adams", "carnival row"), s.recent());
        assertTrue(s.pushRecent("moon"));
        assertEquals(5, s.recent().size());
        assertEquals("moon", s.recent().get(0));
        assertFalse(s.recent().contains("carnival row"));
        assertFalse(s.pushRecent("   "));
        assertFalse(s.pushRecent(null));
        assertEquals(5, s.recent().size());
    }

    @Test
    public void recentlyViewedIsCappedAtTen() {
        HouseholdState s = fresh();
        s.pushViewed("moon");
        assertEquals(Arrays.asList("moon", "arrival", "dune", "past-lives", "coda", "severance"), s.viewed());
        for (String id : Arrays.asList("a", "b", "c", "d", "e", "f")) s.pushViewed(id);
        assertEquals(10, s.viewed().size());
        assertEquals("f", s.viewed().get(0));
        assertEquals("past-lives", s.viewed().get(9));
        assertFalse(s.pushViewed(""));
        assertFalse(s.pushViewed(null));
    }

    @Test
    public void listsHandedOutCannotChangeTheState() {
        HouseholdState s = fresh();
        try {
            s.recent().clear();
        } catch (UnsupportedOperationException expected) {
            // fine
        }
        assertEquals(4, s.recent().size());
    }

    @Test
    public void jsonRoundTrips() {
        HouseholdState s = fresh();
        s.toggleMyList("dune");
        s.toggleSeen("dark");
        s.pushRecent("say \"hi\" \\ there\ttab");
        s.pushRecent("shōgun ’quote’");
        s.pushViewed("911");
        HouseholdState back = HouseholdState.fromJson(s.toJson(), catalog);
        assertEquals(s.myListIds(), back.myListIds());
        assertEquals(s.recent(), back.recent());
        assertEquals(s.viewed(), back.viewed());
        assertTrue(back.isSeen("dark"));
        assertFalse(back.inMyList("dune"));
        assertEquals(s.toJson(), back.toJson());
    }

    @Test
    public void anEmptyListStaysEmpty() {
        HouseholdState s = HouseholdState.fromJson("{\"myList\":[],\"favorites\":[]}", catalog);
        assertTrue(s.myListIds().isEmpty());
        assertFalse(s.isFavorite("arrival"));
        // Keys that are missing keep their defaults, as {...defaults(), ...saved} does.
        assertTrue(s.isSeen("knives-out"));
        assertEquals(4, s.recent().size());
    }

    @Test
    public void readsJsonWrittenElsewhere() {
        String json = " { \"seen\" : [ \"moon\" , \"dark\" ] , \"recent\": [\"a\\u00e9\", \"b\\n\"],"
                + " \"extra\": {\"n\": -1.5e3, \"ok\": true, \"none\": null}, \"viewed\": [] } ";
        HouseholdState s = HouseholdState.fromJson(json, catalog);
        assertTrue(s.isSeen("moon"));
        assertFalse(s.isSeen("knives-out"));
        assertEquals(Arrays.asList("aé", "b\n"), s.recent());
        assertTrue(s.viewed().isEmpty());
        assertEquals(fresh().myListIds(), s.myListIds());
    }

    @Test
    public void tooLongListsAreCutOnRead() {
        HouseholdState s = HouseholdState.fromJson(
                "{\"recent\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\"]}", catalog);
        assertEquals(Arrays.asList("1", "2", "3", "4", "5"), s.recent());
    }

    @Test
    public void anythingUnreadableGivesTheDefaults() {
        String seed = fresh().toJson();
        for (String bad : Arrays.asList(null, "", "   ", "not json", "{", "{\"myList\":[\"a\"", "[1,2]", "\"text\"",
                "{\"myList\":[\"a\"]} trailing", "{\"recent\":\"\\u12\"}", "{myList:[]}")) {
            assertEquals(String.valueOf(bad), seed, HouseholdState.fromJson(bad, catalog).toJson());
        }
    }

    @Test
    public void aKeyOfTheWrongTypeKeepsItsDefault() {
        HouseholdState s = HouseholdState.fromJson("{\"myList\":\"arrival\",\"seen\":[1,2],\"favorites\":[\"dune\"]}",
                catalog);
        assertEquals(fresh().myListIds(), s.myListIds());
        assertTrue(s.isSeen("budapest"));
        assertTrue(s.isFavorite("dune"));
        assertFalse(s.isFavorite("arrival"));
    }

    @Test
    public void writesCompactJson() {
        HouseholdState s = HouseholdState.fromJson("{\"myList\":[\"a\"],\"seen\":[],\"favorites\":[],\"recent\":[],"
                + "\"viewed\":[\"x\"]}", catalog);
        assertEquals("{\"myList\":[\"a\"],\"seen\":[],\"favorites\":[],\"recent\":[],\"viewed\":[\"x\"]}", s.toJson());
        assertEquals(Collections.singletonList("x"), s.viewed());
    }
}
