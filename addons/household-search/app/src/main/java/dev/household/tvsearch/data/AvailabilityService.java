package dev.household.tvsearch.data;

import java.util.List;
import dev.household.tvsearch.domain.MediaTitle;
import dev.household.tvsearch.domain.ProviderAvailability;

public interface AvailabilityService {
    List<ProviderAvailability> getAvailability(String contentId, MediaTitle.Type type, String region)
            throws CatalogException;
}
