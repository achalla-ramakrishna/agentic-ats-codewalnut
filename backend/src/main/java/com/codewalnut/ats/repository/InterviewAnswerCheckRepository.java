package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.InterviewAnswerCheck;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewAnswerCheckRepository extends JpaRepository<InterviewAnswerCheck, UUID> {

    Optional<InterviewAnswerCheck> findByInviteIdAndQuestionId(UUID inviteId, UUID questionId);

    List<InterviewAnswerCheck> findByInviteId(UUID inviteId);

    List<InterviewAnswerCheck> findByInviteIdIn(Collection<UUID> inviteIds);

    List<InterviewAnswerCheck> findByReading(InterviewAnswerCheck.Reading reading);
}
