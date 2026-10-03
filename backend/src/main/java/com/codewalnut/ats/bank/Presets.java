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
            new Preset("infosys", "Infosys style", "Quantitative 15, Logical 15, Verbal 10 — 40 questions in 65 minutes.", 65, 65,
                    List.of(new SectionPlan(Section.QUANT, 6, 6, 3), new SectionPlan(Section.LOGICAL, 6, 6, 3),
                            new SectionPlan(Section.VERBAL, 4, 4, 2))));

    private static final java.util.Map<Category, String> NAMES = java.util.Map.of(Category.JAVA, "Java", Category.PYTHON, "Python",
            Category.JAVASCRIPT, "JavaScript", Category.REACT, "React", Category.ANGULAR, "Angular", Category.SQL, "SQL",
            Category.CS_FUNDAMENTALS, "CS fundamentals", Category.SYSTEM_DESIGN, "System design");

    /** The display name of an area, e.g. "Java", "CS fundamentals". */
    public static String areaName(Category area) {
        return area == Category.APTITUDE ? "Aptitude" : NAMES.getOrDefault(area, area.name());
    }

    /** Aptitude patterns, or for a technical area: freshers, 1–3 years and 3+ years papers. */
    public static List<Preset> forArea(Category area) {
        if (area == Category.APTITUDE) {
            return APTITUDE;
        }
        String name = areaName(area);
        String id = area.name().toLowerCase();
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
