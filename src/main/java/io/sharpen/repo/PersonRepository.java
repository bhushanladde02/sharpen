package io.sharpen.repo;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends JpaRepository<Person, Long> {

    Optional<Person> findByEmailIgnoreCase(String email);

    Optional<Person> findByHandle(String handle);

    Optional<Person> findByApiKey(String apiKey);

    boolean existsByHandle(String handle);

    List<Person> findByAccountTypeAndPublicProfileTrue(AccountType accountType);

    List<Person> findByAccountType(AccountType accountType);
}
