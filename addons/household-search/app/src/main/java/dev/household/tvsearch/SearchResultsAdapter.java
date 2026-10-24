package dev.household.tvsearch;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import dev.household.tvsearch.domain.ProviderAvailability;
import dev.household.tvsearch.domain.Subscription;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class SearchResultsAdapter extends RecyclerView.Adapter<SearchResultsAdapter.Holder> {
    interface Listener {
        void onSelected(SearchController.Result result);
        void onFocused(String contentId);
        void onUpFromFirstRow();
    }

    private final Listener listener;
    private final int columns;
    private final MediaArtworkLoader artwork;
    private List<SearchController.Result> results = Collections.emptyList();
    private List<Subscription> subscriptions = Collections.emptyList();

    SearchResultsAdapter(int columns, Listener listener, MediaArtworkLoader artwork) {
        this.columns = columns;
        this.listener = listener;
        this.artwork = artwork;
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    void submit(List<SearchController.Result> results, List<Subscription> subscriptions) {
        this.results = results;
        this.subscriptions = subscriptions;
        notifyDataSetChanged();
    }

    int positionOf(String contentId) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).entity.id.equals(contentId)) return i;
        }
        return -1;
    }

    SearchController.Result resultAt(int position) { return results.get(position); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        SearchController.Result result = results.get(position);
        holder.title.setText(result.entity.title);
        holder.metadata.setText(MediaPresentation.metadata(holder.itemView.getContext(), result.entity));
        artwork.bind(result.entity, holder.artwork, holder.itemView.findViewById(R.id.placeholderIcon),
                holder.itemView.findViewById(R.id.placeholderLabel));
        Set<String> providers = new LinkedHashSet<>();
        boolean included = false;
        for (ProviderAvailability offer : result.offers) {
            providers.add(offer.providerName);
            included |= Subscription.includes(offer, subscriptions);
        }
        holder.providers.setText(result.title == null ? MediaPresentation.origin(holder.itemView.getContext(), result.entity)
                : holder.itemView.getContext().getString(R.string.summary_providers, String.join(", ", providers)));
        holder.included.setVisibility(included ? View.VISIBLE : View.INVISIBLE);
        Integer percent = result.entity.progressPercent();
        holder.playbackProgress.setVisibility(percent == null ? View.GONE : View.VISIBLE);
        if (percent != null) holder.playbackProgress.setProgress(percent);
        holder.itemView.setContentDescription(result.entity.title + ". " + holder.metadata.getText()
                + ". " + holder.providers.getText() + (included ? ". " + holder.included.getText() : ""));
        holder.itemView.setOnClickListener(v -> listener.onSelected(result));
        holder.itemView.setOnFocusChangeListener((v, focused) -> {
            if (focused) listener.onFocused(result.entity.id);
        });
        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            int current = holder.getBindingAdapterPosition();
            if (event.getAction() != KeyEvent.ACTION_DOWN || current == RecyclerView.NO_POSITION) return false;
            boolean rtl = v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                boolean towardStart = (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) != rtl;
                return towardStart ? current % columns == 0
                        : current % columns == columns - 1 || current == results.size() - 1;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && current < columns) {
                listener.onUpFromFirstRow();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                int next = Math.min(current + columns, results.size() - 1);
                if (current / columns == (results.size() - 1) / columns) return true;
                focusPosition((RecyclerView) holder.itemView.getParent(), next);
                return true;
            }
            return false;
        });
    }

    static void focusPosition(RecyclerView grid, int position) {
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            holder.itemView.requestFocus();
            return;
        }
        grid.scrollToPosition(position);
        grid.post(new Runnable() {
            private int frames;
            @Override public void run() {
                RecyclerView.ViewHolder next = grid.findViewHolderForAdapterPosition(position);
                if (next != null) next.itemView.requestFocus();
                else if (++frames < 10) grid.postOnAnimation(this);
            }
        });
    }

    @Override public int getItemCount() { return results.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView metadata;
        final TextView providers;
        final TextView included;
        final ImageView artwork;
        final ProgressBar playbackProgress;

        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.title);
            metadata = view.findViewById(R.id.metadata);
            providers = view.findViewById(R.id.providers);
            included = view.findViewById(R.id.included);
            artwork = view.findViewById(R.id.artwork);
            playbackProgress = view.findViewById(R.id.playbackProgress);
        }
    }
}
