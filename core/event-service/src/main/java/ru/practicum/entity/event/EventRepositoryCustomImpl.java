package ru.practicum.entity.event;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class EventRepositoryCustomImpl implements EventRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public EventRepositoryCustomImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<Long> findPageIds(Predicate predicate, Sort sort, int offset, int limit) {
        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        if (sort != null && sort.isSorted()) {
            for (Sort.Order order : sort) {
                switch (order.getProperty()) {
                    case "views" -> orderSpecifiers.add(
                            order.isAscending() ? QEvent.event.views.asc() : QEvent.event.views.desc());
                    case "eventDate" -> orderSpecifiers.add(
                            order.isAscending() ? QEvent.event.eventDate.asc() : QEvent.event.eventDate.desc());
                    case "id" -> orderSpecifiers.add(
                            order.isAscending() ? QEvent.event.id.asc() : QEvent.event.id.desc());
                    default -> {
                    }
                }
            }
        }
        if (orderSpecifiers.isEmpty()) {
            orderSpecifiers.add(QEvent.event.id.asc());
        }

        return queryFactory
                .select(QEvent.event.id)
                .from(QEvent.event)
                .where(predicate)
                .orderBy(orderSpecifiers.toArray(new OrderSpecifier[0]))
                .offset(offset)
                .limit(limit)
                .fetch();
    }
}