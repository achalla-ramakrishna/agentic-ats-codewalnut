package com.codewalnut.ats.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codewalnut.ats.client.AssistantClient;
import com.codewalnut.ats.client.AssistantPlan;
import com.codewalnut.ats.domain.AppUser;
import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.Candidate;
import com.codewalnut.ats.domain.JobOpening;
import com.codewalnut.ats.domain.Stage;
import com.codewalnut.ats.dto.AssistantDtos.PlanResponse;
import com.codewalnut.ats.repository.ApplicationRepository;
import com.codewalnut.ats.repository.JobOpeningRepository;
import com.codewalnut.ats.security.AccessPolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Whatever the model says, only real candidates of this opening and real stages get through. */
class AssistantServiceTest {

    private final UUID jobId = UUID.randomUUID();
    private final Application sagar = Application.builder().id(UUID.randomUUID()).stage(Stage.INTERVIEWED)
            .candidate(Candidate.builder().name("Sagar Kumar").build()).build();
    private final JobOpeningRepository jobs = mock(JobOpeningRepository.class);
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final AssistantClient client = mock(AssistantClient.class);
    private final AssistantService service = new AssistantService(jobs, applications, client, mock(AccessPolicy.class),
            mock(AuditService.class));

    @Test
    void dropsMadeUpIdsUnknownStagesAndDuplicates() {
        when(jobs.findById(jobId)).thenReturn(Optional.of(JobOpening.builder().id(jobId).title("Interns").build()));
        when(applications.findByJobIdOrderByCandidateNameAsc(jobId)).thenReturn(List.of(sagar));
        String id = sagar.getId().toString();
        when(client.plan(any())).thenReturn(new AssistantPlan("s", List.of(
                new AssistantPlan.Action("MOVE_STAGE", id, "SHORTLISTED", ""),
                new AssistantPlan.Action("MOVE_STAGE", id, "SHORTLISTED", ""),
                new AssistantPlan.Action("MOVE_STAGE", UUID.randomUUID().toString(), "JOINED", ""),
                new AssistantPlan.Action("MOVE_STAGE", "not-an-id", "JOINED", ""),
                new AssistantPlan.Action("MOVE_STAGE", id, "PROMOTED_TO_CEO", ""),
                new AssistantPlan.Action("DELETE_EVERYONE", id, "", "x"),
                new AssistantPlan.Action("ADD_NOTE", id, "", "Strong in React")),
                List.of(new AssistantPlan.Unresolved("amogh", List.of(UUID.randomUUID().toString(), id), "SHORTLISTED", ""))));

        PlanResponse plan = service.plan(AppUser.builder().email("r@codewalnut.test").build(), jobId, "whatever");

        assertThat(plan.actions()).extracting(a -> a.type() + ":" + a.toStage())
                .containsExactly("MOVE_STAGE:SHORTLISTED", "ADD_NOTE:null");
        assertThat(plan.notes()).hasSize(3);
        assertThat(plan.unresolved()).singleElement()
                .satisfies(u -> assertThat(u.options()).extracting(o -> o.applicationId()).containsExactly(sagar.getId()));
    }
}
