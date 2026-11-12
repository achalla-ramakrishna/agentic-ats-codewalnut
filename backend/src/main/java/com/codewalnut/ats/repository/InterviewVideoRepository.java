package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.InterviewVideo;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewVideoRepository extends JpaRepository<InterviewVideo, UUID> {

    Optional<InterviewVideo> findByInviteIdAndQuestionId(UUID inviteId, UUID questionId);

    List<InterviewVideo> findByInviteId(UUID inviteId);

    List<InterviewVideo> findByInviteIdIn(java.util.Collection<UUID> inviteIds);
}
