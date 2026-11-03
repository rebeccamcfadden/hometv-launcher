# Phone remote: a phone as the TV's remote and keyboard

A phone on the home Wi-Fi drives HomeTV Launcher: arrows, OK, Back, Home, and typing into Search with the phone's own
keyboard. It also adds the title on the TV, or any film or show searched for on the phone, to My list. Nothing to
install on the phone: the TV serves the page itself. Off until you turn it on.

It works on the launcher's own screens (Home, Movies, TV Shows, Search, a title's preview, Launcher settings). In
other apps (Netflix, YouTube) only **Home** works from the phone; for those, the Google TV app is a full remote (see
[faq.md](faq.md)).

## On the TV, once

1. Launcher settings › **Phone remote** › **On**. (It is also a row in Launcher settings › Features.)
2. The pane shows a QR code, three steps, and the address and code to type instead.

Keep the TV's address the same: reserve it in your router (a "DHCP reservation"). A new address means each phone sees
the certificate warning once more.

## On each phone, once

1. Scan the QR with the phone's camera and open the link.
2. The browser warns that **the connection isn't private**. That is expected: the TV makes its own certificate, and no
   public authority can vouch for an address on your home network.
   - **Android (Chrome):** tap **Advanced**, then **Proceed to 192.168.1.50 (unsafe)**.
   - **iPhone (Safari):** tap **Show Details**, then **visit this website**, then **Visit Website**.
3. The page has the code from the QR already. Name the phone ("Rebecca's phone") and tap **Pair**.

The TV says "Rebecca's phone is paired as a remote". Add the page to the phone's home screen to open it in one tap.
No camera? Type the address the pane shows (`https://<tv>:8767/remote`) and then the code.

## Using it

- **The remote:** a ring of arrows round OK, then Back and Home. Arrows repeat while held. A line at the top says
  what the TV shows and whether it takes typing ("On the TV: Search · buttons and typing").
- **Typing:** where the TV has a text box (Search), **Type on the TV** opens the phone's keyboard and a field holding
  what the TV's box holds; each letter appears on the TV as you type. The cross empties both. Enter shows Search's full
  results and brings the remote back; so does closing the phone's keyboard, or the TV leaving its text box. A pause
  never closes it. Elsewhere the bar is dimmed and says why.
- **My list:** while the TV shows a title (its preview, or a card with focus) a card names it, with **My list**. Tap it
  to add the title, again to take it off; the TV says "Added Arrival to My list · From Rebecca's phone". **Something
  else? Search to add** searches the TV's catalogue from the phone: **+** adds, **✓** is already on it. Signed in to
  TMDB, My list follows the account's watchlist, as on the TV.
- **Home** works even when another app is in front.

While it is on, a phone icon leads the top bar's device controls on the TV, with a small dot; the dot turns green and
"A phone is connected" shows under it while a phone has the page open. OK on the icon opens the pane.

## Forgetting phones

In the pane, each paired phone has **Forget**: it stops working at once. A phone can also unpair itself (⋯ › Unpair
this phone). **New code** makes a new pairing code; phones already paired keep working. **Forget all phones** forgets
every phone and also makes a new code, key and certificate, so every phone sees the warning again when it pairs.

## Safety

- **Off by default.** Off, the TV doesn't listen at all; it never starts at boot unless it was on.
- **HTTPS only**, so what you type (a password in a search box, one day a login) isn't readable by others on the
  Wi-Fi. The TV's private key is made on the TV, kept in Android's Keystore and never leaves it.
- **Each phone has its own token**, given when it pairs with the code the TV shows only on its own screen. The TV
  keeps only a hash of each token, the phone's name and when it was last used. Nothing a phone sends is logged.
- **Home network only:** private, loopback and link-local addresses; the page must be asked for at the TV's own
  address, and a web page elsewhere can't call it.
- **Wrong codes:** five in a row from one phone pause pairing there for five minutes (and many from several at once
  pause it for everyone). Phones already paired keep working, and nothing else on the Wi-Fi can lock you out.
- **No analytics**, and the page loads nothing from the internet.

## When it fails

| What you see | What to do |
|---|---|
| The phone says "Can't reach the TV" | The TV is on, the phone is on the same Wi-Fi (not mobile data or a guest network), and Phone remote is on. Open the page again from the QR. |
| The pane says other apps are using its ports | It tries 8767 to 8770 and keeps the one it got. Turn Phone remote off and on again; if it stays, restart the TV. |
| The pane says the TV couldn't make its certificate | Turn it off and on again; if it stays, restart the TV. |
| The warning comes back on a phone | The TV's address changed, or Forget all was used. Accept it again; reserve the address in your router. |
| "Pairing is paused" | Five wrong codes: wait five minutes, then use the code the TV shows now. |
| Typing does nothing | The TV isn't showing a text box: open Search on the TV. |
| Arrows do nothing | Another app is in front: only Home works there. |
