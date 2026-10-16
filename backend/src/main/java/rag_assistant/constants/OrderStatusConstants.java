package rag_assistant.constants;

/** Mirrors pharmacy_webapp OrderStatusConstants (codes used by Bill.oderStatus). */
public final class OrderStatusConstants {
    public static final int PENDING = 0;
    public static final int CONFIRMED = 1;
    public static final int SHIPPING = 2;
    public static final int DELIVERED = 3;
    public static final int CANCELLED = 4;
    public static final int RETURNED = 5;

    private OrderStatusConstants() {}

    public static String getDescription(int code) {
        return switch (code) {
            case PENDING -> "Pending";
            case CONFIRMED -> "Confirmed";
            case SHIPPING -> "Shipping";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
            case RETURNED -> "Returned";
            default -> "Unknown";
        };
    }
}
