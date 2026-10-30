package com.gathin.tvhome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/**
 * A price: an amount in the currency's minor units (öre for SEK) and its ISO 4217 code. Immutable and pure Java, so
 * the price sources, the merger and the labels are unit-tested on the JVM. The UI is English, so a krona price reads
 * "49 kr" (with öre, "49.50 kr"), and several prices read "from 39 kr".
 */
final class Money implements Comparable<Money> {
    final long minor;
    final String currency;

    Money(long minor, String currency) {
        this.minor = minor;
        this.currency = currency.toUpperCase(Locale.ROOT);
    }

    /**
     * From a decimal amount in major units ("39.00", "39", 39.0), rounded half up to the currency's minor digits;
     * null when the amount or the currency isn't usable (blank, not a number, negative, unknown code).
     */
    static Money of(String amount, String currency) {
        if (amount == null || currency == null) return null;
        int digits = digits(currency);
        if (digits < 0) return null;
        try {
            BigDecimal major = new BigDecimal(amount.trim());
            if (major.signum() < 0) return null;
            return new Money(major.movePointRight(digits).setScale(0, RoundingMode.HALF_UP).longValueExact(), currency);
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /** As {@link #of(String, String)}, from a JSON number; NaN and infinities give null. */
    static Money of(double amount, String currency) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) return null;
        return of(BigDecimal.valueOf(amount).toPlainString(), currency);
    }

    /** The currency's minor digits (2 for SEK, 0 for JPY), or -1 for a code Java doesn't know. */
    private static int digits(String currency) {
        try {
            int d = Currency.getInstance(currency.trim().toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
            return Math.max(d, 0);
        } catch (IllegalArgumentException e) {
            return -1;
        }
    }

    /** The amount in major units, for the old {@link Offer#price} field. */
    double major() {
        return BigDecimal.valueOf(minor).movePointLeft(Math.max(digits(currency), 0)).doubleValue();
    }

    /**
     * "49 kr", "49.50 kr", "$3.99", "€3.99", "£3.49"; another currency as "3.99 CHF". Whole kronor drop the öre, as
     * Swedish stores print them.
     */
    String format() {
        int digits = Math.max(digits(currency), 0);
        BigDecimal major = BigDecimal.valueOf(minor).movePointLeft(digits);
        switch (currency) {
            case "SEK":
            case "NOK":
            case "DKK":
                return (minor % pow10(digits) == 0 ? major.setScale(0, RoundingMode.UNNECESSARY).toPlainString()
                        : major.setScale(digits, RoundingMode.UNNECESSARY).toPlainString()) + " kr";
            case "USD":
                return "$" + major.setScale(digits, RoundingMode.UNNECESSARY).toPlainString();
            case "EUR":
                return "€" + major.setScale(digits, RoundingMode.UNNECESSARY).toPlainString();
            case "GBP":
                return "£" + major.setScale(digits, RoundingMode.UNNECESSARY).toPlainString();
            default:
                return major.setScale(digits, RoundingMode.UNNECESSARY).toPlainString() + " " + currency;
        }
    }

    /** "from 39 kr": the cheapest of several prices. */
    String formatFrom() {
        return "from " + format();
    }

    private static long pow10(int n) {
        long p = 1;
        for (int i = 0; i < n; i++) p *= 10;
        return p;
    }

    /** Cheaper first; only meaningful within one currency, so a different one sorts after. */
    @Override
    public int compareTo(Money o) {
        if (!currency.equals(o.currency)) return currency.compareTo(o.currency);
        return Long.compare(minor, o.minor);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money && ((Money) o).minor == minor && ((Money) o).currency.equals(currency);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(minor) * 31 + currency.hashCode();
    }

    @Override
    public String toString() {
        return minor + " " + currency;
    }
}
