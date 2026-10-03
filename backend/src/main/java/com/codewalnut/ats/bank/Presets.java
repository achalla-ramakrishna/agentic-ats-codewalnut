package com.codewalnut.ats.bank;

import com.codewalnut.ats.domain.BankQuestion.Section;
import com.codewalnut.ats.dto.BankDtos.Preset;
import com.codewalnut.ats.dto.BankDtos.SectionPlan;
import java.util.List;

/**
 * Ready-made aptitude blueprints modelled on published campus-test patterns (2025–26). The real
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
}
