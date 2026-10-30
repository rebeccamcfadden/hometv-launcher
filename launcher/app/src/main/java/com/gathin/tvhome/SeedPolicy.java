package com.gathin.tvhome;

/**
 * What a fresh household starts with and what happens to the TV's lists at a sign-in (BACKLOG.md, Household seed;
 * docs/rework.md, Accounts, 2026-10-30). Pure Java, unit-tested (SeedPolicyTest).
 *
 * <ul>
 *   <li>On start: lists saved on the TV are kept as they are; with none saved (a fresh install) My list, Seen,
 *       Favorites, recent searches and recently viewed all start empty: no seed. That part is
 *       {@link HouseholdState#defaults}.</li>
 *   <li>At a sign-in: the same account as last time carries on (changes made while signed out are still queued and go
 *       up). Any other sign-in, the first on this TV included, takes the account's lists as they are and adds nothing
 *       from the TV: the TV's titles, mock leftovers or the household's own, are replaced.</li>
 * </ul>
 */
final class SeedPolicy {
    private SeedPolicy() {}

    enum FirstSync {
        /** The ledger is this account's: an ordinary sync. */
        CONTINUE,
        /** Take the account's lists; the TV's are replaced and nothing of the TV's goes up. */
        ADOPT
    }

    /**
     * @param ledgerAccount the account the sync ledger belongs to ("" when this TV never synced)
     * @param account the account just signed in
     */
    static FirstSync onSignIn(String ledgerAccount, String account) {
        return ledgerAccount != null && !ledgerAccount.isEmpty() && ledgerAccount.equals(account)
                ? FirstSync.CONTINUE : FirstSync.ADOPT;
    }

    /** Gets the ledger ready for the account's sync and returns the decision: for ADOPT, an empty ledger. */
    static FirstSync prepare(SyncLedger ledger, String account) {
        FirstSync decision = onSignIn(ledger.account(), account);
        if (decision == FirstSync.ADOPT) ledger.reset(account);
        return decision;
    }
}
