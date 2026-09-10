package io.sharpen.repo;

import io.sharpen.domain.PersonAvatar;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonAvatarRepository extends JpaRepository<PersonAvatar, Long> {
}
