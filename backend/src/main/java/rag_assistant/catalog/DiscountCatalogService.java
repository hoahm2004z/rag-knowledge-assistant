package rag_assistant.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rag_assistant.model.DiscountEvent;
import rag_assistant.repository.DiscountEventRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DiscountCatalogService {

    private final DiscountEventRepository discountEventRepository;

    public List<DiscountEvent> getAllEvents() {
        return discountEventRepository.findAll();
    }

    public int getBestEventDiscountPercent(String productId, LocalDateTime purchaseTime) {
        List<DiscountEvent> allActiveEvents = discountEventRepository.findByActiveTrue();
        return allActiveEvents.stream()
                .filter(event ->
                        event.getStartDate() != null
                                && event.getEndDate() != null
                                && !purchaseTime.isBefore(event.getStartDate())
                                && !purchaseTime.isAfter(event.getEndDate())
                                && event.getProductIds() != null
                                && event.getProductIds().contains(productId))
                .mapToInt(DiscountEvent::getDiscountPercent)
                .max()
                .orElse(0);
    }

    public double calculateFinalPrice(double price, int productDiscountPercent, int eventDiscountPercent) {
        int appliedDiscount = Math.max(productDiscountPercent, eventDiscountPercent);
        return price * (100 - appliedDiscount) / 100.0;
    }
}
