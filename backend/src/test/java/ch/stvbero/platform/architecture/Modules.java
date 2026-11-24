package ch.stvbero.platform.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The modules of {@code modules.json} in the root of the repository: the one place that says which module may
 * depend on which (docs/architecture.md). The frontend (ESLint) and {@code scripts/test-affected.sh} read the same
 * file.
 */
record Modules(Map<String, Module> byName) {

    /**
     * @param backendPackage   package below {@code ch.stvbero.platform}, {@code null} for a module without backend
     * @param backendDependsOn names of the modules whose backend this module may use
     * @param frontendDependsOn names of the modules whose frontend this module may import
     */
    record Module(String name, String backendPackage, Set<String> backendDependsOn, Set<String> frontendDependsOn) {
    }

    static Modules load() {
        Path file = find();
        try {
            JsonNode root = new ObjectMapper().readTree(file.toFile());
            Map<String, Module> modules = new LinkedHashMap<>();
            root.get("modules").fields().forEachRemaining(entry -> {
                JsonNode backend = entry.getValue().path("backend");
                JsonNode frontend = entry.getValue().path("frontend");
                modules.put(entry.getKey(), new Module(entry.getKey(),
                        backend.hasNonNull("package") ? backend.get("package").asText() : null,
                        names(backend.path("dependsOn")), names(frontend.path("dependsOn"))));
            });
            return new Modules(modules);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The module that owns a backend package, by the first package below {@code ch.stvbero.platform}. */
    Module ofBackendPackage(String topLevelPackage) {
        return byName.values().stream().filter(m -> topLevelPackage.equals(m.backendPackage())).findFirst().orElse(null);
    }

    /** A cycle in the dependencies of one side as "a -> b -> a", or {@code null} if there is none. */
    String cycle(boolean backend) {
        for (String start : byName.keySet()) {
            List<String> path = cycleFrom(start, backend, new ArrayList<>(List.of(start)));
            if (path != null) {
                return String.join(" -> ", path);
            }
        }
        return null;
    }

    private List<String> cycleFrom(String module, boolean backend, List<String> path) {
        Module current = byName.get(module);
        if (current == null) {
            throw new IllegalStateException("modules.json names the unknown module '" + module + "'");
        }
        for (String next : backend ? current.backendDependsOn() : current.frontendDependsOn()) {
            if (path.contains(next)) {
                List<String> cycle = new ArrayList<>(path.subList(path.indexOf(next), path.size()));
                cycle.add(next);
                return cycle;
            }
            path.add(next);
            List<String> found = cycleFrom(next, backend, path);
            if (found != null) {
                return found;
            }
            path.remove(path.size() - 1);
        }
        return null;
    }

    private static Set<String> names(JsonNode array) {
        Set<String> names = new LinkedHashSet<>();
        array.forEach(node -> names.add(node.asText()));
        return names;
    }

    private static Path find() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path file = dir.resolve("modules.json");
            if (Files.isRegularFile(file)) {
                return file;
            }
        }
        throw new IllegalStateException("modules.json not found above " + Path.of("").toAbsolutePath());
    }
}
