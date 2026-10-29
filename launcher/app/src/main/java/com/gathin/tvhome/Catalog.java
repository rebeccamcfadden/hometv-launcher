package com.gathin.tvhome;

import java.util.List;

/**
 * Where titles come from. V1 has only {@link MockCatalog}, the prototype's mock data (docs/rework.md, Catalogue); a
 * TMDB source can replace it behind this interface (BACKLOG.md, Real catalogue).
 */
interface Catalog {
    /** Every title, in the catalogue's order. */
    List<Title> all();

    /** The title with this id, or null. */
    Title get(String id);

    /** Every genre a title has, sorted and distinct. */
    List<String> genres();
}
