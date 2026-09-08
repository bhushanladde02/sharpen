package io.sharpen.repo;

import io.sharpen.domain.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findTop200ByOrderByCreatedAtDesc();
}
