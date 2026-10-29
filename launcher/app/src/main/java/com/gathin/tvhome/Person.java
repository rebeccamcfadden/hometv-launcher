package com.gathin.tvhome;

/** Someone in a title's cast or crew, with their role or job (design_preview/src/types.ts, Person). Immutable. */
final class Person {
    final String name;
    final String role;

    Person(String name, String role) {
        this.name = name;
        this.role = role;
    }

    @Override
    public String toString() {
        return name + " (" + role + ")";
    }
}
