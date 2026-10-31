package com.gathin.tvhome;

/**
 * Which loading, offline or error state a screen shows (V2 design 06-states A; design_preview/README.md, Loading,
 * offline and errors), worked out from the network and what the catalogue has saved rather than from the
 * prototype's State menu. Pure Java, unit-tested (CatalogStateTest).
 *
 * <p>"Online" is Android having reached the internet (a validated network, NavModel.internet). A title is "detailed"
 * once its details are in, from the disk cache or TMDB; "pending" while that load is still under way for the first
 * time (or again, after Retry or the network coming back); a title that is neither failed to load.
 */
final class CatalogState {
    private CatalogState() {}

    /** Movies and TV Shows, and Search's saved titles offline. */
    enum Lists {
        /** Everything as usual: the network is there, or the catalogue doesn't use it (the mock one). */
        ONLINE,
        /** Eight skeleton cards while the lists' details come in. */
        LOADING,
        /** Offline, from the saved copy: the amber pill with when it was saved. */
        OFFLINE_SAVED,
        /** Offline with no title's details saved yet: the crossed-out Wi-Fi circle, Wi-Fi settings and Try again. */
        OFFLINE_EMPTY
    }

    /**
     * The state of a page listing {@code titles} titles, of which {@code detailed} have their details and
     * {@code pending} are still loading. The skeletons show the first time the lists load ({@code settledBefore}
     * false), and again only when nothing is in at all (back online after "nothing saved"); a title joining a list
     * later fills in where it is, so the grid doesn't blink. Offline, the page says so once the loads have settled:
     * reading the disk cache takes a moment at a cold start, and is a load too.
     */
    static Lists lists(boolean remote, boolean online, int titles, int detailed, int pending, boolean settledBefore) {
        if (!remote) return Lists.ONLINE;
        if (pending > 0 && (detailed == 0 || !settledBefore)) return Lists.LOADING;
        if (online) return Lists.ONLINE;
        return titles > 0 && detailed == 0 ? Lists.OFFLINE_EMPTY : Lists.OFFLINE_SAVED;
    }

    /** A tab of the title preview. */
    enum Tab {
        /** Its content, as usual. */
        READY,
        /** Its skeleton: the title's details are on their way. */
        LOADING,
        /** The amber box with Retry: the details failed and the tab has nothing to show without them. */
        ERROR
    }

    /**
     * The state of a preview tab for a title. {@code needsDetails} is true for the tabs drawn from the title's details
     * (Overview, Where to watch, Cast &amp; crew, Episodes), which show the error box when they failed; More like this
     * has its own request and shows its own row.
     */
    static Tab tab(boolean remote, boolean detailed, boolean pending, boolean failed, boolean needsDetails) {
        if (!remote || detailed) return Tab.READY;
        if (pending) return Tab.LOADING;
        return failed && needsDetails ? Tab.ERROR : Tab.READY;
    }

    /** The preview's header (details row, the actions after Watch, the art) shows skeletons while it loads. */
    static boolean headerLoading(boolean remote, boolean detailed, boolean pending) {
        return remote && !detailed && pending;
    }

    /**
     * Whether the preview's Watch button is blank (its play icon, no label): the title's availability isn't known,
     * because its details are still loading or failed and the card that opened it knew no offers. "Not on your
     * services" is said only once the availability is in and there really are none.
     */
    static boolean watchBlank(boolean remote, boolean detailed, boolean hasOffers) {
        return remote && !detailed && !hasOffers;
    }
}
