package dev.household.tvsearch;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import dev.household.tvsearch.domain.MediaEntity;

import java.util.Collections;
import java.util.List;

final class MediaRowAdapter extends RecyclerView.Adapter<MediaRowAdapter.Holder> {
    interface Listener {
        void onSelected(MediaEntity entity);
        void onFocused(String id);
        void onVertical(int keyCode, int position);
    }

    private final Listener listener;
    private final MediaArtworkLoader artwork;
    private List<MediaEntity> items = Collections.emptyList();

    MediaRowAdapter(Listener listener, MediaArtworkLoader artwork) {
        this.listener = listener;
        this.artwork = artwork;
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);
    }

    void submit(List<MediaEntity> items) {
        List<MediaEntity> previous = this.items;
        DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return previous.size(); }
            @Override public int getNewListSize() { return items.size(); }
            @Override public boolean areItemsTheSame(int oldPosition, int newPosition) {
                return previous.get(oldPosition).id.equals(items.get(newPosition).id);
            }
            @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                return previous.get(oldPosition) == items.get(newPosition);
            }
        });
        this.items = items;
        changes.dispatchUpdatesTo(this);
    }

    int positionOf(String id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(id)) return i;
        return -1;
    }

    MediaEntity itemAt(int position) { return items.get(position); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_media_card, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        MediaEntity entity = items.get(position);
        holder.title.setText(entity.title);
        holder.subtitle.setText(MediaPresentation.metadata(holder.itemView.getContext(), entity));
        holder.provider.setText(entity.provider);
        Integer percent = entity.progressPercent();
        holder.progress.setVisibility(percent == null ? View.INVISIBLE : View.VISIBLE);
        if (percent != null) holder.progress.setProgress(percent);
        artwork.bind(entity, holder.artwork, holder.itemView.findViewById(R.id.placeholderIcon),
                holder.itemView.findViewById(R.id.placeholderLabel));
        holder.itemView.setContentDescription(entity.title + ". " + holder.subtitle.getText() + ". "
                + (entity.provider == null ? "" : entity.provider) + (percent == null ? ""
                : ". " + holder.itemView.getContext().getString(R.string.progress_percent, percent)));
        holder.itemView.setOnClickListener(v -> listener.onSelected(entity));
        holder.itemView.setOnFocusChangeListener((v, focused) -> { if (focused) listener.onFocused(entity.id); });
        holder.itemView.setOnKeyListener((v, code, event) -> {
            int at = holder.getBindingAdapterPosition();
            if (event.getAction() != KeyEvent.ACTION_DOWN || at == RecyclerView.NO_POSITION) return false;
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                listener.onVertical(code, at);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                boolean towardStart = (code == KeyEvent.KEYCODE_DPAD_LEFT)
                        != (v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
                int next = towardStart ? at - 1 : at + 1;
                if (next >= 0 && next < items.size())
                    SearchResultsAdapter.focusPosition((RecyclerView) holder.itemView.getParent(), next);
                return true;
            }
            return false;
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title, subtitle, provider;
        final ImageView artwork;
        final ProgressBar progress;
        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.title);
            subtitle = view.findViewById(R.id.metadata);
            provider = view.findViewById(R.id.providers);
            artwork = view.findViewById(R.id.artwork);
            progress = view.findViewById(R.id.playbackProgress);
        }
    }
}
