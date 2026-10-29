package com.gathin.tvhome;

/** The watchlists' names (design_preview/src/data/catalog.ts, LIST_NAMES). English only, as the rest of V1. */
final class ListNames {
    private ListNames() {}

    static String of(ListId list) {
        switch (list) {
            case MY:
                return "My list";
            case HOUSEHOLD:
                return "Household";
            default:
                return "Friends";
        }
    }
}
