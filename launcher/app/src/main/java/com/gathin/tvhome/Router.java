package com.gathin.tvhome;

/**
 * What a screen can ask of the launcher around it: the Shell and the Data, and opening the screens and apps other
 * screens lead to. MainActivity implements it and screens receive it. Until their milestones land, openTitle,
 * openPerson and openPick show a toast naming the milestone (docs/rework.md, Milestones).
 */
interface Router {
    Shell shell();

    Data data();

    /** The title preview popup (M5). */
    void openTitle(Title t);

    /** The person page stub (M5). */
    void openPerson(String name);

    /** Pick for me, for movies or shows (M7). */
    void openPick(Kind kind);

    /** Opens the offer's service app; with no offer, the title's best one. Toasts when it isn't installed. */
    void watch(Title t, Offer o);

    /** The trailer on YouTube's TV app, searched by name and year; a toast without the app. */
    void openTrailer(Title t);

    void toast(CharSequence text);
}
