package rag_assistant.constants;

/** Mirrors pharmacy_webapp PaymentMethodConstants. */
public final class PaymentMethodConstants {
    public static final int CASH_ON_DELIVERY = 1;
    public static final int VNPAY = 2;

    private PaymentMethodConstants() {}

    public static String getDescription(int paymentMethod) {
        return switch (paymentMethod) {
            case CASH_ON_DELIVERY -> "Cash On Delivery";
            case VNPAY -> "Vnpay";
            default -> "Unknown PaymentMethod";
        };
    }
}
