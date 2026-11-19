package edu.cs336b.orders;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class OrderRepository {
    private final Connection connection;

    OrderRepository(Connection connection) {
        this.connection = connection;
    }

    boolean insert(Order order) throws SQLException {
        String sql = """
                INSERT INTO Orders (order_id, customer_name, product_name, quantity, unit_price, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, order.id());
            statement.setString(2, order.customerName());
            statement.setString(3, order.productName());
            statement.setInt(4, order.quantity());
            statement.setDouble(5, order.unitPrice());
            statement.setString(6, order.status());
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 1062) { // MySQL duplicate key
                return false;
            }
            throw exception;
        }
    }

    List<Order> findByStatus(String status) throws SQLException {
        String sql = """
                SELECT * FROM Orders
                WHERE LOWER(status) = LOWER(?) AND quantity > 2
                ORDER BY unit_price DESC, order_id ASC
                """;
        List<Order> orders = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    orders.add(readOrder(result));
                }
            }
        }
        return orders;
    }

    boolean increaseQuantity(int id, int additionalQuantity) throws SQLException {
        String sql = "UPDATE Orders SET quantity = quantity + ? WHERE order_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, additionalQuantity);
            statement.setInt(2, id);
            return statement.executeUpdate() > 0;
        }
    }

    Optional<Order> updateStatus(int id, String status) throws SQLException {
        // Lock the selected row until both the update and its readback finish.
        connection.setAutoCommit(false);
        try {
            Optional<Order> existingOrder = findById(id);
            if (existingOrder.isEmpty()) {
                connection.commit();
                return Optional.empty();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE Orders SET status = ? WHERE order_id = ?")) {
                statement.setString(1, status);
                statement.setInt(2, id);
                statement.executeUpdate();
            }
            Optional<Order> updatedOrder = findById(id);
            connection.commit();
            return updatedOrder;
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
                try {
                    connection.close();
                } catch (SQLException closeFailure) {
                    exception.addSuppressed(closeFailure);
                }
            }
            throw exception;
        } finally {
            if (!connection.isClosed()) {
                connection.setAutoCommit(true);
            }
        }
    }

    double totalValue() throws SQLException {
        String sql = "SELECT COALESCE(SUM(quantity * unit_price), 0) FROM Orders";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getDouble(1);
        }
    }

    boolean deleteCancelled(int id) throws SQLException {
        String sql = "DELETE FROM Orders WHERE order_id = ? AND LOWER(status) = 'cancelled'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    private Optional<Order> findById(int id) throws SQLException {
        String sql = "SELECT * FROM Orders WHERE order_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readOrder(result)) : Optional.empty();
            }
        }
    }

    private static Order readOrder(ResultSet result) throws SQLException {
        return new Order(result.getInt("order_id"), result.getString("customer_name"),
                result.getString("product_name"), result.getInt("quantity"),
                result.getDouble("unit_price"), result.getString("status"));
    }
}
