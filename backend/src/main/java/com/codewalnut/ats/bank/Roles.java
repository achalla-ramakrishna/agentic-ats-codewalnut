package com.codewalnut.ats.bank;

import com.codewalnut.ats.domain.Assessment.Category;
import com.codewalnut.ats.domain.BankQuestion.Section;
import com.codewalnut.ats.dto.BankDtos.Preset;
import com.codewalnut.ats.dto.BankDtos.SectionPlan;
import java.util.ArrayList;
import java.util.List;

/**
 * Role tests (ADR-0015): a role (e.g. Java backend developer) at a level (fresher … lead) becomes a
 * ready-made paper mixing the right areas and bands — the main stack, the stacks it works with,
 * CS fundamentals, system design for seniors and aptitude for freshers. A test checks every mix
 * can be built from the built-in bank.
 */
public final class Roles {

    /** Experience levels. Questions shift from fundamentals to advanced and design as they rise. */
    public enum Level {
        FRESHER("Fresher / intern", "0–1 years", 40, 50),
        JUNIOR("Junior", "1–3 years", 35, 60),
        MID("Mid-level", "3–5 years", 40, 60),
        SENIOR("Senior", "5–8 years", 45, 60),
        LEAD("Lead / architect", "8+ years", 45, 60);

        public final String label;
        public final String years;
        final int minutes;
        final int pass;

        Level(String label, String years, int minutes, int pass) {
            this.label = label;
            this.years = years;
            this.minutes = minutes;
            this.pass = pass;
        }
    }

    /** primary: the role's main stack; others: what it works with. levels: which levels apply. */
    public record Role(String id, String name, String summary, Category primary, List<Category> others, List<Level> levels) {

        public List<Category> areas() {
            List<Category> all = new ArrayList<>(List.of(primary));
            all.addAll(others);
            return all;
        }
    }

    private static final List<Level> ALL = List.of(Level.values());

    public static final List<Role> ROLES = List.of(
            new Role("java-backend", "Java backend developer", "Java and Spring Boot services with SQL databases.", Category.JAVA,
                    List.of(Category.SQL), ALL),
            new Role("python-backend", "Python backend developer", "Python services (Django, Flask or FastAPI) with SQL databases.",
                    Category.PYTHON, List.of(Category.SQL), ALL),
            new Role("react-frontend", "React frontend developer", "React apps in JavaScript or TypeScript.", Category.REACT,
                    List.of(Category.JAVASCRIPT), ALL),
            new Role("angular-frontend", "Angular frontend developer", "Angular apps in TypeScript.", Category.ANGULAR,
                    List.of(Category.JAVASCRIPT), ALL),
            new Role("fullstack-java-react", "Full-stack developer (Java + React)", "Spring Boot back end, React front end, SQL.",
                    Category.JAVA, List.of(Category.REACT, Category.SQL), ALL),
            new Role("fullstack-java-angular", "Full-stack developer (Java + Angular)", "Spring Boot back end, Angular front end, SQL.",
                    Category.JAVA, List.of(Category.ANGULAR, Category.SQL), ALL),
            new Role("fullstack-python-react", "Full-stack developer (Python + React)", "Python back end, React front end, SQL.",
                    Category.PYTHON, List.of(Category.REACT, Category.SQL), ALL),
            new Role("sql-developer", "Database / SQL developer", "Queries, data modelling, tuning and transactions.", Category.SQL,
                    List.of(), ALL),
            new Role("graduate-trainee", "Graduate / fresher trainee", "Any stack: aptitude and computer-science basics.",
                    Category.CS_FUNDAMENTALS, List.of(), List.of(Level.FRESHER)));

    private Roles() {}

    public static Role role(String id) {
        return ROLES.stream().filter(r -> r.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("role: unknown role " + id));
    }

    /** The paper for a role at a level. */
    public static Preset preset(Role role, Level level) {
        List<SectionPlan> plan = role.id().equals("graduate-trainee") ? graduate() : mix(role, level);
        int total = plan.stream().mapToInt(p -> p.easy() + p.medium() + p.hard()).sum();
        List<Category> areas = new ArrayList<>(role.areas());
        plan.stream().map(SectionPlan::area).filter(a -> !areas.contains(a)).distinct().forEach(areas::add);
        return new Preset(role.id() + "-" + level.name().toLowerCase(), role.name() + " — " + level.label,
                total + " questions in " + level.minutes + " minutes for " + level.years + ": "
                        + String.join(", ", areas.stream().map(Presets::areaName).toList()) + ".",
                level.minutes, level.pass, plan);
    }

