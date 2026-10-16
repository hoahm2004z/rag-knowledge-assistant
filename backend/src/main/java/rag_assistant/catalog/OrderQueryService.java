package rag_assistant.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rag_assistant.model.Bill;
import rag_assistant.repository.BillRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final BillRepository billRepository;

    public List<Bill> getUserBills(String userId) {
        return billRepository.findByUser_Id(userId);
    }

    public Bill getBillById(String orderId) {
        return billRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Bill not found"));
    }
}
