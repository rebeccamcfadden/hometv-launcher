# Phone notes: send a watch list from a phone to the TV

When to use this: you keep the titles you want to watch in a note on your phone (Apple Notes or Google Keep) and want
them on the TV's lists without typing them with the remote. Each person's note goes to their own TMDB list, which shows
as a chip in Movies and TV Shows.

What happens: you share the note to a **Send to TV** Shortcut. It posts the note's text to the TV over your home Wi-Fi.
The TV looks each new line up on TMDB and adds the title to your list. If the account has no list with your name yet,
the TV makes a private one. A small pill at the bottom of the TV says "Importing Rebecca · 3 of 12", then how many were
added. A line that doesn't name a title exactly is asked about on the TV: **Launcher settings → Phone notes** lists
it under Last sends, and OK on it shows TMDB's three best answers to pick from.

- **Only adds.** Deleting a line on the phone takes nothing off the TV. A title you take off on the TV isn't put back
  by the next send.
- **Ticked means watched.** A ticked checklist item (or a struck-through line) goes to Seen and comes off the list.
  A note whose every line is ticked only marks them Seen: it doesn't make a list of its name.
- **At home only.** The phone has to be on the same Wi-Fi as the TV, and the TV has to be on. Away from home the
  Shortcut says it can't reach the TV; send again later.

## On the TV, once

1. Sign in to TMDB: **Launcher settings → Account**. The lists live on the account.
2. **Launcher settings → Phone notes → On.** The pane shows the TV's **address** (`http://192.168.1.50:8765/notes`,
   with your TV's own address) and a **pairing code** (`K7QM-4PXT`). Phone notes is off until it is turned on here
   (or by `note_inbox` in the TV's `config.xml` or `config.json`). "Not on Wi-Fi" in place of the address means the
   TV has no address on the home network yet; if another app on the TV holds port 8765, the pane says so.
   Beside them is a **QR code**: scan it with a phone's camera and a setup page opens on the phone: on an iPhone with
   **Get the shortcut** and **Pair this phone**, on an Android phone with the settings to copy into HTTP Shortcuts
   (below).
3. In your router, give the TV a fixed address (a "DHCP reservation"), so the address in the Shortcut keeps working.

**New code** makes a new pairing code. Each iPhone then scans the QR again and taps **Pair this phone**, and each
Android phone pastes the new `Authorization` value into its shortcut (below); nothing else in
the shortcut changes.

## On each iPhone, once

1. Scan the QR in the TV's Phone notes pane with the iPhone camera, and open the link.
2. Delete any older shortcut called **Send to TV** first (pairing runs it by name). Tap **Get the shortcut**, then
   **Add Shortcut**, and come back to the page. The button shows when the TV has the
   household's shortcut link (`note_shortcut_url` in `tv.properties`, below); without it, make the shortcut once (next section) or
   build a simple one by hand.
3. Tap **Pair this phone**. Shortcuts opens and asks whose list this phone's notes go to: type your list's name as it
   should show on the TV (`Rebecca`). It keeps the TV's address, the code and the name in a small file on the phone
   (iCloud Drive › Shortcuts › `Send to TV.txt`) and says "Paired with the TV".

After **New code** on the TV, or to send to another list, scan the QR and tap **Pair this phone** again. The first
time a note is sent, iOS asks whether Shortcuts may find devices on your local network: **Allow**. If you tapped
Don't Allow, turn it on in **Settings → Privacy & Security → Local Network → Shortcuts**.

## Making the shortcut, once

One person builds **Send to TV** on their iPhone and shares it; it holds no address or code, so its link can go to
anyone in the household. It does two things: started by **Pair this phone** it saves what the TV sent, and shared a
note it sends the note with what it saved.

```
If the input contains "cleanyourtv-pair"                 (Pair this phone)
    keep the TV's url and auth, ask for the list name, save them to Send to TV.txt
Otherwise                                                (a shared note)
    read Send to TV.txt, POST the note to the TV, show its answer
End If
```

**First, delete every shortcut called "Send to TV"** on the phone. Pair this phone runs the shortcut by its exact
name: with an old one still there, a new one is called "Send to TV 1", and pairing runs the old one.

New actions land at the bottom of the shortcut: press and hold one to drag it into the right part of an **If**.

**Create it.** In **Shortcuts**, tap **+**, then the name at the top → **Rename** → `Send to TV` (exactly). Tap **ⓘ**,
turn on **Show in Share Sheet**, **Done**. In the **Receive** block at the top: **Any** → **Clear**, tick **Text** and
**Rich Text**; set **If there's no input** to **Ask For → Text** (for trying it with ▶).

**Tell a pairing from a note.**

1. **Get Text from Input** (*Shortcut Input*).
2. **If**: *Text* · **contains** · `cleanyourtv-pair`. It comes with **Otherwise** and **End If**.

**Between If and Otherwise: the pairing.**

3. **Get Dictionary from Input** (*Shortcut Input*).
4. **Get File**: **Shortcuts** folder, path `Send to TV.txt` (newer iOS: **Get File from Folder**). Tap the **›** after
   the path and turn **Error If Not Found** off: the first time there is no file yet.
5. **Get Dictionary Value**: **Value** for `list` in *File*.
6. **Ask for Input**: **Text**, prompt `Whose list do notes from this phone go to?`, **Default Answer** the *Dictionary
   Value* from step 5 (empty the first time).
7. **Set Dictionary Value**: key `list`, value *Provided Input*; tap its dictionary and pick the *Dictionary* from
   step 3.
8. **Text**, holding the *Dictionary* from step 7 (this writes the settings as JSON text; Shortcuts saves text as a `.txt` file, so the name ends in `.txt`).
9. **Save File**: the *Text* from step 8; **Ask Where to Save** off, **Shortcuts** folder, subpath `Send to TV.txt`,
   **Overwrite If File Exists** on.
10. **Show Notification**: `Paired with the TV. Share a note to Send to TV.`

**Between Otherwise and End If: sending a note.**

11. **Get File**: **Shortcuts**, path `Send to TV.txt`, **Error If Not Found** off (behind the **›**).
12. **If** *File* **does not have any value**: inside it **Show Notification** `Scan the QR in the TV's Launcher
    settings → Phone notes, then tap Pair this phone.` and **Stop This Shortcut**. Leave its Otherwise empty.
13. After that inner **End If**: **Get Dictionary from Input** (*File* from step 11).
14. Three times, for `url`, `auth` and `list`: **Get Dictionary Value** (Value for that key; tap its dictionary and pick
    the one from step 13), then **Set Variable** with the same name.
15. **Get Text from Input** (*Shortcut Input*).
16. **Get Contents of URL**: tap the chip it fills in → **Clear Variable**, then set the URL to the variable *url*.
    Tap **›**: Method **POST**; **Headers**: `Authorization` = the variable *auth* (it already says "Bearer …");
    **Request Body: JSON** with Text fields `list` = the variable *list* and `text` = the *Text* from step 15.
17. **Get Dictionary Value**: Value for `message` in *Contents of URL*, then **Show Notification** with it. **Done**.

**Test it**: scan the TV's QR, tap **Pair this phone** (it asks for the list, then says "Paired with the TV"; the
first time, allow it to use the Shortcuts folder; **Files › iCloud Drive › Shortcuts › Send to TV.txt** now exists),
then share a note to **Send to TV**.