    private static List<SectionPlan> mix(Role role, Level level) {
        List<SectionPlan> out = new ArrayList<>();
        Category p = role.primary();
        List<Category> others = role.others();
        boolean two = others.size() > 1;
        switch (level) {
            case FRESHER -> {
                out.add(plan(Category.APTITUDE, Section.QUANT, 2, 2, 0));
                out.add(plan(Category.APTITUDE, Section.LOGICAL, 2, 2, 0));
                out.add(plan(Category.APTITUDE, Section.VERBAL, 2, 0, 0));
                out.add(plan(p, Section.FUNDAMENTALS, 4, 3, 1));
                others.forEach(o -> out.add(plan(o, Section.FUNDAMENTALS, 2, 1, 0)));
                out.add(plan(Category.CS_FUNDAMENTALS, Section.FUNDAMENTALS, 2, 2, 0));
            }
            case JUNIOR -> {
                out.add(plan(p, Section.FUNDAMENTALS, 1, 2, 1));
                out.add(plan(p, Section.PRACTICAL, 3, 4, 1));
                others.forEach(o -> out.add(two ? plan(o, Section.PRACTICAL, 1, 1, 1) : plan(o, Section.PRACTICAL, 1, 2, 1)));
                out.add(plan(Category.CS_FUNDAMENTALS, Section.PRACTICAL, 1, 2, 0));
            }
            case MID -> {
                out.add(plan(p, Section.PRACTICAL, 0, 3, 2));
                out.add(plan(p, Section.ADVANCED, 1, 3, 2));
                others.forEach(o -> {
                    out.add(two ? plan(o, Section.PRACTICAL, 0, 1, 1) : plan(o, Section.PRACTICAL, 0, 2, 1));
                    out.add(plan(o, Section.ADVANCED, 0, 1, 1));
                });
                out.add(plan(Category.SYSTEM_DESIGN, Section.FUNDAMENTALS, 1, 2, 0));
                if (others.isEmpty()) {
                    out.add(plan(Category.CS_FUNDAMENTALS, Section.PRACTICAL, 0, 2, 1));
                }
            }
            case SENIOR -> {
                out.add(plan(p, Section.PRACTICAL, 0, 2, 2));
                out.add(plan(p, Section.ADVANCED, 0, 3, 4));
                others.forEach(o -> out.add(two ? plan(o, Section.ADVANCED, 0, 1, 2) : plan(o, Section.ADVANCED, 0, 2, 2)));
                out.add(plan(Category.SYSTEM_DESIGN, Section.PRACTICAL, 0, 2, 2));
                out.add(plan(Category.SYSTEM_DESIGN, Section.ADVANCED, 0, 1, 1));
                out.add(plan(Category.CS_FUNDAMENTALS, Section.ADVANCED, 0, 1, 1));
            }
            case LEAD -> {
                out.add(plan(p, Section.ADVANCED, 0, 2, 4));
                others.forEach(o -> out.add(two ? plan(o, Section.ADVANCED, 0, 1, 1) : plan(o, Section.ADVANCED, 0, 1, 2)));
                out.add(plan(Category.SYSTEM_DESIGN, Section.PRACTICAL, 0, 2, 2));
                out.add(plan(Category.SYSTEM_DESIGN, Section.ADVANCED, 0, 3, 3));
                out.add(plan(Category.CS_FUNDAMENTALS, Section.ADVANCED, 0, 2, 1));
            }
            default -> throw new IllegalStateException("level " + level);
        }
        return out;
    }

    private static List<SectionPlan> graduate() {
        return List.of(plan(Category.APTITUDE, Section.QUANT, 4, 3, 1), plan(Category.APTITUDE, Section.LOGICAL, 4, 3, 1),
                plan(Category.APTITUDE, Section.VERBAL, 3, 2, 0), plan(Category.CS_FUNDAMENTALS, Section.FUNDAMENTALS, 4, 3, 1),
                plan(Category.CS_FUNDAMENTALS, Section.PRACTICAL, 1, 1, 0));
    }

    private static SectionPlan plan(Category area, Section section, int easy, int medium, int hard) {
        return new SectionPlan(area, section, easy, medium, hard);
    }
}
