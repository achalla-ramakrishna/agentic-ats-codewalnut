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

    boolean existsByWhatsappMessageId(String whatsappMessageId);

    /** The candidate's latest WhatsApp message on this application, for WhatsApp's 24-hour reply window. */
    java.util.Optional<Message> findFirstByApplicationIdAndAuthorTypeAndWhatsappStatusOrderByCreatedAtDesc(
            UUID applicationId, MessageAuthorType authorType, String whatsappStatus);

    /** Delivery receipts only change the status column; the message itself stays as written. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value =
            "UPDATE message SET whatsapp_status = :status WHERE whatsapp_message_id = :id AND author_type = 'STAFF'")
    int updateWhatsappStatus(@org.springframework.data.repository.query.Param("id") String whatsappMessageId,
            @org.springframework.data.repository.query.Param("status") String status);
}
