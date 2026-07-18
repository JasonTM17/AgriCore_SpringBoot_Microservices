package com.agricore.farm.api.response;

import java.util.UUID;

public record FarmResourceAccessResponse(
        UUID farmId,
        UUID plotId
) {
}
