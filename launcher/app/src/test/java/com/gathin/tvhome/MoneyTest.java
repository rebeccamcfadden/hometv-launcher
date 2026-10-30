package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Prices in minor units, their English labels, and the picture qualities the sources name. */
public class MoneyTest {

    @Test
    public void parsesDecimalAmountsIntoMinorUnits() {
        assertEquals(new Money(3900, "SEK"), Money.of("39.00", "SEK"));
        assertEquals(new Money(3900, "SEK"), Money.of("39", "sek"));
        assertEquals(new Money(5950, "SEK"), Money.of(59.5, "SEK"));
        assertEquals(new Money(399, "USD"), Money.of(3.99, "USD"));
        // Half up to the currency's digits; yen has none.
        assertEquals(new Money(3901, "SEK"), Money.of("39.005", "SEK"));
        assertEquals(new Money(500, "JPY"), Money.of("499.5", "JPY"));
    }

    @Test
    public void rejectsWhatIsNotAPrice() {
        assertNull(Money.of("", "SEK"));
        assertNull(Money.of("abc", "SEK"));
        assertNull(Money.of("-1", "SEK"));
        assertNull(Money.of("39", "KRONOR"));
        assertNull(Money.of("39", (String) null));
        assertNull(Money.of(Double.NaN, "SEK"));
    }

    @Test
    public void kronorReadAsSwedishStoresPrintThem() {
        assertEquals("49 kr", new Money(4900, "SEK").format());
        assertEquals("49.50 kr", new Money(4950, "SEK").format());
        assertEquals("0.05 kr", new Money(5, "SEK").format());
        assertEquals("1299 kr", new Money(129900, "SEK").format());
        assertEquals("from 39 kr", new Money(3900, "SEK").formatFrom());
        assertEquals("99 kr", new Money(9900, "NOK").format());
    }

    @Test
    public void otherCurrenciesKeepTheirSymbol() {
        assertEquals("$3.99", new Money(399, "USD").format());
        assertEquals("$4.00", new Money(400, "USD").format());
        assertEquals("€3.99", new Money(399, "EUR").format());
        assertEquals("£3.49", new Money(349, "GBP").format());
        assertEquals("12.50 CHF", new Money(1250, "CHF").format());
        assertEquals("500 JPY", new Money(500, "JPY").format());
    }

    @Test
    public void majorUnitsForTheOldOfferField() {
        assertEquals(39.0, new Money(3900, "SEK").major(), 0);
        assertEquals(3.99, new Money(399, "USD").major(), 1e-9);
    }

    @Test
    public void cheaperSortsFirst() {
        assertTrue(new Money(3900, "SEK").compareTo(new Money(4900, "SEK")) < 0);
        assertEquals(0, new Money(3900, "SEK").compareTo(new Money(3900, "SEK")));
    }

    @Test
    public void qualitiesFromEachSourcesWords() {
        assertEquals(Quality.SD, Quality.parse("sd"));
        assertEquals(Quality.HD, Quality.parse("HD"));
        assertEquals(Quality.HD, Quality.parse("qhd"));
        assertEquals(Quality.UHD, Quality.parse("4K"));
        assertEquals(Quality.UHD, Quality.parse("uhd"));
        assertNull(Quality.parse("8K"));
        assertNull(Quality.parse(null));
        assertEquals("4K", Quality.UHD.label);
    }
}
