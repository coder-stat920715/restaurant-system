package com.example.restaurant.replay;

import com.example.restaurant.order.Order;
import com.example.restaurant.order.OrderRepository;
import com.example.restaurant.outbox.OutboxRepository;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rebuild support for read models. The write model (Postgres) is the source of truth, so a replay re-emits every
 * order as an OrderSnapshot through the normal outbox -> Debezium -> Kafka path. One transaction per page keeps
 * transactions short; consumers upsert, so running a replay twice is harmless.
 */
@Service
public class OrderReplayService {

    private final OrderRepository orders;
    private final OutboxRepository outbox;
    private final TransactionTemplate tx;

    public OrderReplayService(OrderRepository orders, OutboxRepository outbox, PlatformTransactionManager txManager) {
        this.orders = orders;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(txManager);
    }

    /** @return number of orders re-published */
    public int replayAll(int batchSize) {
        int page = 0;
        int total = 0;
        Page<Order> current;
        do {
            final int pageNumber = page;
            current = Objects.requireNonNull(tx.execute(status -> {
                Page<Order> result = orders.findAll(PageRequest.of(pageNumber, batchSize, Sort.by("createdAt", "id")));
                result.forEach(o -> outbox.append("order", o.getId().toString(), "OrderSnapshot", OrderSnapshot.from(o)));
                return result;
            }));
            total += current.getNumberOfElements();
            page++;
        } while (current.hasNext());
        return total;
    }
}
