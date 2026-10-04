package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.InterviewFeedback;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, UUID> {

    List<InterviewFeedback> findByInterviewIdOrderBySubmittedAtAsc(UUID interviewId);

    List<InterviewFeedback> findByInterviewIdIn(Collection<UUID> interviewIds);

    Optional<InterviewFeedback> findByInterviewIdAndAuthorEmail(UUID interviewId, String authorEmail);
}
