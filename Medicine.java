package com.hananimed.model;

/** A medicine stock item in the clinic inventory. */
public class Medicine {
    private final String id;
    private String name;
    private int quantity;
    private double price;

    public Medicine(String id, String name, int quantity, double price) {
        if (quantity < 0) throw new IllegalArgumentException("Quantity cannot be negative.");
        if (price < 0) throw new IllegalArgumentException("Price cannot be negative.");
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }

    public void setPrice(double price) {
        if (price < 0) throw new IllegalArgumentException("Price cannot be negative.");
        this.price = price;
    }

    public void addStock(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Restock quantity must be greater than 0.");
        quantity += qty;
    }

    /** Removes stock; refuses non-positive amounts and amounts larger than the current stock. */
    public void reduceStock(int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be greater than 0.");
        if (qty > quantity) {
            throw new IllegalArgumentException("Not enough stock! (Available: " + quantity + ")");
        }
        quantity -= qty;
    }

    public boolean isLowStock(int threshold) { return quantity < threshold; }

    public String getDetails() {
        return String.format("Med[ID=%s, Name=%s, Qty=%d, Price=RM%.2f]", id, name, quantity, price);
    }
}
