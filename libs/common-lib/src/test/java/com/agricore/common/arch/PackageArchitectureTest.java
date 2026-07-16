package com.agricore.common.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Lightweight architecture guard for shared technical library.
 * Service-level hexagonal rules can mirror this pattern per module.
 */
class PackageArchitectureTest {

    @Test
    void commonLib_shouldNotDependOnSpringFramework() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.agricore.common");
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.agricore.common..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");
        rule.check(classes);
    }
}
