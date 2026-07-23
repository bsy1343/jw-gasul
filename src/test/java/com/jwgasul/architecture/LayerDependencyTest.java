// LayerDependencyTest.java — 레이어 의존성 규칙을 CI에서 강제(ARCHITECTURE-PATTERN-GUIDE 5장).
// Controller→Service→Repository 단방향만 허용. security/common/dto 등 횡단 관심사는 레이어 검증에서 제외.
package com.jwgasul.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LayerDependencyTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.jwgasul");
    }

    // 컨트롤러는 리포지토리에 직접 접근하지 않는다(반드시 서비스 경유)
    @Test
    void controllersShouldNotAccessRepositories() {
        classes().that().resideInAPackage("..controller..")
                .should().onlyDependOnClassesThat().resideOutsideOfPackage("..repository..")
                .check(classes);
    }

    // 레이어드 아키텍처 단방향 규칙. 레이어에 속하지 않는 패키지(security/common/dto) 사이 의존은 고려 제외.
    @Test
    void layeredArchitectureShouldBeRespected() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy("..controller..")
                .layer("Service").definedBy("..service..")
                .layer("Repository").definedBy("..repository..")
                .layer("Domain").definedBy("..domain..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                // Domain(엔티티/enum)은 전 레이어에서 참조 허용 → 접근 제한 없음
                .check(classes);
    }
}
