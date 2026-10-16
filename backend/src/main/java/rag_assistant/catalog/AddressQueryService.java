package rag_assistant.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rag_assistant.model.ShippingAddress;
import rag_assistant.repository.ShippingAddressRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressQueryService {

    private final ShippingAddressRepository shippingAddressRepository;

    public List<ShippingAddress> getAllShippingAddressByUserId(String userId) {
        return shippingAddressRepository.findByUser_Id(userId);
    }
}
