package com.gathin.tvhome;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The mock artwork bundled with the launcher: a title's art base name ("past-lives") to its drawable
 * (res/drawable-nodpi/art_past_lives.jpg, from design_preview/public/img, at most 960 px wide). A plain map rather
 * than getIdentifier, so resource shrinking keeps exactly these and a unit test can check every title's art is here.
 */
final class ArtNames {
    private ArtNames() {}

    private static final Map<String, Integer> IDS = new HashMap<>();

    static {
        IDS.put("arrival", R.drawable.art_arrival);
        IDS.put("budapest", R.drawable.art_budapest);
        IDS.put("coda", R.drawable.art_coda);
        IDS.put("dark", R.drawable.art_dark);
        IDS.put("dune", R.drawable.art_dune);
        IDS.put("fallout", R.drawable.art_fallout);
        IDS.put("knives-out", R.drawable.art_knives_out);
        IDS.put("last-of-us", R.drawable.art_last_of_us);
        IDS.put("microcosmos", R.drawable.art_microcosmos);
        IDS.put("mitchells", R.drawable.art_mitchells);
        IDS.put("moon", R.drawable.art_moon);
        IDS.put("our-planet", R.drawable.art_our_planet);
        IDS.put("past-lives", R.drawable.art_past_lives);
        IDS.put("peripheral", R.drawable.art_peripheral);
        IDS.put("severance", R.drawable.art_severance);
        IDS.put("shogun", R.drawable.art_shogun);
        IDS.put("the-bear", R.drawable.art_the_bear);
    }

    /** The drawable for an art name, or 0 when there is none. */
    static int drawable(String art) {
        Integer id = art == null ? null : IDS.get(art);
        return id == null ? 0 : id;
    }

    /** "past-lives" gives "art_past_lives": the drawable's file name without its extension. */
    static String resourceName(String art) {
        return "art_" + art.replace('-', '_');
    }

    static Set<String> names() {
        return Collections.unmodifiableSet(IDS.keySet());
    }
}
