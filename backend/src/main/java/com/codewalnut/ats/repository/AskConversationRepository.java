package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.AskConversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AskConversationRepository extends JpaRepository<AskConversation, UUID> {

    List<AskConversation> findByOwnerEmailOrderByUpdatedAtDesc(String ownerEmail, Pageable page);

    Optional<AskConversation> findByIdAndOwnerEmail(UUID id, String ownerEmail);
}
