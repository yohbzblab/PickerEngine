package org.engine.pickerengine.repository;

import org.engine.pickerengine.entity.InstagramKeywordCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstagramKeywordCacheRepository extends JpaRepository<InstagramKeywordCacheEntity, Long> {
    Optional<InstagramKeywordCacheEntity> findByUsernameAndPromptVersion(String username, String promptVersion);
}
