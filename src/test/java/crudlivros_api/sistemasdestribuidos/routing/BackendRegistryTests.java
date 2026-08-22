package crudlivros_api.sistemasdestribuidos.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import crudlivros_api.sistemasdestribuidos.config.OrchestratorProperties;

class BackendRegistryTests {

    private BackendRegistry registry;
    private BackendNode first;
    private BackendNode second;

    @BeforeEach
    void setUp() {
        OrchestratorProperties properties = new OrchestratorProperties(
                List.of(URI.create("http://backend-one:8000"), URI.create("http://backend-two:3000")),
                "/health",
                Duration.ofSeconds(3),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(3),
                3);
        registry = new BackendRegistry(properties);
        first = registry.nodes().get(0);
        second = registry.nodes().get(1);
    }

    @Test
    void electsFirstHealthyBackendByConfiguredPriority() {
        registry.registerHealth(second, BackendStatus.HEALTHY, "api-two");
        registry.registerHealth(first, BackendStatus.HEALTHY, "api-one");

        assertThat(registry.currentLeader()).contains(first);
    }

    @Test
    void promotesBackupAfterConfiguredNumberOfFailures() {
        registry.registerHealth(first, BackendStatus.HEALTHY, "api-one");
        registry.registerHealth(second, BackendStatus.HEALTHY, "api-two");
        assertThat(registry.currentLeader()).contains(first);

        registry.registerFailure(first, "erro 1");
        registry.registerFailure(first, "erro 2");
        assertThat(registry.currentLeader()).contains(first);

        registry.registerFailure(first, "erro 3");
        assertThat(registry.currentLeader()).contains(second);
    }

    @Test
    void recoveredBackendReturnsAsBackupWithoutPreemptingLeader() {
        registry.registerHealth(first, BackendStatus.HEALTHY, "api-one");
        registry.registerHealth(second, BackendStatus.HEALTHY, "api-two");
        assertThat(registry.currentLeader()).contains(first);
        registry.registerFailure(first, "erro 1");
        registry.registerFailure(first, "erro 2");
        registry.registerFailure(first, "erro 3");

        registry.registerHealth(first, BackendStatus.HEALTHY, "api-one");

        assertThat(registry.currentLeader()).contains(second);
        assertThat(registry.snapshots()).anySatisfy(snapshot -> {
            assertThat(snapshot.id()).isEqualTo("backend-1");
            assertThat(snapshot.leader()).isFalse();
            assertThat(snapshot.status()).isEqualTo(BackendStatus.HEALTHY);
        });
    }

    @Test
    void degradedBackendIsStillEligible() {
        registry.registerHealth(first, BackendStatus.DEGRADED, "api-one");

        assertThat(registry.currentLeader()).contains(first);
    }
}
