package dev.household.tvsearch;

import dev.household.tvsearch.data.AvailabilityService;
import dev.household.tvsearch.data.CatalogException;
import dev.household.tvsearch.data.MetadataSearchService;
import dev.household.tvsearch.domain.MediaTitle;
import dev.household.tvsearch.domain.MediaEntity;
import dev.household.tvsearch.domain.ProviderAvailability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/** UI-independent request state. Late responses cannot replace a newer query. */
public final class SearchController {
    public enum Status { EMPTY_QUERY, LOADING, RESULTS, NO_RESULTS, ERROR }

    public static final class Result {
        public final MediaTitle title;
        public final MediaEntity entity;
        public final List<ProviderAvailability> offers;

        public Result(MediaTitle title, List<ProviderAvailability> offers) {
            this(MediaEntity.fromTitle(title), offers);
        }

        public Result(MediaEntity entity, List<ProviderAvailability> offers) {
            this.entity = entity;
            this.title = entity.catalogTitle;
            this.offers = Collections.unmodifiableList(new ArrayList<>(offers));
        }
    }

    public static final class State {
        public final Status status;
        public final String query;
        public final List<Result> results;
        public final String error;
        public final String notice;

        State(Status status, String query, List<Result> results, String error) {
            this(status, query, results, error, null);
        }

        State(Status status, String query, List<Result> results, String error, String notice) {
            this.status = status;
            this.query = query;
            this.results = Collections.unmodifiableList(new ArrayList<>(results));
            this.error = error;
            this.notice = notice;
        }
    }

    public interface Listener {
        void onState(State state);
    }

    public interface Source {
        Batch search(String query) throws CatalogException;
    }

    public static final class Batch {
        public final List<Result> results;
        public final String notice;
        public Batch(List<Result> results, String notice) {
            this.results = Collections.unmodifiableList(new ArrayList<>(results));
            this.notice = notice;
        }
    }

    private final Source source;
    private final Executor worker;
    private final Executor main;
    private final Listener listener;
    private int generation;
    private boolean closed;
    private State state = new State(Status.EMPTY_QUERY, "", Collections.emptyList(), null);

    public SearchController(MetadataSearchService metadata, AvailabilityService availability, String region,
                            Executor worker, Executor main, Listener listener) {
        this(query -> {
            List<Result> results = new ArrayList<>();
            for (MediaTitle title : metadata.search(query)) {
                results.add(new Result(title, availability.getAvailability(title.id, title.type, region)));
            }
            return new Batch(results, null);
        }, worker, main, listener);
    }

    public SearchController(Source source, Executor worker, Executor main, Listener listener) {
        this.source = source;
        this.worker = worker;
        this.main = main;
        this.listener = listener;
    }

    public State state() { return state; }

    public void search(String query) {
        if (closed) return;
        String trimmed = query == null ? "" : query.trim();
        int request = ++generation;
        if (trimmed.isEmpty()) {
            publish(new State(Status.EMPTY_QUERY, "", Collections.emptyList(), null));
            return;
        }
        publish(new State(Status.LOADING, trimmed, Collections.emptyList(), null));
        worker.execute(() -> {
            State response;
            try {
                Batch batch = source.search(trimmed);
                List<Result> results = batch.results;
                response = new State(results.isEmpty() ? Status.NO_RESULTS : Status.RESULTS,
                        trimmed, results, null, batch.notice);
            } catch (CatalogException e) {
                response = new State(Status.ERROR, trimmed, Collections.emptyList(), e.getMessage());
            }
            State completed = response;
            main.execute(() -> {
                if (!closed && request == generation) publish(completed);
            });
        });
    }

    public void close() {
        closed = true;
        ++generation;
    }

    private void publish(State next) {
        state = next;
        listener.onState(next);
    }
}
