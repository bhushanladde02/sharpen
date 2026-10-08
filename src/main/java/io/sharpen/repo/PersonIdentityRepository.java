package io.sharpen.repo;

import io.sharpen.domain.PersonIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PersonIdentityRepository extends JpaRepository<PersonIdentity, Long> {
    Optional<PersonIdentity> findByProviderAndSubject(String provider, String subject);
    Optional<PersonIdentity> findByPersonIdAndProvider(Long personId, String provider);
    List<PersonIdentity> findByPersonIdOrderByProviderAsc(Long personId);
    long countByPersonId(Long personId);

    @Transactional
    long deleteByPersonId(Long personId);
}
