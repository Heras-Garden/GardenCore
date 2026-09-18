package com.herasgarden.gardencore.platform.order;

import com.herasgarden.gardencore.api.order.GardenOrder;
import com.herasgarden.gardencore.api.order.OrderService;
import com.herasgarden.gardencore.api.order.OrderState;
import com.herasgarden.gardencore.api.order.OrderType;
import com.herasgarden.gardencore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public final class SqlOrderService implements OrderService {
    private final DatabaseManager database;

    public SqlOrderService(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public GardenOrder create(
            OrderType type,
            UUID buyerUuid,
            String sellerKind,
            String sellerId,
            long amount,
            String domainKey,
            String domainRef,
            String metadata
    ) throws SQLException {
        if (type == null || buyerUuid == null) {
            throw new IllegalArgumentException("Order type and buyer are required.");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("Order amount cannot be negative.");
        }
        if (domainKey == null || domainKey.isBlank() || domainRef == null || domainRef.isBlank()) {
            throw new IllegalArgumentException("Domain key and domain reference are required.");
        }

        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        GardenOrder order = new GardenOrder(id, type, OrderState.DRAFT, buyerUuid,
                blankToNull(sellerKind), blankToNull(sellerId), amount,
                domainKey.trim(), domainRef.trim(), metadata, now, now);

        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO gc_orders "
                                + "(order_uuid, order_type, order_state, buyer_uuid, seller_kind, seller_id, amount, "
                                + "domain_key, domain_ref, metadata, created_at, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    bindOrder(statement, order);
                    statement.executeUpdate();
                }
                appendJournal(connection, order.id(), null, OrderState.DRAFT, "Order created", now);
                connection.commit();
                return order;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public Optional<GardenOrder> find(UUID orderId) throws SQLException {
        if (orderId == null) {
            return Optional.empty();
        }
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM gc_orders WHERE order_uuid = ?")) {
            statement.setString(1, orderId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    @Override
    public GardenOrder transition(UUID orderId, OrderState nextState, String detail) throws SQLException {
        if (orderId == null || nextState == null) {
            throw new IllegalArgumentException("Order id and next state are required.");
        }

        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                GardenOrder current = load(connection, orderId)
                        .orElseThrow(() -> new IllegalArgumentException("Order does not exist: " + orderId));
                if (!canTransition(current.state(), nextState)) {
                    throw new IllegalStateException("Invalid order transition: " + current.state() + " -> " + nextState);
                }

                long now = System.currentTimeMillis();
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE gc_orders SET order_state = ?, updated_at = ? "
                                + "WHERE order_uuid = ? AND order_state = ?")) {
                    statement.setString(1, nextState.name());
                    statement.setLong(2, now);
                    statement.setString(3, orderId.toString());
                    statement.setString(4, current.state().name());
                    changed = statement.executeUpdate();
                }
                if (changed != 1) {
                    throw new SQLException("Order changed concurrently. Reload and retry: " + orderId);
                }

                appendJournal(connection, orderId, current.state(), nextState, detail, now);
                connection.commit();
                return new GardenOrder(current.id(), current.type(), nextState, current.buyerUuid(),
                        current.sellerKind(), current.sellerId(), current.amount(), current.domainKey(),
                        current.domainRef(), current.metadata(), current.createdAt(), now);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private Optional<GardenOrder> load(Connection connection, UUID orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM gc_orders WHERE order_uuid = ?")) {
            statement.setString(1, orderId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    private GardenOrder read(ResultSet result) throws SQLException {
        return new GardenOrder(
                UUID.fromString(result.getString("order_uuid")),
                OrderType.valueOf(result.getString("order_type")),
                OrderState.valueOf(result.getString("order_state")),
                UUID.fromString(result.getString("buyer_uuid")),
                result.getString("seller_kind"),
                result.getString("seller_id"),
                result.getLong("amount"),
                result.getString("domain_key"),
                result.getString("domain_ref"),
                result.getString("metadata"),
                result.getLong("created_at"),
                result.getLong("updated_at")
        );
    }

    private void bindOrder(PreparedStatement statement, GardenOrder order) throws SQLException {
        statement.setString(1, order.id().toString());
        statement.setString(2, order.type().name());
        statement.setString(3, order.state().name());
        statement.setString(4, order.buyerUuid().toString());
        statement.setString(5, order.sellerKind());
        statement.setString(6, order.sellerId());
        statement.setLong(7, order.amount());
        statement.setString(8, order.domainKey());
        statement.setString(9, order.domainRef());
        statement.setString(10, order.metadata());
        statement.setLong(11, order.createdAt());
        statement.setLong(12, order.updatedAt());
    }

    private void appendJournal(
            Connection connection,
            UUID orderId,
            OrderState from,
            OrderState to,
            String detail,
            long now
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO gc_order_journal "
                        + "(event_uuid, order_uuid, from_state, to_state, detail, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, orderId.toString());
            statement.setString(3, from == null ? null : from.name());
            statement.setString(4, to.name());
            statement.setString(5, detail);
            statement.setLong(6, now);
            statement.executeUpdate();
        }
    }

    private boolean canTransition(OrderState from, OrderState to) {
        if (from == to) {
            return false;
        }
        // A completed purchase may later receive a compensating refund.
        // Other terminal states remain closed.
        if (from == OrderState.COMPLETED) {
            return to == OrderState.REFUNDED;
        }
        if (from.terminal()) {
            return false;
        }
        return switch (from) {
            case DRAFT -> EnumSet.of(OrderState.READY, OrderState.CANCELLED).contains(to);
            case READY -> EnumSet.of(OrderState.AWAITING_CONFIRMATION, OrderState.CANCELLED, OrderState.EXPIRED).contains(to);
            case AWAITING_CONFIRMATION -> EnumSet.of(OrderState.PAYMENT_PENDING, OrderState.CANCELLED, OrderState.EXPIRED).contains(to);
            case PAYMENT_PENDING -> EnumSet.of(OrderState.PAID, OrderState.PAYMENT_FAILED, OrderState.CANCELLED).contains(to);
            case PAID -> EnumSet.of(OrderState.FULFILLING, OrderState.REFUNDED).contains(to);
            case FULFILLING -> EnumSet.of(OrderState.COMPLETED, OrderState.FULFILLMENT_FAILED, OrderState.REFUNDED).contains(to);
            default -> false;
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
