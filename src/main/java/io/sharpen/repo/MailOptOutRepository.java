package io.sharpen.repo;

import io.sharpen.domain.MailOptOut;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailOptOutRepository extends JpaRepository<MailOptOut, String> {
}
