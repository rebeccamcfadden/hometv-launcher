package com.hometv.launcher;

import android.content.Context;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * API keys (F16; design_preview/README.md, API keys; src/screens/panes/keys.ts; no Figma design yet): the household's
 * own TMDB, Watchmode and Movie of the Night keys, entered on a phone (the QR) or a laptop (the address and code),
 * never on the TV's keyboard. Each row says whether its key works, ends in which four characters, was refused or
 * couldn't be checked; OK checks it again. Remove (asks first) leaves no key, the build's included; New code (asks
 * first) shuts out a page opened with the old one. The page's server runs only while this pane shows ({@link ApiKeys}).
 *
 * <p>Three of them: Launcher settings' row (all three keys, the price keys behind an empty state with Turn on while
 * Price lookup is off; never itself switched off, since the catalogue needs the TMDB key), and First run's TMDB key and
 * Price keys tabs. The state is {@link ApiKeys}'s; this only draws it, and draws again when it changes.
 */
final class KeysSection implements SettingsSection {
    static final String ID = "keys", TMDB_ID = "tmdbKey", PRICES_ID = "priceKeys";

    enum Mode { ALL, TMDB, PRICES }

    /** The QR's side, in px at 1080p, as the prototype's. */
    private static final int QR = 200;
    private static final String GROUP = "keys";
    private static final String KEY_PRICES_ON = "keys-prices-on", KEY_REMOVE = "keys-remove", KEY_CODE = "keys-code";
    /** A refused key's line and border (design_preview's .apikey-row--bad). */
    private static final int BAD = 0xFFF19A8E, BAD_LINE = 0x80F19A8E;
    private static final List<KeyRules.Id> PRICE_KEYS = Collections.unmodifiableList(
            Arrays.asList(KeyRules.Id.WATCHMODE, KeyRules.Id.MOTN));

    private final Mode mode;
    private final Runnable listener = this::keysChanged;
    private ApiKeys keys;
    private Context ctx;
    private Host host;
    private ViewGroup root;
    private LinearLayout column;
    private View first;

    private KeysSection(Mode mode) {
        this.mode = mode;
    }

    /** Launcher settings › API keys. */
    static KeysSection all() {
        return new KeysSection(Mode.ALL);
    }

    /** First run's TMDB key tab, before Sign in, on a TV that had no working TMDB key when First run opened. */
    static KeysSection tmdb() {
        return new KeysSection(Mode.TMDB);
    }

    /** First run's Price keys tab, Price lookup's setup. */
    static KeysSection prices() {
        return new KeysSection(Mode.PRICES);
    }

    @Override
    public String id() {
        return mode == Mode.TMDB ? TMDB_ID : mode == Mode.PRICES ? PRICES_ID : ID;
    }

    @Override
    public int label() {
        return mode == Mode.TMDB ? R.string.keys_tab_tmdb : mode == Mode.PRICES ? R.string.keys_tab_prices
                : R.string.settings_keys;
    }

    @Override
    public int icon() {
        return R.drawable.ic_ui_key;
    }

    /** Price lookup off: Launcher settings' pane keeps the TMDB key and puts the price keys behind an empty state. */
    private boolean pricesOff(Context c) {
        return mode == Mode.ALL && !FeaturePrefs.live(c).on(Features.Feature.PRICES);
    }

    /** The keys whose rows show now. */
    private List<KeyRules.Id> ids(Context c) {
        if (mode == Mode.TMDB || pricesOff(c)) return Collections.singletonList(KeyRules.Id.TMDB);
        if (mode == Mode.PRICES) return PRICE_KEYS;
        return Arrays.asList(KeyRules.Id.values());
    }

    @Override
    public CharSequence value(Context c) {
        KeyRules.Summary s = ApiKeys.get(c).summary(ids(c));
        android.content.res.Resources res = c.getResources();
        switch (s.kind) {
            case REFUSED:
                return amberText(res.getQuantityString(R.plurals.keys_value_refused, s.count, s.count));
            case UNCHECKED:
                return amberText(res.getQuantityString(R.plurals.keys_value_unchecked, s.count, s.count));
            case CHECKING:
                return c.getString(R.string.keys_value_checking);
            case NOT_SET:
                return c.getString(R.string.keys_value_not_set);
            case SOME:
                return res.getQuantityString(R.plurals.keys_value_some, s.count, s.count, s.total);
            default:
                return res.getQuantityString(R.plurals.keys_value_working, s.count, s.count);
        }
    }

