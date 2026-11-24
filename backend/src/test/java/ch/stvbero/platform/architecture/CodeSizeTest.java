package ch.stvbero.platform.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fitness function for the size of the classes (docs/architecture.md): a class that keeps growing collects
 * responsibilities, is slow to read for people and agents, and is where parallel changes collide. New classes stay
 * below {@link #LIMIT} lines. The classes that were bigger when the limit came are frozen at their size: they may
 * shrink, never grow.
 */
class CodeSizeTest {

    static final int LIMIT = 400;

    /**
     * Frozen: file name → lines when the limit was introduced (E-106). Entries may only be lowered or removed, never
     * raised or added. To get below a limit, split the class along its responsibilities.
     */
    static final Map<String, Integer> FROZEN = Map.of(
            "driver/DriverListService.java", 707,
            "testsupport/Seed.java", 691,
            "testsupport/ClubSeed.java", 661,
            "driver/DriverPlanService.java", 623,
            "attendance/event/SeriesService.java", 456,
            "swissunihockey/SuSync.java", 424);

    @Test
    void classesStayReadable() throws IOException {
        Path root = Path.of("src/main/java/ch/stvbero/platform");
        Map<String, Integer> lines = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                try (Stream<String> content = Files.lines(file)) {
                    lines.put(root.relativize(file).toString().replace('\\', '/'), (int) content.count());
                }
            }
        }
        List<String> problems = new ArrayList<>();
        lines.forEach((file, count) -> {
            Integer frozen = FROZEN.get(file);
            if (frozen == null && count > LIMIT) {
                problems.add(file + " has " + count + " lines, the limit is " + LIMIT + ": split it");
            } else if (frozen != null && count > frozen) {
                problems.add(file + " grew from " + frozen + " to " + count + " lines: it is frozen, move the new"
                        + " code into a class of its own");
            }
        });
        FROZEN.forEach((file, frozen) -> {
            Integer count = lines.get(file);
            if (count == null || count <= LIMIT) {
                problems.add(file + " no longer needs its entry in FROZEN: remove it");
            }
        });
        assertEquals(List.of(), problems);
    }
}
