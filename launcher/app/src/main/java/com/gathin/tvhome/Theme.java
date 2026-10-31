package com.gathin.tvhome;

/**
 * One look for the whole launcher: the values {@link Ui} reads, set by {@link Ui#apply(Theme)} before any screen is
 * built (Figma file lwShqRaciMpDUDW5dYljNw, "CleanTV · Theme explorations", the Theme variable collection). A theme
 * changes colours, type, corner shapes, shading, overlays and the backdrop only; layout, sizes and data stay the same
 * in every theme. Midnight is today's look, value for value.
 */
final class Theme {
    enum Group { CLASSIC, RETRO, SEASONAL }

    /** A Google Fonts family and the weights the launcher asks for; a null family is Roboto, the system font. */
    static final class Font {
        static final Font SYSTEM = new Font(null, 400, 500, 700);

        final String family;
        final int regular;
        final int medium;
        final int bold;

        Font(String family, int regular, int medium, int bold) {
            this.family = family;
            this.regular = regular;
            this.medium = medium;
            this.bold = bold;
        }
    }

    final String id;
    final Group group;
    /** English name; the picker shows its own string resource. */
    final String name;

    // Colours, as Ui's tokens.
    final int bg, bgRaised, surface, surface2, surface3, line, lineStrong;
    final int text1, text2, text3, ghost;
    final int accent, accentSoft, onAccent, active, onActive;
    final int good, goodSoft, star;
    final int menuBg, toastBg, scrim, shadeTop, shadeBottom;
    /** An app or shortcut tile's resting fill, Home's weather strip too (a step below surface). */
    final int tile;
    /** Search's list and keyboard panel, a step below tile. */
    final int panel;
    /** Pick for me's count card, a step above surface. */
    final int card;
    /** The active tab in a tab bar (the title preview's tabs). */
    final int selected;
    /** A dialog's own opaque fill (the rent or buy dialog). */
    final int dialogBg;
    /** A person's round portrait behind the initials, until the photo covers it. */
    final int portrait;
    /** Home's shade over the backdrop, top to bottom, and the extra dim when Home is scrolled. */
    final int[] homeShade;
    final int homeDim;
    /** The sky for a backdrop drawn from the theme alone, and the gradient shown while an image loads. */
    final int skyTop, skyMid, horizon;

    // Shape.
    final float radiusS, radiusM, radiusL, radiusXl, radiusPill;
    /** Hairline width and the focus ring's width, in design pixels. */
    final float lineWidth, focusWidth;
    /** The focused tile's glow; an offset above zero draws a hard shadow instead (Groovy, 8-bit). */
    final int glowColor;
    final float glowBlur, glowOffset, glowSpread;
    /**
     * Whether the focused tile draws the glow (or the hard shadow) at all. Off in Midnight only: its Figma glow was
     * never drawn by the launcher, whose focused tile is the ring alone.
     */
    final boolean tileGlow;
    /** A resting tile's shadow. */
    final int shadowColor;
    final float shadowBlur, shadowOffset;
    /** Whether resting tiles draw that shadow; off in Midnight only, which never drew one. */
    final boolean tileShadow;
    /** Behind large text over the backdrop; transparent when the theme has none. */
    final int textShadowColor;
    final float textShadowBlur, textShadowOffset;

    // Type.
    final Font displayFont, uiFont;

    // Overlays.
    final boolean scanlines, vignette;

    /**
     * Where the picture behind the pages comes from, read by Backdrops: {@code res:<drawable>} bundled,
     * {@code url:<path>} downloaded once and cached, {@code pixel:<name>} a bundled 160×90 scene drawn without
     * smoothing, {@code sky} the theme's own gradient, {@code none} plain bg.
     */
    final String backdrop;

    private Theme(Builder b) {
        id = b.id;
        group = b.group;
        name = b.name;
        bg = b.bg;
        bgRaised = b.bgRaised;
        surface = b.surface;
        surface2 = b.surface2;
        surface3 = b.surface3;
        line = b.line;
        lineStrong = b.lineStrong;
        text1 = b.text1;
        text2 = b.text2;
        text3 = b.text3;
        ghost = b.ghost;
        accent = b.accent;
        accentSoft = b.accentSoft;
        onAccent = b.onAccent;
        active = b.active;
        onActive = b.onActive;
        good = b.good;
        goodSoft = b.goodSoft;
        star = b.star;
        menuBg = b.menuBg;
        toastBg = b.toastBg;
        scrim = b.scrim;
        shadeTop = b.shadeTop;
        shadeBottom = b.shadeBottom;
        tile = b.tile;
        panel = b.panel;
        card = b.card;
        selected = b.selected;
        dialogBg = b.dialogBg;
        portrait = b.portrait;
        homeShade = b.homeShade.clone();
        homeDim = b.homeDim;
        skyTop = b.skyTop;
        skyMid = b.skyMid;
        horizon = b.horizon;
        radiusS = b.radiusS;
        radiusM = b.radiusM;
        radiusL = b.radiusL;
        radiusXl = b.radiusXl;
        radiusPill = b.radiusPill;
        lineWidth = b.lineWidth;
        focusWidth = b.focusWidth;
        glowColor = b.glowColor;
        glowBlur = b.glowBlur;
        glowOffset = b.glowOffset;
        glowSpread = b.glowSpread;
        tileGlow = b.tileGlow;
        shadowColor = b.shadowColor;
        shadowBlur = b.shadowBlur;
        shadowOffset = b.shadowOffset;
        tileShadow = b.tileShadow;
        textShadowColor = b.textShadowColor;
        textShadowBlur = b.textShadowBlur;
        textShadowOffset = b.textShadowOffset;
        displayFont = b.displayFont;
        uiFont = b.uiFont;
        scanlines = b.scanlines;
        vignette = b.vignette;
        backdrop = b.backdrop;
    }

    Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Today's look (design_preview/src/styles/tokens.css): every value Ui had before themes. Its tiles draw no glow and
     * no shadow, as before; every other theme draws its own.
     */
    static Theme midnight() {
        Builder b = new Builder("midnight", Group.CLASSIC, "Midnight");
        b.tileGlow = false;
        b.tileShadow = false;
        return b.build();
    }

