package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.CodingRoom;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodingRoomRepository extends JpaRepository<CodingRoom, UUID> {

    Optional<CodingRoom> findByInterviewId(UUID interviewId);

    Optional<CodingRoom> findByToken(String token);
}
