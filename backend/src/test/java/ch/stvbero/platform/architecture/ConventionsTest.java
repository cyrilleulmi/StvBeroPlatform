package ch.stvbero.platform.architecture;

import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import static ch.stvbero.platform.architecture.ModulesTest.PRODUCTION;
import static ch.stvbero.platform.architecture.ModulesTest.ROOT;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Fitness functions for the conventions inside a module (docs/technology.md, docs/architecture.md): "services
 * load, rules decide, resources stay thin", and the outside world sits behind its own classes. They keep the
 * domain logic testable in milliseconds, without database and without HTTP.
 */
class ConventionsTest {

    @Test
    void rulesDecideWithoutDatabaseAndWithoutFramework() {
        ArchRule rule = noClasses().that().haveSimpleNameEndingWith("Rules")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.persistence..", "jakarta.transaction..", "jakarta.ws.rs..", "jakarta.enterprise..",
                        "jakarta.inject..", "io.quarkus..", "org.hibernate..", "java.sql..", "java.net.http..")
                .because("a …Rules class is pure domain logic with unit tests: the service loads the data and"
                        + " hands it over");
        rule.check(PRODUCTION);
    }

    @Test
    void restEndpointsLiveInResources() {
        ArchRule rule = classes().that().areAnnotatedWith(Path.class)
                .should().haveSimpleNameEndingWith("Resource")
                .because("the REST layer is found by its name");
        rule.check(PRODUCTION);
    }

    @Test
    void resourcesStayThin() {
        ArchRule rule = noClasses().that().haveSimpleNameEndingWith("Resource")
                .and().resideOutsideOfPackage(ROOT + ".testsupport..")
                .should().dependOnClassesThat().areAssignableTo(EntityManager.class)
                .because("a resource translates HTTP and calls a service; loading and storing is the service's job");
        rule.check(PRODUCTION);
    }

    @Test
    void onlyTheRestLayerKnowsHttp() {
        ArchRule rule = noClasses().that().resideOutsideOfPackages(ROOT + ".core.auth..", ROOT + ".core.error..",
                        ROOT + ".core.web..")
                .and().haveSimpleNameNotEndingWith("Resource")
                .and().haveSimpleNameNotEndingWith("RequestGrant")
                .should().dependOnClassesThat().resideInAPackage("jakarta.ws.rs..")
                .because("services and rules work without a request; sign-in, error mapping and routing of the core"
                        + " are the exceptions");
        rule.check(PRODUCTION);
    }

    @Test
    void theOutsideWorldSitsBehindItsOwnClasses() {
        ArchRule rule = noClasses().that().haveSimpleNameNotEndingWith("Api")
                .and().haveSimpleNameNotEndingWith("Transport")
                .should().dependOnClassesThat().resideInAPackage("java.net.http..")
                .because("requests to other systems (swiss unihockey, routing, push) go through an …Api or"
                        + " …Transport class behind an interface, so a simulator can replace them (docs/technology.md)");
        rule.check(PRODUCTION);
    }

    @Test
    void nowComesFromTheBackendClock() {
        // The adapters to the outside world compare with real time (age of a cached file, timeouts)
        ArchRule rule = noClasses().that().resideOutsideOfPackage(ROOT + ".core.clock..")
                .and().haveSimpleNameNotEndingWith("Api")
                .should().callMethod(java.time.Instant.class, "now")
                .orShould().callMethod(java.time.LocalDate.class, "now")
                .orShould().callMethod(java.time.LocalDateTime.class, "now")
                .orShould().callMethod(java.time.ZonedDateTime.class, "now")
                .orShould().callMethod(java.time.OffsetDateTime.class, "now")
                .because("the time comes from the BackendClock, so the tests can set it (docs/technology.md)");
        rule.check(PRODUCTION);
    }
}