    private static CharSequence amberText(String text) {
        SpannableString warn = new SpannableString(text);
        warn.setSpan(new ForegroundColorSpan(StateUi.amber()), 0, warn.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return warn;
    }

    @Override
    public View pane(Context c, Host h) {
        this.ctx = c;
        this.host = h;
        if (keys != null) keys.removeListener(listener);
        keys = ApiKeys.get(c);
        keys.addListener(listener);
        if (root != null) keys.release(root);
        column = PaneParts.column(c);
        ViewGroup pane = PaneParts.scroller(c, column);
        root = pane;
        // The page's server runs while this pane shows: a pane left for another section lets it go.
        pane.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                keys.hold(pane);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                keys.release(pane);
            }
        });
        render();
        return pane;
    }

    @Override
    public View firstFocus() {
        return first;
    }

    @Override
    public void onClose() {
        if (keys == null) return;
        keys.removeListener(listener);
        if (root != null) keys.release(root);
    }

    private void keysChanged() {
        if (host != null) host.valueChanged();
        if (root != null && root.isAttachedToWindow()) render();
    }

    /** Builds the pane for the keys as they are; focus stays where it was if it was in the pane. */
    private void render() {
        View focused = host.shell().focused();
        boolean inPane = focused != null && Focus.isInside(root, focused);
        String focusKey = Focus.key(focused);
        column.removeAllViews();

        int title = mode == Mode.TMDB ? R.string.keys_title_tmdb : mode == Mode.PRICES ? R.string.keys_title_prices
                : R.string.keys_title_all;
        int sub = mode == Mode.TMDB ? R.string.keys_sub_tmdb : mode == Mode.PRICES ? R.string.keys_sub_prices
                : R.string.keys_sub_all;
        column.addView(PaneParts.eyebrow(ctx, ctx.getString(label())));
        column.addView(PaneParts.title(ctx, ctx.getString(title), 40), PaneParts.below(10));
        column.addView(PaneParts.sub(ctx, ctx.getString(sub)), PaneParts.below(8));

        // .keys-setup: the rows on the left, the QR and the laptop's address on the right.
        LinearLayout setup = PaneParts.across(ctx);
        setup.setGravity(Gravity.TOP);
        LinearLayout rows = PaneParts.column(ctx);
        List<KeyRules.Id> ids = ids(ctx);
        first = null;
        for (int i = 0; i < ids.size(); i++) {
            View r = row(ids.get(i), i == 0);
            if (i == 0) first = r;
            rows.addView(r, PaneParts.wide(i == 0 ? 0 : 12));
        }
        if (pricesOff(ctx)) rows.addView(pricesOffBox(), PaneParts.wide(12));
        setup.addView(rows, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        setup.addView(qrBox(), new LinearLayout.LayoutParams(Ui.px(300), ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) setup.getChildAt(1).getLayoutParams()).leftMargin = Ui.px(36);
        column.addView(setup, PaneParts.wide(26));

        if (keys.portTaken()) {
            column.addView(amber(ctx.getString(R.string.keys_port_taken, KeyInbox.DEFAULT_PORT)), PaneParts.wide(16));
        }
        column.addView(PaneParts.note(ctx, ctx.getString(R.string.keys_note), true), PaneParts.wide(22));
        List<View> buttons = new ArrayList<>();
        List<KeyRules.Id> removable = removable();
        if (!removable.isEmpty()) {
            CharSequence label = removable.size() == 1
                    ? ctx.getString(R.string.keys_remove_one, removable.get(0).service)
                    : ctx.getString(R.string.keys_remove_some);
            buttons.add(Kit.button(ctx, label, 0, Kit.NORMAL, false, GROUP, KEY_REMOVE, false, v -> askRemove()));
        }
        buttons.add(Kit.button(ctx, ctx.getString(R.string.keys_new_code), R.drawable.ic_ui_retry, Kit.NORMAL, false,
                GROUP, KEY_CODE, false, v -> host.shell().push(PaneParts.confirm(ctx, host.shell(),
                        ctx.getString(R.string.keys_new_code_title), ctx.getString(R.string.keys_new_code_body),
                        ctx.getString(R.string.keys_new_code), ctx.getString(R.string.keys_cancel), keys::newCode))));
        column.addView(PaneParts.actions(ctx, buttons), PaneParts.below(28));

        if (inPane) {
            View target = Focus.byKey(column, focusKey);
            if (target == null) target = first;
            if (target != null) host.shell().focus(target);
            else host.shell().refocus();
        }
    }

    /**
     * .apikey-row: the service and what its key is for, the line that says how the key is (green, amber or red, with a
     * mark), and its free plan on the right. Focused: the raised surface with the focus ring, scale 1.02. OK checks a
     * key entered here again, or says where to enter one.
     */
    private View row(KeyRules.Id id, boolean isDefault) {
        KeyRules.State state = keys.state(id);
        String status = status(id, state);
        int tone = state == KeyRules.State.OK || state == KeyRules.State.BUILT ? Ui.GOOD
                : state == KeyRules.State.UNREACHABLE ? StateUi.amber()
                : state == KeyRules.State.BAD ? BAD : Ui.TEXT_3;
        int mark = state == KeyRules.State.OK || state == KeyRules.State.BUILT ? R.drawable.ic_ui_check
                : state == KeyRules.State.UNREACHABLE || state == KeyRules.State.BAD ? R.drawable.ic_ui_alert : 0;

        LinearLayout row = PaneParts.across(ctx);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.px(96));
        row.setPadding(Ui.px(22), Ui.px(14), Ui.px(22), Ui.px(14));
        row.setBackground(Ui.states(Ui.round(Ui.SURFACE, Ui.RADIUS_L,
                        state == KeyRules.State.BAD ? BAD_LINE : 0x00000000, Ui.LINE_W), null,
                Ui.round(Ui.SURFACE_2, Ui.RADIUS_L, Ui.ACCENT, Ui.FOCUS_W)));

        LinearLayout text = PaneParts.column(ctx);
        TextView name = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.MEDIUM);
        SpannableStringBuilder n = new SpannableStringBuilder(id.service);
        int start = n.length();
        n.append(" \u00b7 ").append(ctx.getString(useOf(id)));
        n.setSpan(new ForegroundColorSpan(Ui.TEXT_2), start, n.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        name.setText(n);
        text.addView(name, PaneParts.wide(0));
        LinearLayout line = PaneParts.across(ctx);
        line.setGravity(Gravity.CENTER_VERTICAL);
        if (mark != 0) {
            ImageView icon = Ui.icon(ctx, mark, 24, tone);
            line.addView(icon, new LinearLayout.LayoutParams(Ui.px(24), Ui.px(24)));
        }
        TextView s = Ui.text(ctx, Ui.T_SMALL, tone, Ui.REGULAR);
        s.setText(status);
        s.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.leftMargin = mark != 0 ? Ui.px(8) : 0;
        line.addView(s, slp);
        text.addView(line, PaneParts.wide(6));
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView plan = Ui.text(ctx, Ui.T_MIN, Ui.TEXT_3, Ui.REGULAR);
        plan.setText(planOf(id));
        plan.setGravity(Gravity.END);
        plan.setMaxWidth(Ui.px(220));
        row.addView(plan, PaneParts.after(16));

        row.setContentDescription(id.service + ", " + status);
        Focus.make(row, GROUP, "key-" + id.id, isDefault, v -> onOk(id, state));
        Ui.scaleOnFocus(row, 1.02f);
        return row;
    }

    private void onOk(KeyRules.Id id, KeyRules.State state) {
        switch (state) {
            case OK:
            case BAD:
            case UNREACHABLE:
                keys.recheck(id);
                break;
            case BUILT:
                host.shell().toast(ctx.getString(R.string.keys_toast_built, id.service));
                break;
            case UNSET:
                host.shell().toast(ctx.getString(R.string.keys_toast_unset, id.service));
                break;
            default:
                break;
        }
    }

    /** The line under a key's name (keys.ts keyStatus). */
    private String status(KeyRules.Id id, KeyRules.State state) {
        switch (state) {
            case OK:
                return ctx.getString(R.string.keys_status_ok, keys.last4(id));
            case BUILT:
                return ctx.getString(R.string.keys_status_built);
            case CHECKING:
                return ctx.getString(R.string.keys_status_checking, id.service);
            case UNREACHABLE:
                return ctx.getString(R.string.keys_status_unreachable, id.service);
            case BAD:
                return id == KeyRules.Id.TMDB ? ctx.getString(R.string.keys_status_bad_tmdb)
                        : ctx.getString(R.string.keys_status_bad, id.service);
            default:
                return ctx.getString(id.optional() ? R.string.keys_status_unset_optional : R.string.keys_status_unset);
        }
    }

    private static int useOf(KeyRules.Id id) {
        return id == KeyRules.Id.TMDB ? R.string.keys_use_tmdb : id == KeyRules.Id.WATCHMODE
                ? R.string.keys_use_watchmode : R.string.keys_use_motn;
    }

    private static int planOf(KeyRules.Id id) {
        return id == KeyRules.Id.TMDB ? R.string.keys_plan_tmdb : id == KeyRules.Id.WATCHMODE
                ? R.string.keys_plan_watchmode : R.string.keys_plan_motn;
    }

    /** .keys-off: where the price keys would be, that Price lookup is off, and Turn on Price lookup. */
    private View pricesOffBox() {
        LinearLayout box = PaneParts.column(ctx);
        box.setPadding(Ui.px(22), Ui.px(18), Ui.px(22), Ui.px(20));
        box.setBackground(PaneParts.dashed(0x00000000, Ui.RADIUS_L, Ui.LINE_STRONG));
        TextView name = Ui.text(ctx, Ui.T_BODY, Ui.TEXT_1, Ui.MEDIUM);
        name.setText(R.string.keys_prices_off_title);
        box.addView(name, PaneParts.wide(0));
        box.addView(PaneParts.sub(ctx, ctx.getString(R.string.keys_prices_off_sub)), PaneParts.wide(6));
        View on = Kit.button(ctx, ctx.getString(R.string.keys_prices_turn_on), R.drawable.ic_ui_power, Kit.NORMAL,
                false, GROUP, KEY_PRICES_ON, false, v -> host.turnOn(Features.Feature.PRICES));
        box.addView(on, PaneParts.below(16));
        return box;
    }

    /** .keys-qr: the QR that opens the page with the code, and the address and code for a laptop. */
    private View qrBox() {
        LinearLayout box = PaneParts.column(ctx);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        String ip = Notes.localAddress(ctx);
        String url = KeyRules.pageUrl(ip, KeyInbox.DEFAULT_PORT, keys.code());
        if (url == null) {
            box.addView(amber(ctx.getString(R.string.keys_no_wifi)), PaneParts.wide(0));
            return box;
        }
        ImageView qr = new ImageView(ctx);
        qr.setImageBitmap(AccountSection.qrBitmap(Qr.encode(url), Ui.px(QR)));
        qr.setContentDescription(ctx.getString(R.string.keys_qr_description));
        qr.setBackground(Ui.round(0xFFFFFFFF, Ui.RADIUS_M));
        qr.setClipToOutline(true);
        box.addView(qr, new LinearLayout.LayoutParams(Ui.px(QR), Ui.px(QR)));
        box.addView(centred(ctx.getString(R.string.keys_qr_caption), Ui.TEXT_3, Ui.REGULAR, Ui.T_MIN), PaneParts.below(10));
        box.addView(centred(ctx.getString(R.string.keys_laptop), Ui.TEXT_3, Ui.REGULAR, Ui.T_MIN), PaneParts.below(18));
        box.addView(centred(KeyRules.address(ip, KeyInbox.DEFAULT_PORT), Ui.TEXT_1, Ui.MEDIUM, Ui.T_MIN),
                PaneParts.below(4));
        box.addView(centred(ctx.getString(R.string.keys_laptop_code), Ui.TEXT_3, Ui.REGULAR, Ui.T_MIN),
                PaneParts.below(10));
        TextView code = centred(keys.code(), Ui.TEXT_1, Ui.MEDIUM, 26);
        code.setLetterSpacing(0.12f);
        box.addView(code, PaneParts.below(4));
        return box;
    }

    private TextView centred(CharSequence s, int color, android.graphics.Typeface face, float size) {
        TextView t = Ui.text(ctx, size, color, face);
        t.setText(s);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        return t;
    }

    private TextView amber(CharSequence s) {
        TextView t = PaneParts.note(ctx, s, false);
        t.setTextColor(StateUi.amber());
        return t;
    }

    /** The shown keys there is something to remove from: entered, or the build's. */
    private List<KeyRules.Id> removable() {
        List<KeyRules.Id> out = new ArrayList<>();
        for (KeyRules.Id id : ids(ctx)) {
            KeyRules.State s = keys.state(id);
            if (s != KeyRules.State.UNSET && s != KeyRules.State.CHECKING) out.add(id);
        }
        return out;
    }

    private void askRemove() {
        List<KeyRules.Id> ids = removable();
        if (ids.isEmpty()) return;
        List<CharSequence> labels = new ArrayList<>();
        List<Runnable> runs = new ArrayList<>();
        for (KeyRules.Id id : ids) {
            labels.add(ctx.getString(R.string.keys_remove_action, id.service));
            runs.add(() -> keys.remove(id));
        }
        CharSequence title = ids.size() == 1 ? ctx.getString(R.string.keys_remove_title_one, ids.get(0).service)
                : ctx.getString(R.string.keys_remove_title_some);
        host.shell().push(PaneParts.choose(ctx, host.shell(), title, ctx.getString(R.string.keys_remove_body), labels,
                runs, ctx.getString(R.string.keys_cancel)));
    }

    /** The buttons under the rows, left to right, by focus key. */
    private List<String> buttonKeys() {
        List<String> out = new ArrayList<>();
        if (!removable().isEmpty()) out.add(KEY_REMOVE);
        out.add(KEY_CODE);
        return out;
    }

    private boolean focus(String key) {
        View v = Focus.byKey(column, key);
        if (v != null) host.shell().focus(v);
        return true;
    }

    /**
     * ↓ steps through the rows, Turn on Price lookup and the buttons' row; → and ← stay within a row (the QR beside the
     * rows isn't a stop), so a long → never jumps to another row. ↓ from the buttons goes on to First run's footer;
     * Launcher settings has none, so it stays. ↑ is the page's spatial move, which the column keeps in order.
     */
    @Override
    public boolean onKey(Screen.Key key, View focused) {
        String k = Focus.key(focused);
        if (k == null || column == null) return false;
        List<KeyRules.Id> list = ids(ctx);
        String below = pricesOff(ctx) ? KEY_PRICES_ON : buttonKeys().get(0);
        for (int i = 0; i < list.size(); i++) {
            if (!k.equals("key-" + list.get(i).id)) continue;
            if (key == Screen.Key.RIGHT) return true;
            if (key == Screen.Key.DOWN) return focus(i < list.size() - 1 ? "key-" + list.get(i + 1).id : below);
            return false;
        }
        if (KEY_PRICES_ON.equals(k)) {
            if (key == Screen.Key.DOWN) return focus(buttonKeys().get(0));
            return key == Screen.Key.RIGHT;
        }
        List<String> buttons = buttonKeys();
        int at = buttons.indexOf(k);
        if (at < 0) return false;
        switch (key) {
            case RIGHT:
                if (at < buttons.size() - 1) focus(buttons.get(at + 1));
                return true;
            case LEFT:
                return at > 0 && focus(buttons.get(at - 1));
            case DOWN:
                return !host.inSetup();
            default:
                return false;
        }
    }
}