    static final class Builder {
        String id;
        Group group;
        String name;
        int bg = 0xFF090E15, bgRaised = 0xFF0D141D, surface = 0x0DFFFFFF, surface2 = 0x17FFFFFF, surface3 = 0x24FFFFFF;
        int line = 0x17FFFFFF, lineStrong = 0x2EFFFFFF;
        int text1 = 0xFFF4F5F7, text2 = 0xFFADB5C0, text3 = 0xFF7D8999, ghost = 0xFF5E6878;
        int accent = 0xFFC4DDF7, accentSoft = 0x24C4DDF7, onAccent = 0xFF101820, active = 0xE8FFFFFF;
        int onActive = 0xFF101820;
        int good = 0xFF8FD9A8, goodSoft = 0x298FD9A8, star = 0xFFF2C46D;
        int menuBg = 0xF5161C25, toastBg = 0xF7161E29, scrim = 0x9405080C;
        int shadeTop = 0xDB090E15, shadeBottom = 0xE6090E15;
        int tile = 0x0BFFFFFF, panel = 0x08FFFFFF, card = 0x0FFFFFFF, selected = 0x1FFFFFFF;
        int dialogBg = 0xFF121A24, portrait = 0xFF1C2633;
        int[] homeShade = {0x40090E15, 0x8C090E15, 0xEB090E15};
        int homeDim = 0x99090E15;
        int skyTop = 0xFF0D1622, skyMid = 0xFF1A2433, horizon = 0xFF3A3F52;
        float radiusS = 8, radiusM = 12, radiusL = 16, radiusXl = 24, radiusPill = 100;
        float lineWidth = 1, focusWidth = 4;
        int glowColor = 0x59C4DDF7;
        float glowBlur = 24, glowOffset = 0, glowSpread = 0;
        boolean tileGlow = true;
        int shadowColor = 0x4D000000;
        float shadowBlur = 16, shadowOffset = 6;
        boolean tileShadow = true;
        int textShadowColor = 0x00000000;
        float textShadowBlur = 0, textShadowOffset = 0;
        Font displayFont = Font.SYSTEM, uiFont = Font.SYSTEM;
        boolean scanlines, vignette;
        String backdrop = "res:bg_evening";

        Builder(String id, Group group, String name) {
            this.id = id;
            this.group = group;
            this.name = name;
        }

        private Builder(Theme t) {
            id = t.id;
            group = t.group;
            name = t.name;
            bg = t.bg;
            bgRaised = t.bgRaised;
            surface = t.surface;
            surface2 = t.surface2;
            surface3 = t.surface3;
            line = t.line;
            lineStrong = t.lineStrong;
            text1 = t.text1;
            text2 = t.text2;
            text3 = t.text3;
            ghost = t.ghost;
            accent = t.accent;
            accentSoft = t.accentSoft;
            onAccent = t.onAccent;
            active = t.active;
            onActive = t.onActive;
            good = t.good;
            goodSoft = t.goodSoft;
            star = t.star;
            menuBg = t.menuBg;
            toastBg = t.toastBg;
            scrim = t.scrim;
            shadeTop = t.shadeTop;
            shadeBottom = t.shadeBottom;
            tile = t.tile;
            panel = t.panel;
            card = t.card;
            selected = t.selected;
            dialogBg = t.dialogBg;
            portrait = t.portrait;
            homeShade = t.homeShade.clone();
            homeDim = t.homeDim;
            skyTop = t.skyTop;
            skyMid = t.skyMid;
            horizon = t.horizon;
            radiusS = t.radiusS;
            radiusM = t.radiusM;
            radiusL = t.radiusL;
            radiusXl = t.radiusXl;
            radiusPill = t.radiusPill;
            lineWidth = t.lineWidth;
            focusWidth = t.focusWidth;
            glowColor = t.glowColor;
            glowBlur = t.glowBlur;
            glowOffset = t.glowOffset;
            glowSpread = t.glowSpread;
            tileGlow = t.tileGlow;
            shadowColor = t.shadowColor;
            shadowBlur = t.shadowBlur;
            shadowOffset = t.shadowOffset;
            tileShadow = t.tileShadow;
            textShadowColor = t.textShadowColor;
            textShadowBlur = t.textShadowBlur;
            textShadowOffset = t.textShadowOffset;
            displayFont = t.displayFont;
            uiFont = t.uiFont;
            scanlines = t.scanlines;
            vignette = t.vignette;
            backdrop = t.backdrop;
        }

        Theme build() {
            return new Theme(this);
        }
    }
}
