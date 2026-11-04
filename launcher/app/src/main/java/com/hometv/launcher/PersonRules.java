package com.hometv.launcher;

import java.text.Collator;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The person page's rules (design_preview/src/screens/person.ts, Rebecca's pick B): the sorts, the titles by job,
 * the first paragraph of a biography, and the birth date in words. Pure, for the tests.
 */
final class PersonRules {
    private PersonRules() {}

    /** Five C cards across (Figma "Person page", node 266:704). */
    static final int COLUMNS = 5;

    enum Sort { KNOWN, NEWEST, AZ }

    /**
     * Best known keeps the catalogue's order (TMDB's vote count, most first); Newest first by year, an unknown year
     * last, then by name; A–Z by name in the TV's language.
     */
    static List<Title> sorted(List<Title> titles, Sort sort, Locale locale) {
        List<Title> out = new ArrayList<>(titles);
        Collator c = Collator.getInstance(locale);
        c.setStrength(Collator.PRIMARY);
        Comparator<Title> byName = (a, b) -> c.compare(a.name, b.name);
        if (sort == Sort.NEWEST) {
            Collections.sort(out, (a, b) -> {
                int ya = a.year <= 0 ? Integer.MIN_VALUE : a.year;
                int yb = b.year <= 0 ? Integer.MIN_VALUE : b.year;
                return ya != yb ? Integer.compare(yb, ya) : byName.compare(a, b);
            });
        } else if (sort == Sort.AZ) {
            Collections.sort(out, byName);
        }
        return out;
    }

    /**
     * Their titles under each department they worked in ("Acting", "Directing"), the department they are known for
     * first, then the one with most titles. A title they acted in and directed is under both.
     */
    static Map<String, List<Title>> byDepartment(List<Title> titles, Map<String, List<Catalog.Role>> roles,
                                                 String knownFor) {
        Map<String, List<Title>> found = new LinkedHashMap<>();
        for (Title t : titles) {
            List<Catalog.Role> r = roles.get(t.id);
            if (r == null) continue;
            for (Catalog.Role role : r) {
                if (role.department.isEmpty()) continue;
                List<Title> list = found.get(role.department);
                if (list == null) found.put(role.department, list = new ArrayList<>());
                if (!list.contains(t)) list.add(t);
            }
        }
        List<Map.Entry<String, List<Title>>> entries = new ArrayList<>(found.entrySet());
        // Stable: equal counts keep the order they were met in.
        Collections.sort(entries, (a, b) -> {
            boolean ak = a.getKey().equals(knownFor), bk = b.getKey().equals(knownFor);
            if (ak != bk) return ak ? -1 : 1;
            return Integer.compare(b.getValue().size(), a.getValue().size());
        });
        Map<String, List<Title>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Title>> e : entries) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** A catalogue credit's department, from the role the mock catalogue gives a crew member. */
    static String departmentOf(String job) {
        String j = job.toLowerCase(Locale.ROOT);
        if (j.contains("director")) return "Directing";
        if (j.contains("writer") || j.contains("screenplay") || j.contains("story")) return "Writing";
        if (j.contains("creator")) return "Creator";
        if (j.contains("producer")) return "Production";
        if (j.contains("composer") || j.contains("music")) return "Sound";
        return "Crew";
    }

    /** The biography's first paragraph: what the page shows before Read more. */
    static String firstParagraph(String bio) {
        String b = bio.trim();
        int cut = b.indexOf("\n\n");
        return cut < 0 ? b : b.substring(0, cut).trim();
    }

    /** Whether there is more than the first paragraph. */
    static boolean hasMoreParagraphs(String bio) {
        return !firstParagraph(bio).equals(bio.trim());
    }

    /** "27 December 1995" (in the TV's language) from TMDB's "1995-12-27"; "" when missing or not a date. */
    static String born(String isoDate, Locale locale) {
        if (isoDate == null || !isoDate.matches("\\d{4}-\\d{2}-\\d{2}")) return "";
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        in.setLenient(false);
        in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        try {
            Date d = in.parse(isoDate);
            DateFormat out = DateFormat.getDateInstance(DateFormat.LONG, locale);
            out.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return out.format(d);
        } catch (ParseException e) {
            return "";
        }
    }
}
