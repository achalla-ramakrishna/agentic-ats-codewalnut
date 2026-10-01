package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.Message;
import com.codewalnut.ats.domain.MessageAuthorType;
import com.codewalnut.ats.domain.MessageChannel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only: no update or delete methods are used. */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByApplicationIdAndChannelOrderByCreatedAtAsc(UUID applicationId, MessageChannel channel);

    List<Message> findByChannelInOrderByCreatedAtDesc(java.util.Collection<MessageChannel> channels, Pageable pageable);

    long countByApplicationIdAndChannelAndAuthorTypeAndCreatedAtAfter(
            UUID applicationId, MessageChannel channel, MessageAuthorType authorType, Instant after);

    long countByApplicationIdAndChannelAndAuthorType(UUID applicationId, MessageChannel channel, MessageAuthorType authorType);
}
