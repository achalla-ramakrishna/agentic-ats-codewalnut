package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.Application;
import com.codewalnut.ats.domain.Stage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findByJobIdOrderByCandidateNameAsc(UUID jobId);

    boolean existsByJobIdAndCandidateId(UUID jobId, UUID candidateId);

    /** For people with neither email nor phone: the same name in the same opening counts as the same person. */
    boolean existsByJobIdAndCandidateNameIgnoreCase(UUID jobId, String name);

    @Query("select a.job.id, a.stage, count(a) from Application a group by a.job.id, a.stage")
    List<Object[]> countByJobAndStage();

    @Query("""
            select a from Application a
            where (:q is null
                   or lower(a.candidate.name) like lower(concat('%', :q, '%'))
                   or lower(a.candidate.email) like lower(concat('%', :q, '%'))
                   or a.candidate.phone like concat('%', :q, '%'))
              and (:stage is null or a.stage = :stage)
            order by a.updatedAt desc
            """)
    List<Application> search(String q, Stage stage);
}
