package crudlivros_api.sistemasdestribuidos.routing;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import crudlivros_api.sistemasdestribuidos.config.OrchestratorProperties;

@Component
public class BackendRegistry {

    private static final Logger log = LoggerFactory.getLogger(BackendRegistry.class);

    private final List<BackendNode> nodes;
    private final int failureThreshold;
    private BackendNode leader;
    private Instant lastLeadershipChange;

    public BackendRegistry(OrchestratorProperties properties) {
        this.failureThreshold = properties.failureThreshold();
        this.nodes = createNodes(properties.backends());
    }

    public synchronized Optional<BackendNode> currentLeader() {
        if (leader != null && leader.isEligible()) {
            return Optional.of(leader);
        }
        return electLeader("líder ausente ou indisponível");
    }

    public synchronized Optional<BackendNode> alternativeTo(BackendNode failedNode) {
        return nodes.stream()
                .filter(node -> node != failedNode && node.isEligible())
                .min(Comparator.comparingInt(BackendNode::priority));
    }

    public synchronized void registerHealth(BackendNode node, BackendStatus status, String serviceName) {
        node.registerHealth(status, serviceName, Instant.now());
    }

    public synchronized void registerFailure(BackendNode node, String reason) {
        boolean unavailable = node.registerFailure(failureThreshold, Instant.now());
        if (unavailable && node == leader) {
            electLeader("falha do líder: " + reason);
        }
    }

    public synchronized void registerRequestSuccess(BackendNode node) {
        node.registerRequestSuccess();
    }

    public synchronized void markUnavailable(BackendNode node, String reason) {
        node.markUnavailable(Instant.now());
        if (node == leader) {
            electLeader(reason);
        }
    }

    public synchronized void promote(BackendNode node, String reason) {
        if (node.isEligible() && node != leader) {
            changeLeader(node, reason);
        }
    }

    public List<BackendNode> nodes() {
        return nodes;
    }

    public synchronized List<BackendSnapshot> snapshots() {
        return nodes.stream().map(node -> node.snapshot(node == leader)).toList();
    }

    public synchronized Instant lastLeadershipChange() {
        return lastLeadershipChange;
    }

    private Optional<BackendNode> electLeader(String reason) {
        Optional<BackendNode> candidate = nodes.stream()
                .filter(BackendNode::isEligible)
                .min(Comparator.comparingInt(BackendNode::priority));

        if (candidate.isPresent()) {
            changeLeader(candidate.get(), reason);
        } else if (leader != null) {
            log.warn("Nenhum backend elegível; líder {} removido ({})", leader.id(), reason);
            leader = null;
            lastLeadershipChange = Instant.now();
        }
        return candidate;
    }

    private void changeLeader(BackendNode newLeader, String reason) {
        BackendNode previous = leader;
        leader = newLeader;
        if (previous != newLeader) {
            lastLeadershipChange = Instant.now();
            log.warn("Troca de líder: {} -> {}. Motivo: {}",
                    previous == null ? "nenhum" : previous.id(), newLeader.id(), reason);
        }
    }

    private static List<BackendNode> createNodes(List<URI> uris) {
        List<BackendNode> result = new ArrayList<>();
        for (int index = 0; index < uris.size(); index++) {
            result.add(new BackendNode("backend-" + (index + 1), index, uris.get(index)));
        }
        return List.copyOf(result);
    }
}
