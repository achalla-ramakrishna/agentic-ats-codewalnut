package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codewalnut.ats.bank.Roles;
import com.codewalnut.ats.dto.InterviewKitDtos.KitContent;
import com.codewalnut.ats.dto.InterviewKitDtos.KitRound;
import com.codewalnut.ats.dto.InterviewKitDtos.KitSkill;
import java.util.List;
import org.junit.jupiter.api.Test;

/** INT-28, INT-29: reading a job description and building the kit, without a database. */
class InterviewKitServiceTest {

    private final InterviewKitService service = new InterviewKitService(null, null, null, new InterviewGuideService(null), null, null, null);

    private static final String FULLSTACK = """
            We are hiring a full-stack engineer with 3-5 years of experience.
            Must have:
            - Strong Java and Spring Boot, REST APIs
            - Hands-on React and TypeScript
            - MySQL, writing efficient queries
            Nice to have: Docker, Kubernetes
            You will work with the rest of the team on microservices for a fintech client.
            """;

    private static KitRound round(KitContent k, String name) {
        return k.rounds().stream().filter(r -> r.name().equals(name)).findFirst().orElse(null);
    }

    private static KitSkill skill(KitContent k, String name) {
        return k.skills().stream().filter(s -> s.name().equals(name)).findFirst().orElse(null);
    }

    @Test
    void readsSkillsLevelAndRoleFromTheJobDescription() {
        KitContent k = service.build("Full-stack Developer", FULLSTACK, "HYBRID", "Bengaluru", "Acme", null, null, 42);
        assertThat(k.level()).isEqualTo("MID");
        assertThat(k.roleId()).isEqualTo("fullstack-java-react");
        assertThat(k.skills()).extracting(KitSkill::name).contains("Java", "Spring Boot", "React", "TypeScript", "SQL databases",
                "Docker", "Kubernetes", "System design and microservices", "APIs (REST)");
        assertThat(skill(k, "Java").mustHave()).isTrue();
        assertThat(skill(k, "Docker").mustHave()).isFalse();
        // Must-haves come first.
        assertThat(k.skills().get(0).mustHave()).isTrue();

        assertThat(k.rounds()).extracting(KitRound::name)
                .containsExactly("Screening call", "Technical interview", "Live coding", "System design", "Project deep-dive and fit");
        assertThat(round(k, "Screening call").questions()).anyMatch(q -> q.question().contains("hybrid in Bengaluru"));
        assertThat(round(k, "Screening call").questions()).anyMatch(q -> q.question().contains("Acme (through CodeWalnut)"));
        KitRound tech = round(k, "Technical interview");
        assertThat(tech.questions()).anyMatch(q -> q.source().equals("job") && q.topic().equals("Java"));
        assertThat(tech.questions()).anyMatch(q -> q.source().equals("Java and Spring") || q.source().equals("Spring Boot and Hibernate"));
        var coding = round(k, "Live coding").coding();
        assertThat(coding).hasSize(2);
        assertThat(coding.get(0).difficulty()).isEqualTo("MEDIUM");
        assertThat(coding.get(1).difficulty()).isEqualTo("MEDIUM");
        // Across many seeds the warm-up is never harder than the main problem.
        for (long seed = 0; seed < 40; seed++) {
            for (Roles.Level level : Roles.Level.values()) {
                var c = round(service.build("Java Developer", FULLSTACK, null, null, null, level, null, seed), "Live coding").coding();
                assertThat(c).hasSize(2);
                assertThat(com.codewalnut.ats.domain.BankQuestion.Difficulty.valueOf(c.get(0).difficulty()))
                        .isLessThanOrEqualTo(com.codewalnut.ats.domain.BankQuestion.Difficulty.valueOf(c.get(1).difficulty()));
                if (level.ordinal() >= Roles.Level.MID.ordinal()) {
                    assertThat(c).noneMatch(x -> x.difficulty().equals("EASY"));
                }
            }
        }
        assertThat(round(k, "System design").questions()).isNotEmpty();
        assertThat(k.scorecard()).extracting(r -> r.name()).contains("Problem solving", "Java");
        assertThat(k.test().preset().sections()).isNotEmpty();
        assertThat(k.hireBar()).contains("Java");
    }

    @Test
    void internsGetFresherQuestionsAndNoSystemDesign() {
        KitContent k = service.build("Python Intern", "Learn Python and Django. Basic SQL. Good problem-solving skills.", null, null, null,
                null, null, 7);
        assertThat(k.level()).isEqualTo("FRESHER");
        assertThat(k.roleId()).isEqualTo("python-backend");
        assertThat(k.rounds()).extracting(KitRound::name).doesNotContain("System design").contains("Projects and attitude");
        KitRound coding = round(k, "Live coding");
        assertThat(coding.coding()).hasSize(2).allMatch(c -> !c.difficulty().equals("HARD"));
        assertThat(coding.questions()).allMatch(q -> q.source().equals("Data structures for freshers and interns"));
        assertThat(k.test().level()).isEqualTo("FRESHER");
    }

    @Test
    void rolesWithoutCodingGetAHandsOnExercise() {
        KitContent k = service.build("Senior DevOps Engineer", "6+ years with AWS, Kubernetes, Terraform and Jenkins CI/CD pipelines.", null,
                null, null, null, null, 3);
        assertThat(k.level()).isEqualTo("SENIOR");
        assertThat(k.roleId()).isEqualTo("devops-engineer");
        KitRound handsOn = round(k, "Hands-on exercise");
        assertThat(handsOn).isNotNull();
        assertThat(handsOn.coding()).isEmpty();
        assertThat(handsOn.questions()).isNotEmpty();
    }

    @Test
    void sparseDescriptionsSayWhatWasAssumedAndOverridesWin() {
        KitContent k = service.build("Software Engineer", null, null, null, null, null, null, 1);
        assertThat(k.level()).isEqualTo("JUNIOR");
        assertThat(k.notes()).anyMatch(n -> n.contains("no job description")).anyMatch(n -> n.contains("assumes 1–3 years"));
        assertThat(k.skills()).isEmpty();

        KitContent o = service.build("Software Engineer", null, null, null, null, Roles.Level.LEAD, Roles.role("node-backend"), 1);
        assertThat(o.level()).isEqualTo("LEAD");
        assertThat(o.detectedLevel()).isEqualTo("JUNIOR");
        assertThat(o.roleId()).isEqualTo("node-backend");
        assertThat(o.rounds()).extracting(KitRound::name).contains("System design");
    }

    @Test
    void theSameSeedGivesTheSameKitAndWordsInPassingDontCount() {
        KitContent a = service.build("Full-stack Developer", FULLSTACK, null, null, null, null, null, 99);
        KitContent b = service.build("Full-stack Developer", FULLSTACK, null, null, null, null, null, 99);
        assertThat(a).isEqualTo(b);
        List<KitSkill> skills = InterviewKitService.skills("Engineer", "Take some rest. You will excel with the rest of the team.");
        assertThat(skills).isEmpty();
        assertThat(InterviewKitService.skills("Engineer", "We react quickly to incidents.")).isEmpty();
        assertThat(InterviewKitService.skills("Engineer", "JavaScript only")).extracting(KitSkill::name).containsExactly("JavaScript");
    }
}
