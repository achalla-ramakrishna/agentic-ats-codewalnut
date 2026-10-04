package com.codewalnut.ats.bank;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.domain.BankQuestion.Section;
import com.codewalnut.ats.dto.BankDtos.Preset;
import com.codewalnut.ats.dto.BankDtos.SectionPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Every role test at every level can be built from the built-in bank alone. */
class RolesTest {

    @Test
    void everyRoleAndLevelFitsInTheBuiltInBank() {
        List<Seed> all = new ArrayList<>(AptitudeBank.all());
        all.addAll(TechBank.all());
        all.addAll(CodingBank.all());
        Map<String, Long> available = all.stream()
                .collect(Collectors.groupingBy(s -> s.area() + "/" + s.section() + "/" + s.difficulty(), Collectors.counting()));
        for (Roles.Role role : Roles.ROLES) {
            for (Roles.Level level : role.levels()) {
                Preset p = Roles.preset(role, level);
                int total = 0;
                for (SectionPlan plan : p.sections()) {
                    assertThat(Section.forArea(plan.area())).as(p.name()).contains(plan.section());
                    int[] wanted = {plan.easy(), plan.medium(), plan.hard()};
                    String[] levels = {"EASY", "MEDIUM", "HARD"};
                    for (int d = 0; d < 3; d++) {
                        long have = available.getOrDefault(plan.area() + "/" + plan.section() + "/" + levels[d], 0L);
                        assertThat(have).as("%s: %s %s %s", p.name(), plan.area(), plan.section(), levels[d]).isGreaterThanOrEqualTo(wanted[d]);
                        total += wanted[d];
                    }
                }
                assertThat(total).as(p.name()).isBetween(15, 40);
                assertThat(p.sections().stream().map(SectionPlan::area)).as(p.name()).contains(role.primary());
            }
        }
    }

    @Test
    void levelsShiftFromFundamentalsToDesign() {
        Roles.Role java = Roles.role("java-backend");
        assertThat(Roles.preset(java, Roles.Level.FRESHER).sections()).anyMatch(s -> s.section() == Section.QUANT);
        assertThat(Roles.preset(java, Roles.Level.FRESHER).sections()).noneMatch(s -> s.section() == Section.ADVANCED);
        assertThat(Roles.preset(java, Roles.Level.LEAD).sections())
                .anyMatch(s -> s.area() == com.codewalnut.ats.domain.Assessment.Category.SYSTEM_DESIGN)
                .noneMatch(s -> s.section() == Section.FUNDAMENTALS);
        assertThat(Roles.preset(java, Roles.Level.MID).description()).contains("Java", "SQL", "System design");
    }

    /** ASMT-36: developer role tests include one coding problem (harder as the level rises) and more time for it. */
    @Test
    void developerRolesIncludeACodingProblem() {
        var coding = com.codewalnut.ats.domain.Assessment.Category.CODING;
        Roles.Role java = Roles.role("java-backend");
        assertThat(Roles.preset(java, Roles.Level.FRESHER).sections()).anyMatch(s -> s.area() == coding && s.section() == Section.FUNDAMENTALS && s.easy() == 1);
        assertThat(Roles.preset(java, Roles.Level.LEAD).sections()).anyMatch(s -> s.area() == coding && s.hard() == 1);
        assertThat(Roles.preset(java, Roles.Level.MID).durationMinutes()).isEqualTo(Roles.Level.MID.minutes + 25);
        assertThat(Roles.preset(Roles.role("sql-developer"), Roles.Level.MID).sections()).noneMatch(s -> s.area() == coding);
        assertThat(Roles.preset(Roles.role("data-analyst"), Roles.Level.JUNIOR).sections()).noneMatch(s -> s.area() == coding);
    }

    /** ASMT-38: freshers and juniors in developer roles get data-structures questions; graduates too. */
    @Test
    void freshersAndJuniorsGetDataStructures() {
        var dsa = com.codewalnut.ats.domain.Assessment.Category.DSA;
        Roles.Role java = Roles.role("java-backend");
        assertThat(Roles.preset(java, Roles.Level.FRESHER).sections()).anyMatch(s -> s.area() == dsa && s.section() == Section.FUNDAMENTALS);
        assertThat(Roles.preset(java, Roles.Level.JUNIOR).sections()).anyMatch(s -> s.area() == dsa && s.section() == Section.PRACTICAL);
        assertThat(Roles.preset(java, Roles.Level.SENIOR).sections()).noneMatch(s -> s.area() == dsa);
        assertThat(Roles.preset(Roles.role("graduate-trainee"), Roles.Level.FRESHER).sections()).anyMatch(s -> s.area() == dsa);
        assertThat(Roles.preset(Roles.role("sql-developer"), Roles.Level.FRESHER).sections()).noneMatch(s -> s.area() == dsa);
        assertThat(Presets.areaName(dsa)).isEqualTo("Data structures & algorithms");
    }
}
