package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.BookingLink;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingLinkRepository extends JpaRepository<BookingLink, String> {

    Optional<BookingLink> findByApplicationId(UUID applicationId);
}
