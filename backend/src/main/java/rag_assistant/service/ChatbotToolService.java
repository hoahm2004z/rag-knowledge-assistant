package rag_assistant.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import rag_assistant.config.RagProperties;
import rag_assistant.catalog.AddressQueryService;
import rag_assistant.catalog.CartQueryService;
import rag_assistant.catalog.CategoriesCatalogService;
import rag_assistant.catalog.DiscountCatalogService;
import rag_assistant.catalog.ManufacturerCatalogService;
import rag_assistant.catalog.OrderQueryService;
import rag_assistant.catalog.ProductCatalogService;
import rag_assistant.catalog.UserAccountService;
import rag_assistant.config.StoreProperties;
import rag_assistant.constants.OrderStatusConstants;
import rag_assistant.constants.PaymentMethodConstants;
import rag_assistant.constants.PaymentStatusConstants;
import rag_assistant.model.Bill;
import rag_assistant.model.Categories;
import rag_assistant.model.DiscountEvent;
import rag_assistant.model.Manufacturer;
import rag_assistant.model.Product;
import rag_assistant.model.ShippingAddress;
import rag_assistant.model.ShoppingCart;
import rag_assistant.model.User;
import rag_assistant.rag.service.RagSearchService;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tools mirrored from pharmacy_webapp ChatbotToolService.
 * Navigation tools (openProduct / openMyOrders) are intentionally omitted — no SPA navigation yet.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotToolService {

    public static final int MAX_PRODUCT_RESULTS = 8;

    private final ProductCatalogService productCatalogService;
    private final CategoriesCatalogService categoriesCatalogService;
    private final ManufacturerCatalogService manufacturerCatalogService;
    private final DiscountCatalogService discountCatalogService;
    private final OrderQueryService orderQueryService;
    private final CartQueryService cartQueryService;
    private final UserAccountService userAccountService;
    private final AddressQueryService addressQueryService;
    private final RagSearchService ragSearchService;
    private final StoreProperties storeProperties;
    private final RagProperties ragProperties;

    @Getter
    public static class ToolExecution {
        private final Map<String, Object> payload;

        public ToolExecution(Map<String, Object> payload) {
            this.payload = payload;
        }
    }

    public List<Map<String, Object>> functionDeclarations() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(fn("searchProducts",
                "Tim san pham theo ten, tu khoa, danh muc hoac gia trong database. Dung khi hoi gia, ton kho, san pham.",
                Map.of(
                        "query", prop("string", "Ten hoac tu khoa. Co the rong neu chi loc theo gia."),
                        "maxPrice", prop("number", "Gia toi da (VND), khong bat buoc.")
                ),
                List.of()));
        tools.add(fn("getProductDetail",
                "Lay chi tiet 1 san pham theo productId tu database: gia, ton kho, hang, danh muc, mo ta.",
                Map.of("productId", prop("string", "Id san pham")),
                List.of("productId")));
        tools.add(fn("getDiscountProducts",
                "Lay cac su kien giam gia/flash sale dang active va san pham ap dung tu database.",
                Map.of(),
                List.of()));
        tools.add(fn("getStoreInfo",
                "Thong tin cong khai cua hang (cau hinh local standalone).",
                Map.of(),
                List.of()));
        tools.add(fn("searchKnowledge",
                "Tim chinh sach/FAQ/huong dan trong kho tai lieu RAG. Dung khi hoi giao hang, doi tra, thanh toan, FAQ. Khong dung de lay gia/ton kho realtime.",
                Map.of("query", prop("string", "Cau hoi hoac tu khoa can tra cuu trong tai lieu")),
                List.of("query")));
        tools.add(fn("getMyProfile",
                "Ho so cua user dang dang nhap. Khong dung userId tu model. Neu chua login thi bao can dang nhap.",
                Map.of(),
                List.of()));
        tools.add(fn("getMyOrders",
                "Danh sach don hang cua user dang dang nhap.",
                Map.of(),
                List.of()));
        tools.add(fn("getMyOrderDetail",
                "Chi tiet 1 don hang cua CHINH user dang dang nhap. Tu choi don cua nguoi khac.",
                Map.of("orderId", prop("string", "Id don hang (bill id)")),
                List.of("orderId")));
        tools.add(fn("getMyCart",
                "Gio hang cua user dang dang nhap.",
                Map.of(),
                List.of()));
        tools.add(fn("getMyAddresses",
                "Dia chi giao hang cua user dang dang nhap (khong gom so dien thoai).",
                Map.of(),
                List.of()));
        return tools;
    }

    public ToolExecution execute(String name, Map<String, Object> args, String currentUserId) {
        try {
            return switch (name) {
                case "searchProducts" -> okDb(searchProducts(str(args, "query"), num(args, "maxPrice")));
                case "getProductDetail" -> getProductDetail(str(args, "productId"));
                case "getDiscountProducts" -> okDb(getDiscountProducts());
                case "getStoreInfo" -> okDb(getStoreInfo());
                case "searchKnowledge" -> searchKnowledge(str(args, "query"));
                case "getMyProfile" -> requireUser(currentUserId, this::getMyProfile);
                case "getMyOrders" -> requireUser(currentUserId, this::getMyOrders);
                case "getMyOrderDetail" -> requireUser(currentUserId, id -> getMyOrderDetail(id, str(args, "orderId")));
                case "getMyCart" -> requireUser(currentUserId, this::getMyCart);
                case "getMyAddresses" -> requireUser(currentUserId, this::getMyAddresses);
                default -> error("UNKNOWN_TOOL", "Cong cu khong duoc phep.");
            };
        } catch (DataAccessException e) {
            return error("DB_ERROR", "Loi truy van database. Khong the lay du lieu luc nay.");
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("not found")) {
                return error("NOT_FOUND", e.getMessage());
            }
            return error("TOOL_ERROR", "Loi xu ly cong cu: " + e.getMessage());
        }
    }

    private ToolExecution requireUser(String userId, java.util.function.Function<String, ToolExecution> fn) {
        if (userId == null || userId.isBlank()) {
            return error("LOGIN_REQUIRED", "Nguoi dung chua dang nhap. Hay bao khach dang nhap de xem thong tin nay.");
        }
        return fn.apply(userId);
    }

    private Map<String, Object> searchProducts(String query, Double maxPrice) {
        List<Product> products;
        if (query == null || query.isBlank()) {
            products = productCatalogService.getAllProducts();
        } else {
            products = productCatalogService.searchProductByName(query.trim());
            if (products.isEmpty()) {
                products = filterByNormalizedName(productCatalogService.getAllProducts(), query);
            }
            if (products.isEmpty()) {
                products = filterByCategoryOrManufacturer(query);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> items = new ArrayList<>();
        for (Product product : products) {
            Map<String, Object> row = sanitizeProduct(product, now);
            if (maxPrice != null) {
                Object finalPrice = row.get("finalPrice");
                if (!(finalPrice instanceof Number) || ((Number) finalPrice).doubleValue() > maxPrice) {
                    continue;
                }
            }
            items.add(row);
            if (items.size() >= MAX_PRODUCT_RESULTS) {
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", items.size());
        result.put("products", items);
        if (items.isEmpty()) {
            result.put("message", "Khong tim thay san pham phu hop trong database.");
        }
        return result;
    }

    private ToolExecution getProductDetail(String productId) {
        if (productId == null || productId.isBlank()) {
            return error("INVALID_ARGUMENT", "Thieu productId.");
        }
        try {
            Product product = productCatalogService.getProductById(productId);
            return okDb(sanitizeProduct(product, LocalDateTime.now()));
        } catch (RuntimeException e) {
            return error("NOT_FOUND", "Khong tim thay san pham.");
        }
    }

    private Map<String, Object> getDiscountProducts() {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> events = new ArrayList<>();
        for (DiscountEvent event : discountCatalogService.getAllEvents()) {
            if (!event.isActive()) {
                continue;
            }
            if (event.getStartDate() != null && now.isBefore(event.getStartDate())) {
                continue;
            }
            if (event.getEndDate() != null && now.isAfter(event.getEndDate())) {
                continue;
            }
            List<Map<String, Object>> products = new ArrayList<>();
            if (event.getProductIds() != null) {
                for (String productId : event.getProductIds()) {
                    try {
                        products.add(sanitizeProduct(productCatalogService.getProductById(productId), now));
                    } catch (RuntimeException ignored) {
                        // skip missing product
                    }
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", event.getName());
            row.put("description", event.getDescription());
            row.put("discountPercent", event.getDiscountPercent());
            row.put("products", products);
            events.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("events", events);
        return result;
    }

    private Map<String, Object> getStoreInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", storeProperties.getName());
        info.put("address", storeProperties.getAddress());
        info.put("hotline", storeProperties.getHotline());
        info.put("email", storeProperties.getEmail());
        info.put("chatChannel", storeProperties.getChatChannel());
        info.put("note", "Cau hinh local standalone — khong phai lien he doanh nghiep that.");
        return info;
    }

    private ToolExecution searchKnowledge(String query) {
        if (query == null || query.isBlank()) {
            return error("INVALID_ARGUMENT", "Thieu query.");
        }
        try {
            RagSearchService.SearchDiagnostics diag = ragSearchService.searchWithDiagnostics(query);
            Map<String, Object> result = diag.toToolResult();
            if (ragProperties.isDebug()) {
                log.info(
                        "RAG_TOOL searchKnowledge queryId={} ok={} returned={} aboveThreshold={} queryLen={}",
                        diag.getQueryId(),
                        diag.isOk(),
                        diag.getReturnedChunks().size(),
                        diag.getAboveThresholdCount(),
                        query.length()
                );
            }
            boolean ok = Boolean.TRUE.equals(result.get("ok"));
            if (!ok) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("ok", false);
                payload.put("sourceType", "RAG");
                payload.put("error", "RAG_EMPTY");
                payload.put("queryId", result.get("queryId"));
                payload.put("message", String.valueOf(result.getOrDefault("message", "Khong co tai lieu.")));
                payload.put("chunks", result.getOrDefault("chunks", List.of()));
                return new ToolExecution(payload);
            }
            Object count = result.get("count");
            if (count instanceof Number && ((Number) count).intValue() == 0) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("ok", false);
                payload.put("sourceType", "RAG");
                payload.put("error", "RAG_NO_MATCH");
                payload.put("queryId", result.get("queryId"));
                payload.put("message", String.valueOf(result.getOrDefault("message", "Khong tim thay doan tai lieu lien quan.")));
                payload.put("chunks", List.of());
                return new ToolExecution(payload);
            }
            return okRag(result);
        } catch (Exception e) {
            return error("RAG_ERROR", "Loi tim tai lieu: " + e.getMessage());
        }
    }

    private ToolExecution getMyProfile(String userId) {
        User user = userAccountService.getUserById(userId);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("fullName", user.getFullName());
        profile.put("email", user.getEmail());
        profile.put("phoneNumber", user.getPhoneNumber());
        profile.put("gender", user.getGender());
        profile.put("birthDate", user.getBirthDate());
        profile.put("username", user.getUsername());
        return okDb(profile);
    }

    private ToolExecution getMyOrders(String userId) {
        List<Map<String, Object>> orders = new ArrayList<>();
        for (Bill bill : orderQueryService.getUserBills(userId)) {
            orders.add(sanitizeBill(bill, false));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", orders.size());
        result.put("orders", orders);
        return okDb(result);
    }

    private ToolExecution getMyOrderDetail(String userId, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return error("INVALID_ARGUMENT", "Thieu orderId.");
        }
        try {
            Bill bill = orderQueryService.getBillById(orderId);
            if (bill.getUserId() == null || !bill.getUserId().equals(userId)) {
                return error("FORBIDDEN", "Khong tim thay don hang cua ban.");
            }
            return okDb(sanitizeBill(bill, true));
        } catch (RuntimeException e) {
            return error("FORBIDDEN", "Khong tim thay don hang cua ban.");
        }
    }

    private ToolExecution getMyCart(String userId) {
        ShoppingCart cart = cartQueryService.getShoppingCart(userId);
        List<Map<String, Object>> items = new ArrayList<>();
        if (cart != null && cart.getItems() != null) {
            LocalDateTime now = LocalDateTime.now();
            cart.getItems().forEach((productId, quantity) -> {
                try {
                    Map<String, Object> row = sanitizeProduct(productCatalogService.getProductById(productId), now);
                    row.put("cartQuantity", quantity);
                    items.add(row);
                } catch (RuntimeException ignored) {
                    // skip missing product
                }
            });
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itemCount", items.size());
        result.put("items", items);
        return okDb(result);
    }

    private ToolExecution getMyAddresses(String userId) {
        List<Map<String, Object>> addresses = new ArrayList<>();
        for (ShippingAddress address : addressQueryService.getAllShippingAddressByUserId(userId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recipientName", address.getRecipientName());
            row.put("addressLine", address.getAddressLine());
            row.put("district", address.getDistrict());
            row.put("city", address.getCity());
            addresses.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("addresses", addresses);
        return okDb(result);
    }

    private Map<String, Object> sanitizeProduct(Product product, LocalDateTime now) {
        int eventDiscount = discountCatalogService.getBestEventDiscountPercent(product.getId(), now);
        double finalPrice = discountCatalogService.calculateFinalPrice(
                product.getPrice(), product.getPercentDiscount(), eventDiscount);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productId", product.getId());
        row.put("name", product.getName());
        row.put("description", truncate(product.getDescription(), 220));
        row.put("price", product.getPrice());
        row.put("originalPrice", product.getOriginalPrice());
        row.put("percentDiscount", product.getPercentDiscount());
        row.put("eventDiscountPercent", eventDiscount);
        row.put("finalPrice", finalPrice);
        row.put("quantity", product.getQuantity());
        row.put("inStock", product.getQuantity() > 0);
        Categories category = product.getCategories();
        row.put("categoryName", category != null ? category.getName() : null);
        Manufacturer manufacturer = product.getManufacturer();
        row.put("manufacturerName", manufacturer != null ? manufacturer.getName() : null);
        row.put("ragDocumentKey", product.getRagDocumentKey());
        return row;
    }

    private Map<String, Object> sanitizeBill(Bill bill, boolean detailed) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderId", bill.getId());
        row.put("totalAmount", bill.getTotalAmount());
        row.put("orderStatus", OrderStatusConstants.getDescription(
                bill.getOderStatus() == null ? -1 : bill.getOderStatus()));
        row.put("paymentStatus", PaymentStatusConstants.getDescription(
                bill.getPaymentStatus() == null ? -1 : bill.getPaymentStatus()));
        row.put("paymentMethod", PaymentMethodConstants.getDescription(
                bill.getPaymentMethod() == null ? -1 : bill.getPaymentMethod()));
        row.put("createdAt", bill.getCreatedAt() != null ? bill.getCreatedAt().toString() : null);
        if (detailed && bill.getProducts() != null) {
            List<Map<String, Object>> items = new ArrayList<>();
            bill.getProducts().forEach((productId, quantity) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("productId", productId);
                item.put("quantity", quantity);
                try {
                    item.put("name", productCatalogService.getProductById(productId).getName());
                } catch (RuntimeException e) {
                    item.put("name", null);
                }
                items.add(item);
            });
            row.put("items", items);
            if (bill.getShippingAddress() != null) {
                Map<String, Object> shipping = new LinkedHashMap<>();
                shipping.put("recipientName", bill.getShippingAddress().getRecipientName());
                shipping.put("addressLine", bill.getShippingAddress().getAddressLine());
                shipping.put("district", bill.getShippingAddress().getDistrict());
                shipping.put("city", bill.getShippingAddress().getCity());
                row.put("shipping", shipping);
            }
        }
        return row;
    }

    private List<Product> filterByNormalizedName(List<Product> products, String query) {
        String needle = normalize(query);
        List<Product> matched = new ArrayList<>();
        for (Product product : products) {
            if (product.getName() != null && normalize(product.getName()).contains(needle)) {
                matched.add(product);
            }
        }
        return matched;
    }

    private List<Product> filterByCategoryOrManufacturer(String query) {
        String needle = normalize(query);
        List<Product> matched = new ArrayList<>();
        for (Categories category : categoriesCatalogService.getAllCategories()) {
            if (category.getName() != null && normalize(category.getName()).contains(needle)) {
                matched.addAll(productCatalogService.getProductByCategories(category.getId()));
            }
        }
        for (Manufacturer manufacturer : manufacturerCatalogService.getAllManufacturers()) {
            if (manufacturer.getName() != null && normalize(manufacturer.getName()).contains(needle)) {
                matched.addAll(productCatalogService.getProductByManufacturer(manufacturer.getId()));
            }
        }
        return matched;
    }

    private static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String noAccent = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        noAccent = noAccent.replace("đ", "d").replace("Đ", "d");
        return noAccent.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private static ToolExecution okDb(Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("sourceType", "DB");
        payload.put("data", data);
        return new ToolExecution(payload);
    }

    private static ToolExecution okRag(Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("sourceType", "RAG");
        payload.put("data", data);
        return new ToolExecution(payload);
    }

    private static ToolExecution error(String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", false);
        payload.put("error", code);
        payload.put("message", message);
        return new ToolExecution(payload);
    }

    private static Map<String, Object> fn(String name, String description,
                                          Map<String, Object> properties, List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            parameters.put("required", required);
        }
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put("name", name);
        declaration.put("description", description);
        declaration.put("parameters", parameters);
        return declaration;
    }

    private static Map<String, Object> prop(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static String str(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null) {
            return null;
        }
        return String.valueOf(args.get(key));
    }

    private static Double num(Map<String, Object> args, String key) {
        if (args == null || args.get(key) == null || "".equals(args.get(key))) {
            return null;
        }
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
