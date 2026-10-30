package com.gathin.tvhome;

/**
 * Someone in a title's cast or crew, with their role or job (design_preview/src/types.ts, Person). Immutable.
 * {@link #id} is their TMDB id, 0 in the mock catalogue; {@link #photo} is their TMDB profile path, or null.
 */
final class Person {
    final String name;
    final String role;
    final int id;
    final String photo;

    Person(String name, String role) {
        this(name, role, 0, null);
    }

    Person(String name, String role, int id, String photo) {
        this.name = name;
        this.role = role == null ? "" : role;
        this.id = id;
        this.photo = photo;
    }

    @Override
    public String toString() {
        return name + " (" + role + ")";
    }
}
