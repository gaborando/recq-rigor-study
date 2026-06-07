package conformance;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Evaluates CQRS-discipline rules against a generated app's compiled classes.
 * Output: one JSON object on stdout:
 *   {"rules":[{"rule":..., "result":"pass|violation|inconclusive",
 *              "violations":N, "detail":...}, ...]}
 *
 * Rules are arm-neutral (the layered packages are mandated by the spec for
 * every arm) plus arm-specific refinements keyed on framework annotations,
 * matched BY NAME so no framework jar is needed on this classpath.
 */
public final class Main {

    record RuleOutcome(String rule, String result, int violations, String detail) {}

    static final String EVENTO_GATEWAY_PKG = "com.evento.application.proxy..";
    static final String AXON_CMD_GATEWAY = "org.axonframework.commandhandling..";

    public static void main(String[] args) throws Exception {
        Path classes = Path.of(System.getProperty("conformance.classes"));
        String arm = System.getProperty("conformance.arm", "ARM_B");
        if (!Files.exists(classes)) {
            System.out.println("{\"error\":\"classes path not found\",\"rules\":[]}");
            return;
        }
        JavaClasses imported = new ClassFileImporter().importPath(classes);
        List<RuleOutcome> outcomes = new ArrayList<>();

        // --- arm-neutral CQRS discipline (mandated layer packages) ---
        outcomes.add(evaluate(imported, "command-side-independent-of-query-side",
                "..command..",
                noClasses().that().resideInAPackage("..command..")
                        .should().dependOnClassesThat().resideInAPackage("..query..")));

        outcomes.add(evaluate(imported, "query-side-never-reaches-command-side",
                "..query..",
                noClasses().that().resideInAPackage("..query..")
                        .should().dependOnClassesThat().resideInAPackage("..command..")));

        outcomes.add(evaluate(imported, "web-layer-free-of-persistence",
                "..web..",
                noClasses().that().resideInAPackage("..web..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")));

        outcomes.add(evaluate(imported, "no-cycles-between-layers", "com.study.app.",
                slices().matching("com.study.app.(*)..").should().beFreeOfCycles()
                        .allowEmptyShould(true)));

        // --- arm-specific refinements (annotation names, no framework deps) ---
        switch (arm) {
            case "ARM_A" -> {
                outcomes.add(evaluate(imported, "evento-read-side-never-sends-commands",
                        annotatedAny(imported,
                                "com.evento.common.modeling.annotations.component.Projection",
                                "com.evento.common.modeling.annotations.component.Projector"),
                        noClasses().that(annotatedWithAny(
                                        "com.evento.common.modeling.annotations.component.Projection",
                                        "com.evento.common.modeling.annotations.component.Projector"))
                                .should().dependOnClassesThat().resideInAPackage(EVENTO_GATEWAY_PKG)));
                outcomes.add(evaluate(imported, "evento-aggregate-independent-of-query-side",
                        annotatedAny(imported,
                                "com.evento.common.modeling.annotations.component.Aggregate"),
                        noClasses().that(annotatedWithAny(
                                        "com.evento.common.modeling.annotations.component.Aggregate"))
                                .should().dependOnClassesThat().resideInAPackage("..query..")));
            }
            case "ARM_C" -> {
                outcomes.add(evaluate(imported, "axon-aggregate-independent-of-query-side",
                        annotatedAny(imported, "org.axonframework.spring.stereotype.Aggregate"),
                        noClasses().that(annotatedWithAny(
                                        "org.axonframework.spring.stereotype.Aggregate"))
                                .should().dependOnClassesThat().resideInAPackage("..query..")));
                outcomes.add(evaluate(imported, "axon-query-side-never-sends-commands",
                        "..query..",
                        noClasses().that().resideInAPackage("..query..")
                                .should().dependOnClassesThat().resideInAPackage(AXON_CMD_GATEWAY)));
            }
            default -> { /* ARM_B: neutral rules only */ }
        }

        StringBuilder sb = new StringBuilder("{\"rules\":[");
        for (int i = 0; i < outcomes.size(); i++) {
            RuleOutcome o = outcomes.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"rule\":\"").append(o.rule)
              .append("\",\"result\":\"").append(o.result)
              .append("\",\"violations\":").append(o.violations)
              .append(",\"detail\":\"").append(escape(o.detail)).append("\"}");
        }
        sb.append("]}");
        System.out.println(sb);
    }

    @SafeVarargs
    static DescribedPredicate<JavaClass> annotatedWithAny(String... names) {
        DescribedPredicate<JavaClass> p = DescribedPredicate.alwaysFalse();
        for (String n : names) p = p.or(annotatedWith(n));
        return p.as("annotated with any of given component annotations");
    }

    /** inconclusive-guard: does any class match the scope? */
    static boolean annotatedAny(JavaClasses classes, String... names) {
        for (JavaClass c : classes) for (String n : names)
            if (c.isAnnotatedWith(n)) return true;
        return false;
    }

    static RuleOutcome evaluate(JavaClasses classes, String name, Object scope, ArchRule rule) {
        boolean scopeNonEmpty;
        if (scope instanceof Boolean b) {
            scopeNonEmpty = b;
        } else {
            String pkg = scope.toString();
            scopeNonEmpty = classes.stream().anyMatch(
                    pkg.endsWith(".") ? c -> c.getName().startsWith(pkg)
                                      : resideInAPackage(pkg)::test);
        }
        if (!scopeNonEmpty) {
            return new RuleOutcome(name, "inconclusive", 0,
                    "scope empty: mandated structure absent");
        }
        EvaluationResult res = rule.allowEmptyShould(true).evaluate(classes);
        List<String> details = res.getFailureReport().getDetails();
        if (details.isEmpty()) return new RuleOutcome(name, "pass", 0, "");
        String first = details.get(0);
        return new RuleOutcome(name, "violation", details.size(),
                first.substring(0, Math.min(first.length(), 200)));
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ").replace("\t", " ");
    }

    private Main() {}
}
