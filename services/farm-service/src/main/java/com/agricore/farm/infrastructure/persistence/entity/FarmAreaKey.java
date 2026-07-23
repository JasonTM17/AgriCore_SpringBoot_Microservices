package com.agricore.farm.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class FarmAreaKey implements Serializable {

    private UUID farmId;
    private UUID id;

    public FarmAreaKey() {
    }

    public FarmAreaKey(UUID farmId, UUID id) {
        this.farmId = farmId;
        this.id = id;
    }

    public UUID getFarmId() {
        return farmId;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FarmAreaKey that)) {
            return false;
        }
        return Objects.equals(farmId, that.farmId) && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(farmId, id);
    }
}
