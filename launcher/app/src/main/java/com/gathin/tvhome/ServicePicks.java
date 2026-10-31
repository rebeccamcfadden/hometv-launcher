package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What the Streaming services and Stores for rent and buy panes of Launcher settings list, and their ticks
 * (design_preview/README.md, Streaming services and stores; src/data/services.ts, PICK_SERVICES and PICK_STORES):
 * Sweden's services in the prototype's order, with seven more behind "More services", and the stores whose prices
 * may show. Ticks are saved as lowercase ids ({@link ServiceId#id}), as {@code subscribed_services} names them. Pure
 * Java, unit-tested (ServicePicksTest); {@link ServiceChoices} keeps them on the TV.
 *
 * <p>Ticked services are the household's subscribed set ({@link Services#subscribed()}), so their SUB offers read
 * "Included"; a free service's FREE offers are included whether it is ticked or not (Offers.isIncludedOffer), so its
 * tick only counts it among the household's services. Unticked stores' rent and buy offers are left out ({@link
 * #keep}).
 */
final class ServicePicks {
    private ServicePicks() {}

    /** The grid's first eleven tiles. */
    static final List<ServiceId> MAIN = Collections.unmodifiableList(Arrays.asList(
            ServiceId.NETFLIX, ServiceId.PRIME, ServiceId.DISNEY, ServiceId.MAX, ServiceId.APPLETVPLUS,
            ServiceId.VIAPLAY, ServiceId.SKYSHOWTIME, ServiceId.SVTPLAY, ServiceId.TV4PLAY, ServiceId.TELE2PLAY,
            ServiceId.TELIAPLAY));

    /** Behind "More services". */
    static final List<ServiceId> MORE = Collections.unmodifiableList(Arrays.asList(
            ServiceId.HAYU, ServiceId.MUBI, ServiceId.CRUNCHYROLL, ServiceId.BRITBOX, ServiceId.PLUTOTV, ServiceId.PLEX,
            ServiceId.CINEASTERNA));

    /**
     * The stores that rent and sell in Sweden, in the prototype's order (Viaplay's rent and buy is the Viaplay Store).
     * Google TV is left out: Google Play doesn't sell films in Sweden. YouTube is free, and Tele2 Play and Telia Play
     * mostly stream, so their rent and buy offers always show.
     */
    static final List<ServiceId> STORES = Collections.unmodifiableList(Arrays.asList(
            ServiceId.APPLETV, ServiceId.AMAZONVIDEO, ServiceId.VIAPLAY, ServiceId.SFANYTIME, ServiceId.RAKUTEN));

    /** A tile's caption beside "Installed" or "No app". */
    enum Hint { NONE, FREE, FREE_TIER }

    static Hint hint(ServiceId id) {
        WatchProviders.Entry e = WatchProviders.SE.of(id);
        if (e == null || !e.free) return Hint.NONE;
        return e.subscription ? Hint.FREE_TIER : Hint.FREE;
    }

    /**
     * {@link #MORE}, then any other ticked service the grid doesn't name (a {@code subscribed_services} that lists
     * Hulu), so nothing ticked is out of sight. A store alone, and free YouTube, never get a tile.
     */
    static List<ServiceId> more(Set<ServiceId> ticked) {
        List<ServiceId> out = new ArrayList<>(MORE);
        for (ServiceId s : ServiceId.values()) {
            if (ticked.contains(s) && !MAIN.contains(s) && !MORE.contains(s) && tileable(s)) out.add(s);
        }
        return out;
    }

    private static boolean tileable(ServiceId s) {
        WatchProviders.Entry e = WatchProviders.SE.of(s);
        return e != null && (e.subscription || e.free) && !(e.free && e.store);
    }

    /** The second grid shows when opened, and stays while anything in it is ticked. */
    static boolean moreShown(boolean opened, Set<ServiceId> ticked) {
        if (opened) return true;
        for (ServiceId s : more(ticked)) if (ticked.contains(s)) return true;
        return false;
    }

    /** How many of {@code among} are ticked: "8 of 11 ticked", and the section's "8 ticked". */
    static int count(Set<ServiceId> ticked, Collection<ServiceId> among) {
        int n = 0;
        for (ServiceId s : among) if (ticked.contains(s)) n++;
        return n;
    }

    /** Every service the Streaming services pane lists: the grid, then the ones behind More. */
    static List<ServiceId> listed(Set<ServiceId> ticked) {
        List<ServiceId> out = new ArrayList<>(MAIN);
        out.addAll(more(ticked));
        return out;
    }

    /** OK on a tile or row: ticked becomes unticked and the other way round. */
    static Set<ServiceId> toggle(Set<ServiceId> ticked, ServiceId id) {
        Set<ServiceId> out = ticked.isEmpty() ? EnumSet.noneOf(ServiceId.class) : EnumSet.copyOf(ticked);
        if (!out.remove(id)) out.add(id);
        return Collections.unmodifiableSet(out);
    }

    /** "netflix,prime" in the enum's order; "" for none ticked, which differs from no choice at all. */
    static String encode(Collection<ServiceId> ticked) {
        StringBuilder b = new StringBuilder();
        for (ServiceId s : ServiceId.values()) {
            if (!ticked.contains(s)) continue;
            if (b.length() > 0) b.append(',');
            b.append(s.id);
        }
        return b.toString();
    }

    /** The saved ticks, unknown ids left out; null when nothing was saved (the TV's default applies). */
    static Set<ServiceId> decode(String saved) {
        if (saved == null) return null;
        Set<ServiceId> out = EnumSet.noneOf(ServiceId.class);
        for (String part : saved.split(",")) {
            ServiceId s = ServiceId.parse(part);
            if (s != null) out.add(s);
        }
        return Collections.unmodifiableSet(out);
    }

    /**
     * Whether an offer shows with these stores ticked: rent and buy offers of a listed store only when it is ticked,
     * everything else (subscriptions, free, free YouTube's rentals, a store the pane doesn't list) always, as the
     * prototype's offersOf does.
     */
    static boolean keeps(Offer o, Set<ServiceId> stores) {
        if (o.type != OfferType.RENT && o.type != OfferType.BUY) return true;
        return !STORES.contains(o.service) || stores.contains(o.service);
    }

    /**
     * The stores whose rent and buy offers count, for {@link PriceCards#ticked}: the ticked ones plus every store the
     * pane doesn't list, as {@link #keeps} does. Null (every store) stays null.
     */
    static Set<ServiceId> counted(Set<ServiceId> ticked) {
        if (ticked == null) return null;
        EnumSet<ServiceId> out = EnumSet.complementOf(EnumSet.copyOf(STORES));
        out.addAll(ticked);
        return Collections.unmodifiableSet(out);
    }
    /** The title's offers with only the ticked stores' prices; the same list when none is left out. */
    static List<Offer> keep(List<Offer> offers, Set<ServiceId> stores) {
        List<Offer> out = new ArrayList<>(offers.size());
        for (Offer o : offers) if (keeps(o, stores)) out.add(o);
        return out.size() == offers.size() ? offers : Collections.unmodifiableList(out);
    }
}
