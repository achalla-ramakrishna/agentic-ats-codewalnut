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

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(v.sizeBytes), 0) from InterviewVideo v")
    long totalBytes();

    @org.springframework.data.jpa.repository.Query("select count(distinct v.inviteId) from InterviewVideo v")
    long interviewCount();
}
