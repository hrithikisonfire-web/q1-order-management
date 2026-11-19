package edu.cs336b.orders;

import java.io.Console;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;

public final class OrderManagementApp {
    private static final List<Order> SAMPLE_ORDERS = List.of(
            new Order(101, "Asha Rao", "Keyboard", 3, 1499.00, "Pending"),
            new Order(102, "Ravi Shah", "Monitor", 4, 7999.00, "Pending"),
            new Order(103, "Meera Das", "Mouse", 2, 699.00, "Cancelled")
    );

    public static void main(String[] args) {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/ecommerce_db");
        String user = System.getenv().getOrDefault("DB_USER", "root");
        String password = System.getenv("DB_PASSWORD");
        Console console = System.console();
        if (password == null && console != null) {
            char[] entered = console.readPassword("MySQL password for %s: ", user);
            if (entered == null) {
                return;
            }
            password = new String(entered);
        }

        try (Connection connection = DriverManager.getConnection(url, user, password == null ? "" : password);
             Scanner input = new Scanner(System.in)) {
            System.out.println("Connected to MySQL.");
            runMenu(input, new OrderRepository(connection));
        } catch (SQLException exception) {
            System.err.println("Database connection error: " + exception.getMessage());
            System.exit(1);
        } catch (NoSuchElementException exception) {
            System.out.println("Input ended. Goodbye.");
        }
    }

    private static void runMenu(Scanner input, OrderRepository repository) {
        while (true) {
            System.out.print("""

                    Order Management
                    1. Insert three sample orders
                    2. List orders by status with quantity greater than 2
                    3. Increase order quantity
                    4. Update order status
                    5. Display total order value
                    6. Delete a cancelled order
                    0. Exit
                    """);
            String choice = readText(input, "Choose an option: ");
            try {
                switch (choice) {
                    case "1" -> insertSamples(repository);
                    case "2" -> showMatchingOrders(input, repository);
                    case "3" -> increaseQuantity(input, repository);
                    case "4" -> updateStatus(input, repository);
                    case "5" -> System.out.printf(Locale.ROOT, "Total order value: %.2f%n",
                            repository.totalValue());
                    case "6" -> deleteCancelled(input, repository);
                    case "0" -> {
                        System.out.println("Goodbye.");
                        return;
                    }
                    default -> System.out.println("Choose a menu option from 0 to 6.");
                }
            } catch (SQLException exception) {
                System.err.println("Database operation failed: " + exception.getMessage());
            }
        }
    }

    private static void insertSamples(OrderRepository repository) throws SQLException {
        for (Order order : SAMPLE_ORDERS) {
            if (repository.insert(order)) {
                System.out.println("Inserted order " + order.id() + ".");
            } else {
                System.out.println("Order " + order.id() + " already exists. Skipped.");
            }
        }
    }

    private static void showMatchingOrders(Scanner input, OrderRepository repository) throws SQLException {
        String status = readText(input, "Order status: ");
        List<Order> orders = repository.findByStatus(status);
        if (orders.isEmpty()) {
            System.out.println("No matching orders.");
        } else {
            orders.forEach(OrderManagementApp::printOrder);
        }
    }

    private static void increaseQuantity(Scanner input, OrderRepository repository) throws SQLException {
        int id = readInt(input, "Order ID: ");
        int additionalQuantity = readInt(input, "Additional quantity: ");
        if (additionalQuantity <= 0) {
            System.out.println("Additional quantity must be positive.");
            return;
        }
        if (repository.increaseQuantity(id, additionalQuantity)) {
            System.out.println("Quantity increased for order " + id + ".");
        } else {
            System.out.println("Order " + id + " was not found.");
        }
    }

    private static void updateStatus(Scanner input, OrderRepository repository) throws SQLException {
        int id = readInt(input, "Order ID: ");
        String entered = readText(input, "New status (Pending, Shipped, Delivered, Cancelled): ");
        String status = switch (entered.toLowerCase(Locale.ROOT)) {
            case "pending" -> "Pending";
            case "shipped" -> "Shipped";
            case "delivered" -> "Delivered";
            case "cancelled" -> "Cancelled";
            default -> null;
        };
        if (status == null) {
            System.out.println("Invalid order status.");
            return;
        }
        Optional<Order> updatedOrder = repository.updateStatus(id, status);
        if (updatedOrder.isPresent()) {
            System.out.println("Updated order:");
            printOrder(updatedOrder.get());
        } else {
            System.out.println("Order " + id + " was not found.");
        }
    }

    private static void deleteCancelled(Scanner input, OrderRepository repository) throws SQLException {
        int id = readInt(input, "Order ID: ");
        if (repository.deleteCancelled(id)) {
            System.out.println("Deleted cancelled order " + id + ".");
        } else {
            System.out.println("Order " + id + " cannot be deleted. It must exist and be Cancelled.");
        }
    }

    private static void printOrder(Order order) {
        System.out.printf(Locale.ROOT, "%d | %s | %s | quantity %d | unit price %.2f | %s%n",
                order.id(), order.customerName(), order.productName(), order.quantity(),
                order.unitPrice(), order.status());
    }

    private static int readInt(Scanner input, String prompt) {
        while (true) {
            try {
                return Integer.parseInt(readText(input, prompt));
            } catch (NumberFormatException ignored) {
                System.out.println("Enter a whole number in the Java int range.");
            }
        }
    }

    private static String readText(Scanner input, String prompt) {
        System.out.print(prompt);
        return input.nextLine().strip();
    }
}
