# Theme backdrops

The pictures behind the launcher's pages for the themes that use a photo or a drawing: Daylight, Cinema and
Landscapes (`classic/`), and Seasonal · Nature and Seasonal · Vector art (`seasonal/<style>/<period>.webp`, one per
period of `SeasonCalendar`). Who took each photo, and its licence, is in [CREDITS.md](CREDITS.md).

They are **not built into the APK**: together they are about 3 MB and the launcher must stay under 1 MB. The
launcher downloads the one a theme needs, once, from `theme_assets_url` in `launcher/tv.properties` (by default this
folder on the fork's `main`, through jsDelivr), keeps it in its own storage, and draws the theme's sky gradient until
it arrives or while the TV is offline. Only the 8-bit scenes are bundled (`app/src/main/res/drawable-nodpi/pixel_*.png`,
about 2 KB each); `seasonal/8bit/<period>.png` are copies of them for the web design preview, which keep the same
bytes.

A file's path here is its key in the theme (`url:classic/daylight.webp`), so a renamed file needs the theme changed
with it, and a changed picture needs a new name: a TV keeps the copy it downloaded.

## Making them again

The source is the Figma file "CleanTV · Theme explorations" (`lwShqRaciMpDUDW5dYljNw`): the Backdrop components on
"Components & backdrops", the Calendar · Nature components and the Calendar · Vector art component set.

- **Photos:** take the original image of the photo layer (not an export of the component, which carries a credit
  line), centre-crop it to 16:9 at its own width (Pixabay's is 1280; never scale up), and save it as WebP at quality
  80, lower if it is over 250 KB. Add or update its row in CREDITS.md from the layer name
  (`Photo · <what> · Pixabay <id> by <user>`).
- **Vector art:** export each variant as PNG at 1× (1920×1080) and save it as WebP at quality 90, which keeps the
  flat edges clean at about 40 KB.
- **8-bit:** the scenes are drawn at 160×90; copy each bundled `pixel_<period>.png` to `seasonal/8bit/<period>.png`
  (dashes for underscores) unchanged.

With Pillow:

```python
from PIL import Image
im = Image.open("photo.jpg").convert("RGB")
w, h = im.size
top = (h - w * 9 // 16) // 2
im.crop((0, top, w, top + w * 9 // 16)).save("classic/daylight.webp", "WEBP", quality=80, method=6)
Image.open("snow.png").convert("RGB").save("seasonal/vector/snow.webp", "WEBP", quality=90, method=6)
```