**Share it**: in the shortcut's **⋯** menu, **Share → Copy iCloud Link**. A link is a copy of the shortcut as it is
then: after changing the shortcut, copy a new one. Put it in `launcher/tv.properties` as
`note_shortcut_url=https://www.icloud.com/shortcuts/…` before building the launcher (it is per household, and that
file is never committed), or in a pushed `config.json` as `"note_shortcut_url": "…"`, which wins; `config.xml`'s
`note_shortcut_url` is the last fallback. The setup page then shows **Get the shortcut** on every phone. Only iCloud
shortcut links are accepted.

## A simple shortcut by hand

Without a shared shortcut, a phone can build a simpler one that keeps the address and code inside it (after **New
code**, paste the new value into it again). The setup page's **Build a simple shortcut by hand instead** has the address
and the `Authorization` value, each with a **Copy** button. The finished shortcut has four actions:

```
Receive Text from Share Sheet
Get Text from Input
Get Contents of URL      (POST, JSON, to the TV)
Get Dictionary Value     (message), then Show Notification
```

**Create it.** In the **Shortcuts** app, on the **Shortcuts** tab, tap **+**. Tap the name at the top
("New Shortcut") → **Rename** → `Send to TV`.

**Put it in the share sheet.**

1. Tap **ⓘ** at the bottom of the editor, turn on **Show in Share Sheet**, and tap **Done**.
2. A block appears at the top: **Receive *Any* input from *Share Sheet***. Tap **Any** → **Clear**, tick **Text**
   and **Rich Text**, then **Done**.
3. Optional, for testing with ▶ in the editor: tap **If there's no input: Continue** and choose **Ask For → Text**.

**Read the note as text.** In the action search bar at the bottom, type `get text` and add **Get Text from Input**.
It reads "Get text from *Shortcut Input*"; leave it.

