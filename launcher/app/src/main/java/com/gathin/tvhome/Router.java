package com.gathin.tvhome;

/**
 * What a screen can ask of the launcher around it: the Shell and the Data, and opening the screens and apps other
 * screens lead to. MainActivity implements it and screens receive it.
 */
interface Router {
    Shell shell();

    Data data();

    /** The title preview popup (M5). */
    void openTitle(Title t);

    /** The title preview on its More like this tab (F3, the title menu). */
    void openMoreLike(Title t);

    /** The person page (M5), with what the catalogue knows they are in. */
    void openPerson(Person p);

    /** Pick for me, for movies or shows (M7). */
    void openPick(Kind kind);

    /** Opens the offer's service app; with no offer, the title's best one. Toasts when it isn't installed. */
    void watch(Title t, Offer o);

    /** The trailer in YouTube's TV app: TMDB's trailer when the title has one, else a search; a toast without it. */
    void openTrailer(Title t);

    void toast(CharSequence text);
}
