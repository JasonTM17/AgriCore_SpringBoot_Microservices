package com.agricore.assistant;

import com.agricore.assistant.infrastructure.persistence.RoleSnapshotJsonCodec;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleSnapshotJsonCodecTest {

    private final RoleSnapshotJsonCodec codec = new RoleSnapshotJsonCodec(JsonMapper.builder().build());

    @Test
    void decodesLegacyCommaDelimitedSnapshotAndCanonicalizesRoles() {
        assertThat(codec.decode("FARM_MANAGER,AGRONOMIST,FARM_MANAGER"))
                .containsExactly("AGRONOMIST", "FARM_MANAGER");
        assertThat(codec.decode("")).isEmpty();
    }

    @Test
    void decodesJsonSnapshotWithSameCanonicalOrdering() {
        assertThat(codec.decode("[\"ROLE_FARM_MANAGER\",\"AGRONOMIST\"]"))
                .containsExactly("AGRONOMIST", "FARM_MANAGER");
        assertThat(codec.encode(List.of("AGRONOMIST", "FARM_MANAGER")))
                .isEqualTo("[\"AGRONOMIST\",\"FARM_MANAGER\"]");
    }
}