**Send it to the TV.** Search `get contents`, add **Get Contents of URL**, then:

1. Shortcuts fills the URL with the yellow **Text** chip from the step before. Tap that chip → **Clear Variable**,
   then tap **URL** and enter the address from the TV's pane, e.g. `http://192.168.1.50:8765/notes`. It starts with
   `http://` (not https) and ends with `/notes`.
2. Tap **›** on the action to show its options and set:

   | Option | Value |
   |---|---|
   | Method | **POST** (tap GET to change it) |
   | Headers | **Add new header**: Key `Authorization`, Value `Bearer K7QM-4PXT`: the word `Bearer`, one space, then your TV's code |
   | Request Body | **JSON** |
   | Field 1 | **Add new field → Text**: Key `list`, Value your list's name as it should show on the TV, typed: `Rebecca` |
   | Field 2 | **Add new field → Text**: Key `text`. For the value, tap the empty box, then tap the blue **Text** variable (from Get Text from Input) in the bar above the keyboard |

   Field 2 must show a blue variable chip, not the typed word "Text"; otherwise the TV gets the word instead of the
   note.

**Show the TV's answer.**

1. Search `dictionary value` and add **Get Dictionary Value**: Get **Value** for Key `message` in
   *Contents of URL*.
2. Search `notification` and add **Show Notification**; tap its text and pick the **Dictionary Value** variable.
3. Tap **Done**.

The first time it runs, iOS asks whether Shortcuts may find devices on your local network: **Allow**. If you
tapped Don't Allow, turn it on in **Settings → Privacy & Security → Local Network → Shortcuts**.

A second phone uses the same shortcut with its own list name in `list`.

## On an Android phone

Android has no Shortcuts app, so a phone uses **HTTP Shortcuts** (free and open source, on Google Play and F-Droid). It
sends the note as plain text with the list's name in the address, which the TV accepts as it is. Not tried on a real
Android phone yet.

Scan the QR in the TV's Phone notes pane with the phone's camera and open the link: on Android the page shows these
settings first. Type whose list this phone's notes go to; the page fills in the **URL** (`http://192.168.1.50:8765/
notes?list=Rebecca`), the **Authorization** value (`Bearer` and the TV's code) and a one-line **script** that shows
the TV's answer, each with a **Copy** button. Then, in HTTP Shortcuts:

1. **⋮ → Global Variables → + → Static Variable**: name it `note`, tick **Allow Receiving Value from Share Dialog**,
   save.
2. On the main screen, **+ → Regular Shortcut**, named `Send to TV`.
3. **Basic Request Settings**: method **POST**, URL: paste it.
4. **Request Headers**: `Authorization` = paste the value.
5. **Request Body / Parameters**: body type **Custom Text**, content type `text/plain`; for the body, tap **{ }** and
   pick `note`.
6. **Response Handling**: On Success and On Failure show nothing (the script shows the answer instead).
7. **Scripting**: paste the script into **Run on Success** and **Run on Failure**:
   `var m = ''; try { m = JSON.parse(response.body).message; } catch (e) {} showToast(m || 'Could not reach the TV: is this phone on the home Wi-Fi?');`
8. **Trigger & Execution Settings** (Android 11 and later): tick the Direct Share option, so **Send to TV** shows in the
   share sheet. Save.

To send, share the note: in **Google Keep**, **⋮ → Send → Send via other apps → Send to TV** (or HTTP Shortcuts); in
**Samsung Notes**, **Share → Text → Send to TV**. The phone shows the TV's answer, e.g. "Sent: 12 lines, 3 new".
After **New code** on the TV, scan the QR again and paste the new `Authorization` value into the shortcut's header.

## Sending a note

- **Apple Notes:** open the note, tap **Share** (or **⋯ → Send a Copy**) and pick **Send to TV**.
- **Google Keep:** open the note, tap **⋮ → Send → Send via other apps** and pick **Send to TV**.

The phone shows what the TV said, e.g. "Sent: 12 lines, 3 new". Send again whenever you add titles; only the new
lines are looked up.

## Writing the note

One title a line. The TV ignores the note's own title line, blank lines and headings, and takes off checkboxes,
bullets and numbers. In a checklist (Apple Notes, Google Keep), a line without a box is a heading, and an empty box is
skipped.

| Write | Means |
|---|---|
| `Dune` | The movie or show called exactly that; the more popular one when both exist |
| `Dune (1984)` | From that year (or the next) |
| `Fargo (tv)`, `Fargo (series)`, `Andor S2`, `The Bear - season 3` | A show |
| `Fargo (movie)` | A movie |
| `Movies to watch` or `Shows:` as a heading | The lines under it are looked up as movies first, or shows first; the other kind if that finds nothing. The whole note still goes to one list |
| A themoviedb.org link | Exactly that title |
| ☑ / `[x]` / ✓ / ~~struck through~~ | Watched: goes to Seen |

Only an exact title goes straight onto the list (a leading "The" aside: "Birdcage" is The Birdcage). Anything else is
asked, never guessed: "Dune" never becomes "Dune: Part Two" on its own. Two spellings of one title add it once.

## Lines the TV asks about

**Launcher settings → Phone notes → Last sends** lists each line that wasn't matched exactly, as a button: "Inglorious
basterds · Choose". OK on it shows TMDB's three best answers as posters with their title and year, a slip of the
keyboard first ("Inglourious Basterds"):

- **OK on a poster** puts that title on the list, or on Seen if the line was ticked. The line leaves Last sends.
- **Search again** opens the line's text over the on-screen keyboard (as in Search; a keyboard or a remote with keys
  types too): fix the spelling or shorten it, then **Search**, and pick from what that finds. TMDB's search doesn't
  forgive a misspelled word: "Dead poet society" finds only a band, "dead poets" finds the film. Back returns to the
  answers.
- **None of these** stops asking about the line; the TV remembers that, so sending the note again doesn't bring it
  back.
- **Back** leaves the line there for later. Sending the note again keeps its answers.

A line with no answers saved (sent before this TV could ask) is looked up when you open it. If TMDB has nothing
close, change it in the note (add the year, or paste the title's
themoviedb.org link) and send again, since a changed line is looked up again, or choose **Stop asking**. Picking needs
the TV signed in to TMDB; signed out, the pill says so and the line stays.

## Safety

- The TV listens only while Phone notes is **On**, only for phones on the home network (private addresses), and only
  for `POST /notes` with the pairing code. After five wrong codes it stops listening for five minutes.
- The QR opens `GET /setup`, a fixed page with no code in it: the code rides in the QR's link after the `#`, which a
  phone keeps to itself and never sends to the TV.
