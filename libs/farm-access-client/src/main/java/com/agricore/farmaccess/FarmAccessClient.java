package com.agricore.farmaccess;

import java.util.UUID;

/**
 * Authoritative farm-service boundary used synchronously inside authenticated servlet requests.
 * Detached jobs, including assistant generations, must capture caller credentials at their own
 * request boundary and use a purpose-built downstream client rather than this thread-bound API.
 */
public interface FarmAccessClient {

    FarmResourceAccess requireFarm(UUID farmId);

    FarmResourceAccess requirePlot(UUID plotId);

    FarmResourceAccess requireFarmPlot(UUID farmId, UUID plotId);

    boolean isSystemAdmin();
}
