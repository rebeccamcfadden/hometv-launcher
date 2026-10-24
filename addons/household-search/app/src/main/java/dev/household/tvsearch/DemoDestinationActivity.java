package dev.household.tvsearch;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.household.tvsearch.data.FakeCatalogService;
import dev.household.tvsearch.domain.LaunchTarget;

public final class DemoDestinationActivity extends Activity {
    public static final String EXTRA_PROVIDER = "provider";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_CONTENT = "content";
    public static final String EXTRA_QUERY = "query";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(32 * getResources().getDisplayMetrics().density);
        root.setPadding(padding, padding, padding, padding);
        setContentView(root);
        text(root, getString(R.string.demo_destination), 28);
        String provider = getIntent().getStringExtra(EXTRA_PROVIDER);
        String level = getIntent().getStringExtra(EXTRA_LEVEL);
        if ((!FakeCatalogService.CINEMA.equals(provider) && !FakeCatalogService.SERIES.equals(provider))
                || !validLevel(level)) {
            text(root, getString(R.string.invalid_demo_target), 20);
        } else {
            text(root, getString(R.string.destination_provider,
                    FakeCatalogService.CINEMA.equals(provider) ? "Demo Cinema" : "Demo Series"), 22);
            text(root, getString(R.string.destination_level, level), 20);
            String query = getIntent().getStringExtra(EXTRA_QUERY);
            String content = getIntent().getStringExtra(EXTRA_CONTENT);
            if (query != null) text(root, getString(R.string.destination_query, query), 20);
            if (content != null) text(root, getString(R.string.destination_content, content), 16);
            text(root, getString(R.string.demo_destination_notice), 18);
        }
        Button back = new Button(this, null, 0, R.style.SearchButton);
        back.setText(R.string.return_to_search);
        back.setOnClickListener(v -> finish());
        root.addView(back);
        back.requestFocus();
    }

    private static boolean validLevel(String level) {
        return LaunchTarget.Level.TITLE.name().equals(level)
                || LaunchTarget.Level.SEARCH.name().equals(level)
                || LaunchTarget.Level.APP_HOME.name().equals(level);
    }

    private void text(LinearLayout root, String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(Color.WHITE);
        text.setTextSize(size);
        text.setPadding(0, 0, 0, Math.round(14 * getResources().getDisplayMetrics().density));
        root.addView(text);
    }
}
