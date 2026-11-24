package ch.stvbero.platform.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Fitness function for the module boundaries (docs/architecture.md): the backend may only depend the way
 * {@code modules.json} says. A new dependency between modules is a decision, so it needs a change of that file,
 * visible in the pull request. Works on the bytecode, so fully qualified names in the code count like imports.
 */
class ModulesTest {

    static final String ROOT = "ch.stvbero.platform";

    static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    private static final Modules MODULES = Modules.load();

    @Test
    void everyPackageBelongsToExactlyOneModule() {
        Set<String> inCode = new TreeSet<>();
        PRODUCTION.forEach(type -> inCode.add(topLevelPackage(type.getPackageName())));
        Set<String> inManifest = new TreeSet<>();
        MODULES.byName().values().stream().filter(m -> m.backendPackage() != null)
                .forEach(m -> inManifest.add(m.backendPackage()));
        assertEquals(inManifest, inCode,
                "The packages directly below " + ROOT + " are the modules of modules.json (backend.package)");
    }

    @Test
    void modulesDependOnlyOnTheModulesTheManifestAllows() {
        List<Executable> checks = new ArrayList<>();
        for (Modules.Module module : MODULES.byName().values()) {
            if (module.backendPackage() == null) {
                continue;
            }
            String[] forbidden = MODULES.byName().values().stream()
                    .filter(other -> other.backendPackage() != null && !other.equals(module)
                            && !module.backendDependsOn().contains(other.name()))
                    .map(other -> ROOT + "." + other.backendPackage() + "..")
                    .toArray(String[]::new);
            if (forbidden.length == 0) {
                continue;
            }
            ArchRule rule = noClasses().that().resideInAPackage(ROOT + "." + module.backendPackage() + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(forbidden)
                    .because("modules.json lets '" + module.name() + "' depend only on " + module.backendDependsOn()
                            + ". If the lower module needs something from the upper one, give the lower module an"
                            + " interface that the upper one implements (docs/architecture.md, extension points)");
            checks.add(() -> rule.check(PRODUCTION));
        }
        assertAll(checks);
    }

    @Test
    void theModuleGraphHasNoCycles() {
        assertNull(MODULES.cycle(true), "Cycle in backend.dependsOn of modules.json");
        assertNull(MODULES.cycle(false), "Cycle in frontend.dependsOn of modules.json");
    }

    private static String topLevelPackage(String packageName) {
        String rest = packageName.substring(ROOT.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
