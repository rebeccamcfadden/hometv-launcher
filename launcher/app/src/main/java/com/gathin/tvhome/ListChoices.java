package com.gathin.tvhome;

/**
 * Which of the account's TMDB lists are Household and Friends, as chosen on the TV (docs/rework.md, Accounts,
 * 2026-10-30): each list is Household, Friends or Not used, and each role holds one list, so choosing a list for a role
 * takes the role from the list that had it. config.xml's tmdb_household_list and tmdb_friends_list, when set, override
 * the choice ({@link #ref}). Pure Java, unit-tested (ListChoicesTest); {@link Accounts} keeps the two numbers.
 */
final class ListChoices {
    enum Role {
        HOUSEHOLD, FRIENDS, NOT_USED;

        /** What OK on a list's row turns it into: Household, Friends, Not used, and round again. */
        Role next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private long household;
    private long friends;

    ListChoices(long household, long friends) {
        this.household = Math.max(0, household);
        this.friends = Math.max(0, friends);
        if (this.friends == this.household) this.friends = 0;
    }

    long household() {
        return household;
    }

    long friends() {
        return friends;
    }

    Role role(long listId) {
        if (listId > 0 && listId == household) return Role.HOUSEHOLD;
        if (listId > 0 && listId == friends) return Role.FRIENDS;
        return Role.NOT_USED;
    }

    /** Gives the list its role, taking the role from the list that had it; NOT_USED just takes the list's away. */
    void choose(long listId, Role role) {
        if (listId <= 0) return;
        if (household == listId) household = 0;
        if (friends == listId) friends = 0;
        if (role == Role.HOUSEHOLD) household = listId;
        else if (role == Role.FRIENDS) friends = listId;
    }

    /** Household or Friends: the config.xml override when set, else the list chosen on the TV, else none. */
    ListRef ref(ListId list, ListRef override) {
        if (override != null && !override.isNone()) return override;
        long id = list == ListId.HOUSEHOLD ? household : list == ListId.FRIENDS ? friends : 0;
        return id > 0 ? ListRef.parse(Long.toString(id)) : ListRef.NONE;
    }
}
