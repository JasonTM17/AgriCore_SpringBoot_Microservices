package com.agricore.iot;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IotTimescaleExistingVolumeIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
    private static final DockerImageName TIMESCALE_IMAGE = DockerImageName.parse(
            "timescale/timescaledb:2.27.0-pg16"
                    + "@sha256:51eb3bcdfc41f481c797026813d9d457fb5cbc8ea370a65640d8cda13a4040c1");
    private static final String DATA_DIRECTORY = "/var/lib/postgresql/data";

    @Test
    void vanillaPg16VolumeUpgradesAndProvisionerSurvivesMissingIotDatabaseRace() throws Exception {
        String volumeName = "agricore-timescale-upgrade-" + UUID.randomUUID();
        var docker = DockerClientFactory.instance().client();
        docker.createVolumeCmd().withName(volumeName).exec();

        GenericContainer<?> vanilla = null;
        GenericContainer<?> upgraded = null;
        try {
            vanilla = postgresContainer(POSTGRES_IMAGE, volumeName)
                    .waitingFor(Wait.forLogMessage(
                            ".*database system is ready to accept connections.*\\n", 2))
                    .withStartupTimeout(Duration.ofSeconds(60));
            vanilla.start();
            assertSuccessful(vanilla, "psql", "-U", "agricore", "-d", "postgres",
                    "-c", "CREATE DATABASE agricore_identity");
            vanilla.stop();
            vanilla = null;

            upgraded = postgresContainer(TIMESCALE_IMAGE, volumeName)
                    .withCommand(
                            "postgres",
                            "-c", "max_connections=200",
                            "-c", "shared_preload_libraries=timescaledb"
                    )
                    .waitingFor(Wait.forLogMessage(
                            ".*database system is ready to accept connections.*\\n", 1))
                    .withStartupTimeout(Duration.ofSeconds(60));
            upgraded.start();

            assertThat(query(upgraded, "postgres", "SHOW shared_preload_libraries"))
                    .contains("timescaledb");
            upgraded.copyFileToContainer(
                    MountableFile.forHostPath(provisioningScript()),
                    "/tmp/provision-iot-timescale.sh"
            );
            assertSuccessful(upgraded, "sh", "-c", """
                    PGHOST=/var/run/postgresql PGUSER=agricore \
                    IOT_TIMESCALE_INIT_MAX_ATTEMPTS=20 IOT_TIMESCALE_INIT_RETRY_SECONDS=1 \
                    sh /tmp/provision-iot-timescale.sh >/tmp/provision.log 2>&1 &
                    """);

            Thread.sleep(1_500L);
            assertSuccessful(upgraded, "psql", "-U", "agricore", "-d", "postgres",
                    "-c", "CREATE DATABASE agricore_iot");
            awaitExtension(upgraded);

            assertThat(query(upgraded, "postgres", extensionCountSql())).isEqualTo("0");
            assertThat(query(upgraded, "template1", extensionCountSql())).isEqualTo("0");
            assertThat(query(upgraded, "agricore_identity", extensionCountSql())).isEqualTo("0");
            assertThat(query(upgraded, "agricore_iot", extensionCountSql())).isEqualTo("1");
            assertThat(exec(upgraded, "sh", "-c", "cat /tmp/provision.log").getStdout())
                    .contains("Waiting for agricore_iot Timescale initialization")
                    .contains("is ready in agricore_iot");
        } finally {
            if (upgraded != null) {
                upgraded.stop();
            }
            if (vanilla != null) {
                vanilla.stop();
            }
            docker.removeVolumeCmd(volumeName).exec();
        }
    }

    private static GenericContainer<?> postgresContainer(DockerImageName image, String volumeName) {
        return new GenericContainer<>(image)
                .withEnv("POSTGRES_USER", "agricore")
                .withEnv("POSTGRES_PASSWORD", "agricore-test")
                .withEnv("POSTGRES_DB", "postgres")
                .withCreateContainerCmdModifier(command -> command.getHostConfig()
                        .withBinds(new Bind(volumeName, new Volume(DATA_DIRECTORY))));
    }

    private static Path provisioningScript() {
        String root = System.getProperty("maven.multiModuleProjectDirectory", "../..");
        return Path.of(root, "infrastructure", "docker", "postgres", "provision-iot-timescale.sh")
                .toAbsolutePath()
                .normalize();
    }

    private static void awaitExtension(GenericContainer<?> container) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            if ("1".equals(query(container, "agricore_iot", extensionCountSql()))) {
                return;
            }
            Thread.sleep(250L);
        }
        throw new AssertionError("TimescaleDB extension was not provisioned after database creation");
    }

    private static String query(GenericContainer<?> container, String database, String sql)
            throws Exception {
        var result = exec(container, "psql", "-U", "agricore", "-d", database, "-Atc", sql);
        return result.getExitCode() == 0 ? result.getStdout().trim() : "";
    }

    private static String extensionCountSql() {
        return "SELECT count(*) FROM pg_extension WHERE extname = 'timescaledb'";
    }

    private static void assertSuccessful(GenericContainer<?> container, String... command)
            throws Exception {
        var result = exec(container, command);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    private static org.testcontainers.containers.Container.ExecResult exec(
            GenericContainer<?> container,
            String... command
    ) throws Exception {
        return container.execInContainer(command);
    }
}
