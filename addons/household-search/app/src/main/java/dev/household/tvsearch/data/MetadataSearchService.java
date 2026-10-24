package dev.household.tvsearch.data;

import java.util.List;
import dev.household.tvsearch.domain.MediaTitle;

/** Blocking operations; the controller calls these off the UI thread. */
public interface MetadataSearchService {
    List<MediaTitle> search(String query) throws CatalogException;
    MediaTitle getTitleDetails(String contentId, MediaTitle.Type type) throws CatalogException;
}
