package rag_assistant.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rag_assistant.model.ShoppingCart;
import rag_assistant.repository.ShoppingCartRepository;

@Service
@RequiredArgsConstructor
public class CartQueryService {

    private final ShoppingCartRepository shoppingCartRepository;

    public ShoppingCart getShoppingCart(String userId) {
        return shoppingCartRepository.getShoppingCartByUser_Id(userId);
    }
}
