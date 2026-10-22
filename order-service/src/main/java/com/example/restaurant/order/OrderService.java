package com.example.restaurant.order;

import com.example.restaurant.outbox.OutboxRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OutboxRepository outbox;

    public OrderService(OrderRepository orders, OutboxRepository outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    /** Order row + outbox row commit atomically. Kafka is never touched here. */
    @Transactional
    @PreAuthorize("hasRole('CUSTOMER')")
    public UUID createOrder(CreateOrderCommand cmd, String customerId) {
        BigDecimal total = cmd.lines().stream()
            .map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = orders.save(Order.create(customerId, cmd.locationId(), cmd.lines(), total));

        outbox.append("order", order.getId().toString(), "OrderCreated",
            new OrderEvent.OrderCreated(order.getId(), customerId, cmd.locationId(), cmd.lines(), total));
        return order.getId();
    }

    /** Used by the saga (no end-user security context). */
    @Transactional
    public void transition(UUID id, OrderStatus next, String reason) {
        Order order = orders.findById(id).orElseThrow();
        order.transitionTo(next);   // dirty checking + @Version optimistic lock
        outbox.append("order", id.toString(), "OrderStatusChanged",
            new OrderEvent.OrderStatusChanged(id, next.name(), reason));
    }

    @Transactional(readOnly = true)
    @PostAuthorize("hasPermission(returnObject, 'READ')")
    public Order get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
