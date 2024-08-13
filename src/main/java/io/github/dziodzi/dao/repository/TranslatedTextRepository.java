package io.github.dziodzi.dao.repository;

import io.github.dziodzi.dao.entity.TranslatedText;
import org.springframework.data.repository.CrudRepository;

public interface TranslatedTextRepository extends CrudRepository<TranslatedText, Long> {
}
