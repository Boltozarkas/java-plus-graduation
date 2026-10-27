package ru.practicum.entity.event;

import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface EventRepositoryCustom {
    List<Long> findPageIds(Predicate predicate, Sort sort, int offset, int limit);
}