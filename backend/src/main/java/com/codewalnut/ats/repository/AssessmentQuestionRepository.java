package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.AssessmentQuestion;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, UUID> {

    List<AssessmentQuestion> findByAssessmentIdOrderByPositionAsc(UUID assessmentId);

    long countByAssessmentId(UUID assessmentId);

    void deleteByAssessmentId(UUID assessmentId);

    @Query("select q.assessmentId, count(q), sum(q.points) from AssessmentQuestion q where q.assessmentId in :ids group by q.assessmentId")
    List<Object[]> countsFor(Collection<UUID> ids);
}
