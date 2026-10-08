package com.codewalnut.ats.bank;

import com.codewalnut.ats.domain.Assessment.Category;
import com.codewalnut.ats.domain.BankQuestion.Section;
import com.codewalnut.ats.dto.BankDtos.Preset;
import com.codewalnut.ats.dto.BankDtos.SectionPlan;
import java.util.List;

/**
 * Ready-made blueprints. Aptitude ones are modelled on published campus-test patterns (2025–26). The real
 * tests time each section separately; ours use one timer for the whole paper.
 */
public final class Presets {

    private Presets() {}

    public static final List<Preset> APTITUDE = List.of(
            new Preset("quick", "Quick screening", "20 questions in 25 minutes: a first filter right after someone applies.", 25, 50,
                    List.of(new SectionPlan(Section.QUANT, 3, 3, 2), new SectionPlan(Section.LOGICAL, 3, 3, 2),
                            new SectionPlan(Section.VERBAL, 2, 2, 0))),
            new Preset("tcs-nqt", "TCS NQT style (Foundation)", "Numerical 20, Verbal 25, Reasoning 20 — 65 questions in 75 minutes.", 75, 60,
                    List.of(new SectionPlan(Section.QUANT, 8, 8, 4), new SectionPlan(Section.VERBAL, 10, 10, 5),
                            new SectionPlan(Section.LOGICAL, 8, 8, 4))),
            new Preset("wipro-nlth", "Wipro NLTH style", "Quantitative 20, Logical 20, Verbal 20 — 60 questions in 75 minutes.", 75, 60,
                    List.of(new SectionPlan(Section.QUANT, 8, 8, 4), new SectionPlan(Section.LOGICAL, 8, 8, 4),
                            new SectionPlan(Section.VERBAL, 8, 8, 4))),
            new Preset("cognizant-genc", "Cognizant GenC style", "Numerical 18, Reasoning 18, Verbal 18 — 54 questions in 85 minutes.", 85, 65,
                    List.of(new SectionPlan(Section.QUANT, 7, 7, 4), new SectionPlan(Section.LOGICAL, 7, 7, 4),
                            new SectionPlan(Section.VERBAL, 7, 7, 4))),
            new Preset("se-intern", "Software engineering intern — aptitude",
                    "30 questions in 40 minutes: numerical 11, logical 11, verbal 8, mostly easy and medium. A first filter for any stack.",
                    40, 60, List.of(new SectionPlan(Section.QUANT, 5, 4, 2), new SectionPlan(Section.LOGICAL, 5, 4, 2),
                            new SectionPlan(Section.VERBAL, 4, 3, 1))),
            new Preset("infosys", "Infosys style", "Quantitative 15, Logical 15, Verbal 10 — 40 questions in 65 minutes.", 65, 65,
                    List.of(new SectionPlan(Section.QUANT, 6, 6, 3), new SectionPlan(Section.LOGICAL, 6, 6, 3),
                            new SectionPlan(Section.VERBAL, 4, 4, 2))));

    private static final java.util.Map<Category, String> NAMES = java.util.Map.ofEntries(
            java.util.Map.entry(Category.JAVA, "Java"), java.util.Map.entry(Category.PYTHON, "Python"),
            java.util.Map.entry(Category.JAVASCRIPT, "JavaScript"), java.util.Map.entry(Category.REACT, "React"),
            java.util.Map.entry(Category.ANGULAR, "Angular"), java.util.Map.entry(Category.SQL, "SQL"),
            java.util.Map.entry(Category.CS_FUNDAMENTALS, "CS fundamentals"), java.util.Map.entry(Category.SYSTEM_DESIGN, "System design"),
            java.util.Map.entry(Category.NODEJS, "Node.js"), java.util.Map.entry(Category.QA_AUTOMATION, "Testing & QA automation"),
            java.util.Map.entry(Category.DEVOPS, "DevOps & cloud"), java.util.Map.entry(Category.DATA_ANALYTICS, "Data analytics"),
            java.util.Map.entry(Category.DSA, "Data structures & algorithms"), java.util.Map.entry(Category.CODING, "Coding"),
            java.util.Map.entry(Category.WEB_API, "Web, APIs & tools"));

    /** Coding papers: a few problems, with time to think, write and test (ADR-0016). */
    private static final List<Preset> CODING = List.of(
            new Preset("coding-fresher", "Coding — freshers", "2 easy problems in 45 minutes: basics, strings and arrays.", 45, 50,
                    List.of(new SectionPlan(Section.FUNDAMENTALS, 2, 0, 0))),
            new Preset("coding-mid", "Coding — 1 to 3 years", "2 problems in 60 minutes: one easy, one medium on core data structures.", 60, 50,
                    List.of(new SectionPlan(Section.FUNDAMENTALS, 0, 1, 0), new SectionPlan(Section.PRACTICAL, 0, 1, 0))),
            new Preset("coding-senior", "Coding — 3+ years", "3 problems in 90 minutes: data structures, then a harder algorithm problem.", 90, 50,
                    List.of(new SectionPlan(Section.PRACTICAL, 0, 1, 0), new SectionPlan(Section.ADVANCED, 0, 1, 1))));

    /** The display name of an area, e.g. "Java", "CS fundamentals". */
    public static String areaName(Category area) {
        return area == Category.APTITUDE ? "Aptitude" : NAMES.getOrDefault(area, area.name());
    }

    /** Aptitude patterns, or for a technical area: freshers, 1–3 years and 3+ years papers. */
    public static List<Preset> forArea(Category area) {
        if (area == Category.APTITUDE) {
            return APTITUDE;
        }
        if (area == Category.CODING) {
            return CODING;
        }
        String name = areaName(area);
        String id = area.name().toLowerCase();
        if (area == Category.WEB_API) {
            // A freshers' area: HTTP/REST, HTML/CSS and Git/Docker/Kubernetes basics.
            return List.of(new Preset(id + "-fresher", name + " — freshers", "20 questions in 25 minutes: REST and HTTP, HTML/CSS, Git and Docker.",
                    25, 60, List.of(new SectionPlan(Section.FUNDAMENTALS, 8, 8, 4))));
        }
        return List.of(
                new Preset(id + "-fresher", name + " — freshers", "20 fundamentals questions in 30 minutes, mostly easy and medium.", 30, 60,
                        List.of(new SectionPlan(Section.FUNDAMENTALS, 8, 8, 4))),
                new Preset(id + "-mid", name + " — 1 to 3 years", "25 questions in 40 minutes: a few fundamentals, mostly applied, some advanced.", 40, 60,
                        List.of(new SectionPlan(Section.FUNDAMENTALS, 0, 3, 2), new SectionPlan(Section.PRACTICAL, 4, 6, 4),
                                new SectionPlan(Section.ADVANCED, 0, 3, 3))),
                new Preset(id + "-senior", name + " — 3+ years", "25 questions in 40 minutes: applied and advanced, weighted to harder questions.", 40, 60,
                        List.of(new SectionPlan(Section.PRACTICAL, 0, 4, 4), new SectionPlan(Section.ADVANCED, 3, 7, 7))));
    }
}
