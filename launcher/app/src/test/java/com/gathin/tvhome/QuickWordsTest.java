package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;

/** The values on the quick-settings dropdown's household rows. */
public class QuickWordsTest {

    @Test
    public void accountFollowsTheSignIn() {
        assertEquals(QuickWords.Account.NOT_SIGNED_IN, QuickWords.account(SignInFlow.Phase.SIGNED_OUT, false));
        assertEquals(QuickWords.Account.GETTING_CODE, QuickWords.account(SignInFlow.Phase.REQUESTING, false));
        assertEquals(QuickWords.Account.WAITING, QuickWords.account(SignInFlow.Phase.WAITING, false));
        assertEquals(QuickWords.Account.SIGNING_IN, QuickWords.account(SignInFlow.Phase.FINISHING, false));
        assertEquals(QuickWords.Account.EXPIRED, QuickWords.account(SignInFlow.Phase.EXPIRED, false));
        assertEquals(QuickWords.Account.NOT_SIGNED_IN, QuickWords.account(SignInFlow.Phase.FAILED, false));
        assertEquals(QuickWords.Account.NOT_SIGNED_IN, QuickWords.account(null, false));
    }

    @Test
    public void aSavedAccountIsSignedInWhateverTheFlowSays() {
        assertEquals(QuickWords.Account.SIGNED_IN, QuickWords.account(SignInFlow.Phase.SIGNED_IN, true));
        assertEquals(QuickWords.Account.SIGNED_IN, QuickWords.account(SignInFlow.Phase.SIGNED_OUT, true));
    }

    @Test
    public void tickedCountsStreamingServicesNotStores() {
        ServiceTable table = ServiceTable.branded();
        assertEquals(0, QuickWords.servicesTicked(Collections.emptySet(), table));
        assertEquals(3, QuickWords.servicesTicked(EnumSet.of(ServiceId.NETFLIX, ServiceId.PRIME, ServiceId.YOUTUBE),
                table));
        // A subscribed_services override can name a store; it is not a streaming service.
        assertEquals(2, QuickWords.servicesTicked(Arrays.asList(ServiceId.NETFLIX, ServiceId.VIAPLAY,
                ServiceId.SFANYTIME, ServiceId.AMAZONVIDEO), table));
    }

    @Test
    public void weatherIsPlaceAndUnit() {
        assertEquals("New York \u00B7 \u00B0C", QuickWords.weather("New York", "", "Default", "\u00B0C"));
        assertEquals("Gothenburg \u00B7 \u00B0F", QuickWords.weather(" Gothenburg ", "", "Default", "\u00B0F"));
        // A chosen place wins over the build's city.
        assertEquals("Malm\u00F6 \u00B7 \u00B0C", QuickWords.weather("Malm\u00F6", "New York", "Default", "\u00B0C"));
    }

    @Test
    public void withoutAChosenPlaceTheBuildsCityShows() {
        assertEquals("New York \u00B7 \u00B0C", QuickWords.weather(null, "New York", "Default", "\u00B0C"));
        assertEquals("New York \u00B7 \u00B0F", QuickWords.weather("  ", " New York ", "Default", "\u00B0F"));
    }

    @Test
    public void withoutACityInTheBuildItSaysDefault() {
        assertEquals("Default \u00B7 \u00B0C", QuickWords.weather(null, "", "Default", "\u00B0C"));
        assertEquals("Default \u00B7 \u00B0C", QuickWords.weather("  ", null, "Default", "\u00B0C"));
        assertEquals("Default \u00B7 \u00B0C", QuickWords.weather(null, "   ", "Default", "\u00B0C"));
    }
}
