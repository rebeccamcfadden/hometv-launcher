package com.hometv.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PersonRulesTest {
    private static Title t(String id, String name, int year) {
        return new Title(id, Kind.MOVIE, name, year, Collections.singletonList("Drama"), 100, "PG", 7,
                "", "", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), 0, 0);
    }

    private static String ids(List<Title> ts) {
        StringBuilder b = new StringBuilder();
        for (Title x : ts) b.append(x.id).append(' ');
        return b.toString().trim();
    }

    private final Title dune = t("dune", "Dune", 2021);
    private final Title arrival = t("arrival", "Arrival", 2016);
    private final Title unknown = t("unknown", "Untitled", 0);
    private final Title enemy = t("enemy", "Enemy", 2013);

    @Test
    public void bestKnownKeepsTheCataloguesOrder() {
        List<Title> in = Arrays.asList(dune, unknown, arrival, enemy);
        assertEquals("dune unknown arrival enemy", ids(PersonRules.sorted(in, PersonRules.Sort.KNOWN, Locale.US)));
    }

    @Test
    public void newestFirstPutsAnUnknownYearLast() {
        List<Title> in = Arrays.asList(unknown, arrival, enemy, dune);
        assertEquals("dune arrival enemy unknown", ids(PersonRules.sorted(in, PersonRules.Sort.NEWEST, Locale.US)));
    }

    @Test
    public void azSortsByName() {
        List<Title> in = Arrays.asList(unknown, dune, enemy, arrival);
        assertEquals("arrival dune enemy unknown", ids(PersonRules.sorted(in, PersonRules.Sort.AZ, Locale.US)));
    }

    @Test
    public void titlesByDepartmentPutTheKnownForJobFirst() {
        Map<String, List<Catalog.Role>> roles = new LinkedHashMap<>();
        roles.put("dune", Arrays.asList(new Catalog.Role("Directing", "Director"),
                new Catalog.Role("Writing", "Screenplay")));
        roles.put("arrival", Collections.singletonList(new Catalog.Role("Directing", "Director")));
        roles.put("enemy", Collections.singletonList(new Catalog.Role("Acting", "Cameo")));
        roles.put("unknown", Collections.singletonList(new Catalog.Role("", "")));
        Map<String, List<Title>> by = PersonRules.byDepartment(Arrays.asList(dune, arrival, enemy, unknown), roles,
                "Acting");
        assertEquals(Arrays.asList("Acting", "Directing", "Writing"), Arrays.asList(by.keySet().toArray()));
        assertEquals("dune arrival", ids(by.get("Directing")));
        assertEquals("dune", ids(by.get("Writing")));
    }

    @Test
    public void departmentsFromTheCataloguesJobs() {
        assertEquals("Directing", PersonRules.departmentOf("Director"));
        assertEquals("Writing", PersonRules.departmentOf("Screenplay"));
        assertEquals("Creator", PersonRules.departmentOf("Creator"));
        assertEquals("Production", PersonRules.departmentOf("Executive Producer"));
        assertEquals("Crew", PersonRules.departmentOf("Gaffer"));
    }

    @Test
    public void theFirstParagraphAndWhetherThereIsMore() {
        assertEquals("One.", PersonRules.firstParagraph("  One.\n\nTwo.\n"));
        assertTrue(PersonRules.hasMoreParagraphs("One.\n\nTwo."));
        assertFalse(PersonRules.hasMoreParagraphs("One.\nStill one.\n"));
        assertEquals("", PersonRules.firstParagraph(""));
        assertFalse(PersonRules.hasMoreParagraphs(""));
    }

    @Test
    public void bornInWords() {
        assertEquals("December 27, 1995", PersonRules.born("1995-12-27", Locale.US));
        assertEquals("27 december 1995", PersonRules.born("1995-12-27", new Locale("sv", "SE")));
        assertEquals("", PersonRules.born("", Locale.US));
        assertEquals("", PersonRules.born("1995", Locale.US));
        assertEquals("", PersonRules.born("1995-13-40", Locale.US));
    }

    @Test
    public void theCatalogueAloneGivesRolesFromCastAndCrew() {
        Person denis = new Person("Denis", "Director");
        Title a = new Title("a", Kind.MOVIE, "A", 2020, Collections.singletonList("Drama"), 100, "PG", 7,
                "", "", Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(new Person("Denis", "Himself")),
                Collections.singletonList(new Person("Denis", "Director")), Collections.emptyList(), 0, 0);
        Catalog.Credits c = Catalog.Credits.fromCatalogue(denis, Collections.singletonList(a));
        assertEquals(2, c.roles.get("a").size());
        assertEquals("Acting", c.roles.get("a").get(0).department);
        assertEquals("Directing", c.roles.get("a").get(1).department);
        assertEquals("", c.biography);
    }
}
