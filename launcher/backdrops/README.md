# Theme backdrops

The pictures behind the launcher's pages for the themes that use a photo or a drawing: Daylight, Cinema and
Landscapes (`classic/`), and Seasonal · Vector art (`seasonal/vector-5/<period>-<phase>.webp`: each period of
`SeasonCalendar` at dawn, day, sunset and night, for the daylight cycle). `seasonal/nature/` and `seasonal/vector-2/`
are kept for TVs on an older build: Nature was removed and `vector-5` replaced `vector-2` on 2026-11-01. Who took each photo, and its licence, is in [CREDITS.md](CREDITS.md).

They are **not built into the APK**: together they are about 3 MB and the launcher must stay under 1 MB. The
launcher downloads the one a theme needs, once, from `theme_assets_url` in `launcher/tv.properties` (by default this
folder on the fork's `main`, through jsDelivr), keeps it in its own storage, and draws the theme's sky gradient until
it arrives or while the TV is offline. Only the 8-bit scenes are bundled (`app/src/main/res/drawable-nodpi/pixel_<period>[_<phase>].png`,
about 2 KB each, no suffix for day); `seasonal/8bit/<period>[-<phase>].png` are copies of them for the web design
preview, which keep the same bytes.

A file's path here is its key in the theme (`url:classic/daylight.webp`), so a renamed file needs the theme changed
with it, and a changed picture needs a new name: a TV keeps the copy it downloaded. A Seasonal file is named after its
period and part of the day (the theme's key says `<period>-{phase}`; the launcher fills in the phase and fetches the
next period's by swapping the name), so a changed Seasonal set gets a new folder instead, as `vector-5` replaced
`vector-2`.

## Making them again

The source is the Figma file "CleanTV · Theme explorations" (`lwShqRaciMpDUDW5dYljNw`): the Backdrop components on
"Components & backdrops", and the component sets "Calendar · Vector art · Time of day" and "Calendar · 8-bit · Time of
day" on the page "Calendar · Time of day" (Season × Time; Day is the period's scene from "Calendar · Vector art").

- **Photos:** take the original image of the photo layer (not an export of the component, which carries a credit
  line), centre-crop it to 16:9 at its own width (Pixabay's is 1280; never scale up), and save it as WebP at quality
  80, lower if it is over 250 KB. Add or update its row in CREDITS.md from the layer name
  (`Photo · <what> · Pixabay <id> by <user>`).
- **Vector art:** export each variant as PNG at 1× (1920×1080) to `<period>-<phase>.png`, dither it to the RGB_565
  grid with a 4×4 ordered (Bayer) pattern and save it as lossless WebP at Pillow's quality 100 (its effort setting
  for lossless; the default, 80, makes files twice the size), about 65–130 KB. The 40 pictures follow one brightness
  curve (mean L*: day 71, dawn 62, sunset 57, night 28 in spring and summer; Snow, Spring rain and Christmas 4–7
  lower, Autumn 8, Halloween 20): a changed picture is levelled to it in Figma before export. The launcher decodes backdrops as RGB_565 to save
  memory, which turns a smooth sky into visible bands; the dither breaks them up. A lossy WebP smooths the dither away.
- **8-bit:** the scenes are drawn at 160×90, at each part of the day; copy each bundled `pixel_<period>[_<phase>].png`
  to `seasonal/8bit/<period>[-<phase>].png` (dashes for underscores) unchanged.

With Pillow:

```python
import numpy as np
from PIL import Image
im = Image.open("photo.jpg").convert("RGB")
w, h = im.size
top = (h - w * 9 // 16) // 2
im.crop((0, top, w, top + w * 9 // 16)).save("classic/daylight.webp", "WEBP", quality=80, method=6)

src = np.asarray(Image.open("snow-dawn.png").convert("RGB")).astype(float)
bayer = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16 - 0.5
t = np.tile(bayer, (src.shape[0] // 4 + 1, src.shape[1] // 4 + 1))[:src.shape[0], :src.shape[1]]
for c, step in ((0, 8), (1, 4), (2, 8)):  # 5 bits of red, 6 of green, 5 of blue
    src[..., c] = np.clip(np.floor(src[..., c] / step + t + 0.5) * step, 0, 255)
Image.fromarray(src.astype(np.uint8)).save("seasonal/vector-5/snow-dawn.webp", "WEBP", lossless=True, quality=100,
                                           method=6)
```