- It is plain HTTP on your home network: someone on your Wi-Fi who sees the code could add titles to your lists, and
  nothing more. The code never leaves the TV, the paired phones and their Shortcuts; don't share screenshots of it.
- The shared shortcut holds no address or code: each phone keeps those in its own `Send to TV.txt` (iCloud Drive ›
  Shortcuts), so its iCloud link is safe to pass around the household.
- The TV keeps each line's text and what it matched, so it doesn't look the same line up twice, and the latest note
  of each list until its titles are on the list (a note sent while the TV is signed out or offline waits there), and
  nothing else from the note.

## When it fails

| The phone says | Do this |
|---|---|
| "Could not connect to the server" | The phone isn't on the home Wi-Fi, the TV is off or asleep, Phone notes is off, or the TV's address changed (check the pane, then the router reservation). Right after the launcher is updated, the TV doesn't listen until Home has been opened once: press Home on the remote. Also check the URL starts with `http://` and ends in `:8765/notes`, and that **Settings → Privacy & Security → Local Network → Shortcuts** is on. |
| "Wrong pairing code" | Scan the QR in the TV's Phone notes pane and tap **Pair this phone**. With a simple shortcut, or on Android, paste the setup page's `Authorization` value into its header again. |
| "Scan the QR in the TV's Launcher settings…" | This phone hasn't paired yet, or iCloud Drive is off (the shortcut keeps its settings there): turn it on, then pair. |
| Shortcuts says it can't find "Send to TV" | **Pair this phone** runs the shortcut by name: rename it back to exactly `Send to TV`. |
| "Too many wrong codes" | Wait five minutes. |
| "Say which list the note is for" | The Shortcut's `list` field is empty, or its key isn't spelled `list`. |
| "The note was empty" | The `text` field is the typed word, not the blue **Text** variable from Get Text from Input; or the share sheet sent nothing. |
| Nothing new appears on the TV | The lines were sent before (only new lines count), or the TV isn't signed in to TMDB yet: they wait until it is. |
| "The TV will add these once it's signed in to TMDB." | Sign in on the TV (**Launcher settings → Account**). The note waits on the TV and is added after the first sync. |
| "The TV found no titles in that note." | The note had only its title, headings or blank lines. |

On the TV, an amber pill that ends "waiting for the internet", "couldn't reach TMDB" or "waiting for TMDB sign-in"
means the note is kept and tried again after the next send or when the TV next reads its lists; nothing is lost.
