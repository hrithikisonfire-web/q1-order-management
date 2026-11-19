package edu.cs336b.orders;

record Order(int id, String customerName, String productName, int quantity,
             double unitPrice, String status) {
}
