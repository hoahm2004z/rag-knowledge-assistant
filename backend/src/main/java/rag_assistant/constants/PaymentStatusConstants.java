package rag_assistant.constants;

/** Mirrors pharmacy_webapp PaymentStatusConstants. */
public final class PaymentStatusConstants {
    public static final int PENDING = 0;
    public static final int PAID = 1;
    public static final int FAILED = 2;
    public static final int REFUNDED = 3;
    public static final int CANCELLED = 4;

    private PaymentStatusConstants() {}

    public static String getDescription(int code) {
        return switch (code) {
            case PENDING -> "Pending";
            case PAID -> "Paid";
            case FAILED -> "Failed";
            case REFUNDED -> "Refunded";
            case CANCELLED -> "Cancelled";
            default -> "Unknown";
        };
    }
}
