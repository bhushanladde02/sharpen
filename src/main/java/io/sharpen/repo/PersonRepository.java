package io.sharpen.repo;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByEmailIgnoreCase(String email);

    long countByInvitedBy(Long invitedBy);

    /** When a member leaves, the accounts they invited stop pointing at them. */
    @Modifying @Transactional
    @Query("update Person p set p.invitedBy = null where p.invitedBy = :id")
    int clearInvitedBy(Long id);

    Optional<Person> findByHandle(String handle);

    Optional<Person> findByApiKey(String apiKey);

    boolean existsByHandle(String handle);

    List<Person> findByAccountTypeAndPublicProfileTrue(AccountType accountType);

    List<Person> findByAccountType(AccountType accountType);

    long countByAccountType(AccountType accountType);
}
