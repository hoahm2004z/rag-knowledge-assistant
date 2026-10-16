package rag_assistant.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
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
import rag_assistant.repository.BillRepository;
import rag_assistant.repository.CategoriesRepository;
import rag_assistant.repository.DiscountEventRepository;
import rag_assistant.repository.ManufacturerRepository;
import rag_assistant.repository.ProductRepository;
import rag_assistant.repository.ShippingAddressRepository;
import rag_assistant.repository.ShoppingCartRepository;
import rag_assistant.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Idempotent local seed for AI Pharmacy Assistant.
 * Only writes to the configured local MongoDB URI — never production.
 * Synthetic users/orders only; catalog aligns with rag-data/products/*.md keys.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD_PLAIN = "DemoUser123!";

    private final SeedProperties seedProperties;
    private final ManufacturerRepository manufacturerRepository;
    private final CategoriesRepository categoriesRepository;
    private final ProductRepository productRepository;
    private final DiscountEventRepository discountEventRepository;
    private final UserRepository userRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final ShoppingCartRepository shoppingCartRepository;
    private final BillRepository billRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.isEnabled()) {
            log.info("Catalog seed disabled (app.seed.enabled=false)");
            return;
        }
        if (!seedProperties.isForce() && productRepository.count() > 0) {
            log.info("Catalog seed skipped — product collection already has {} docs", productRepository.count());
            return;
        }
        Map<String, Object> result = seedAll(seedProperties.isForce());
        log.info("Catalog seed completed: {}", result);
    }

    public Map<String, Object> seedAll(boolean force) {
        if (force) {
            clearBusinessCollections();
        }
        Manufacturer bidiphar = saveManufacturer("mfr-bidiphar", "Bidiphar", "Synthetic manufacturer for local seed");
        Manufacturer traphaco = saveManufacturer("mfr-traphaco", "Traphaco", "Synthetic manufacturer for local seed");
        Manufacturer jj = saveManufacturer("mfr-jj", "Johnson & Johnson", "Synthetic manufacturer for local seed");
        Manufacturer pearlie = saveManufacturer("mfr-pearlie", "Pearlie White / Corlison", "Synthetic manufacturer for local seed");
        Manufacturer foripharm = saveManufacturer("mfr-foripharm", "Foripharm / TW3", "Synthetic manufacturer for local seed");

        Categories digestive = saveCategory("cat-digestive", "Tiêu hóa", "tieu-hoa");
        Categories pain = saveCategory("cat-pain", "Giảm đau", "giam-dau");
        Categories oral = saveCategory("cat-oral", "Chăm sóc răng miệng", "cham-soc-rang-mieng");
        Categories circulation = saveCategory("cat-circulation", "Tuần hoàn", "tuan-hoan");

        List<Product> products = List.of(
                product("prod-berberin-100mg", "Viên nang cứng Berberin 100mg Bidiphar",
                        "Berberin 100mg — dữ liệu catalog seed (giá/tồn local).",
                        bidiphar, digestive, 120, 45000, 39000, 0, "berberin-100mg"),
                product("prod-biragan-650mg", "Biragan 650",
                        "Paracetamol 650mg — catalog seed đồng bộ rag-data/products/biragan-650mg.md.",
                        bidiphar, pain, 80, 55000, 48000, 5, "biragan-650mg"),
                product("prod-boganic-forte", "Boganic Forte",
                        "Boganic Forte — catalog seed.",
                        traphaco, digestive, 60, 120000, 110000, 0, "boganic-forte"),
                product("prod-boganic-premium", "Boganic Premium",
                        "Boganic Premium — catalog seed.",
                        traphaco, digestive, 45, 150000, 140000, 0, "boganic-premium"),
                product("prod-tocimat-120mg", "Tocimat 120mg",
                        "Tocimat 120mg — catalog seed.",
                        bidiphar, digestive, 70, 95000, 89000, 0, "tocimat-120mg"),
                product("prod-listerine-100ml", "Listerine Cool Mint 100ml",
                        "Listerine Cool Mint 100ml — catalog seed.",
                        jj, oral, 200, 65000, 59000, 10, "listerine-cool-mint-100ml"),
                product("prod-listerine-500ml", "Listerine Cool Mint 500ml",
                        "Listerine Cool Mint 500ml — catalog seed.",
                        jj, oral, 90, 145000, 135000, 0, "listerine-cool-mint-500ml"),
                product("prod-fluorinze-750ml", "Fluorinze 750ml",
                        "Fluorinze 750ml — catalog seed.",
                        pearlie, oral, 40, 175000, 165000, 0, "fluorinze-750ml"),
                product("prod-chlor-rinse-250ml", "Chlor-Rinse Plus 250ml",
                        "Chlor-Rinse Plus 250ml — catalog seed.",
                        pearlie, oral, 55, 98000, 92000, 0, "chlor-rinse-plus-250ml"),
                product("prod-hoat-huyet-tw3", "Hoạt huyết thông mạch Gold TW3",
                        "Hoạt huyết thông mạch Gold TW3 — catalog seed.",
                        foripharm, circulation, 35, 210000, 199000, 0, "hoat-huyet-thong-mach-gold-tw3"),
                product("prod-out-of-stock-demo", "Sản phẩm hết hàng (seed demo)",
                        "Dùng để kiểm tra inStock=false. Không có trong RAG.",
                        bidiphar, pain, 0, 30000, 30000, 0, null)
        );
        productRepository.saveAll(products);

        DiscountEvent flash = new DiscountEvent();
        flash.setId("evt-flash-oral");
        flash.setName("Flash sale chăm sóc răng miệng");
        flash.setDescription("Sự kiện khuyến mại seed — chỉ áp dụng sản phẩm oral đã gắn.");
        flash.setStartDate(LocalDateTime.now().minusDays(1));
        flash.setEndDate(LocalDateTime.now().plusDays(14));
        flash.setActive(true);
        flash.setDiscountPercent(15);
        flash.setProductIds(new HashSet<>(Set.of("prod-listerine-100ml", "prod-fluorinze-750ml")));
        flash.setCreatedAt(LocalDateTime.now());
        flash.setUpdatedAt(LocalDateTime.now());
        discountEventRepository.save(flash);

        String hash = passwordEncoder.encode(DEMO_PASSWORD_PLAIN);
        User alice = saveUser("user-alice", "alice_demo", hash, "Nguyen Van Alice",
                "alice.demo@example.com", "0900000001", Set.of("ROLE_USER"));
        User bob = saveUser("user-bob", "bob_demo", hash, "Tran Thi Bob",
                "bob.demo@example.com", "0900000002", Set.of("ROLE_USER"));
        saveUser("user-admin", "admin_demo", hash, "Admin Demo",
                "admin.demo@example.com", "0900000000", Set.of("ROLE_ADMIN", "ROLE_USER"));

        ShippingAddress aliceAddr = saveAddress("addr-alice-1", alice, "Nguyen Van Alice",
                "0900000001", "12 Demo Street", "Demo District", "Ha Noi");
        saveAddress("addr-bob-1", bob, "Tran Thi Bob",
                "0900000002", "34 Sample Road", "Sample District", "Da Nang");

        ShoppingCart aliceCart = new ShoppingCart();
        aliceCart.setId("cart-alice");
        aliceCart.setUser(alice);
        HashMap<String, Integer> cartItems = new HashMap<>();
        cartItems.put("prod-biragan-650mg", 2);
        cartItems.put("prod-listerine-100ml", 1);
        aliceCart.setItems(cartItems);
        shoppingCartRepository.save(aliceCart);

        Bill aliceBill = new Bill();
        aliceBill.setId("bill-alice-001");
        aliceBill.setUser(alice);
        aliceBill.setShippingAddress(aliceAddr);
        HashMap<String, Integer> billItems = new HashMap<>();
        billItems.put("prod-berberin-100mg", 1);
        aliceBill.setProducts(billItems);
        aliceBill.setTotalAmount(39000);
        aliceBill.setPaymentMethod(PaymentMethodConstants.CASH_ON_DELIVERY);
        aliceBill.setPaymentStatus(PaymentStatusConstants.PENDING);
        aliceBill.setOderStatus(OrderStatusConstants.CONFIRMED);
        aliceBill.setCreatedAt(LocalDateTime.now().minusDays(2));
        aliceBill.setUpdatedAt(LocalDateTime.now().minusDays(1));
        aliceBill.setNote("Synthetic order for ownership tests");
        billRepository.save(aliceBill);

        Bill bobBill = new Bill();
        bobBill.setId("bill-bob-001");
        bobBill.setUser(bob);
        bobBill.setShippingAddress(shippingAddressRepository.findById("addr-bob-1").orElse(null));
        HashMap<String, Integer> bobItems = new HashMap<>();
        bobItems.put("prod-boganic-forte", 1);
        bobBill.setProducts(bobItems);
        bobBill.setTotalAmount(110000);
        bobBill.setPaymentMethod(PaymentMethodConstants.VNPAY);
        bobBill.setPaymentStatus(PaymentStatusConstants.PAID);
        bobBill.setOderStatus(OrderStatusConstants.SHIPPING);
        bobBill.setCreatedAt(LocalDateTime.now().minusDays(3));
        bobBill.setUpdatedAt(LocalDateTime.now().minusDays(1));
        bobBill.setPaidAt(LocalDateTime.now().minusDays(3));
        bobBill.setNote("Synthetic order belonging to bob_demo only");
        billRepository.save(bobBill);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("databaseNote", "Local seed only — not production commerce");
        summary.put("manufacturers", manufacturerRepository.count());
        summary.put("categories", categoriesRepository.count());
        summary.put("products", productRepository.count());
        summary.put("discountEvents", discountEventRepository.count());
        summary.put("users", userRepository.count());
        summary.put("addresses", shippingAddressRepository.count());
        summary.put("carts", shoppingCartRepository.count());
        summary.put("bills", billRepository.count());
        summary.put("demoUsernames", List.of("alice_demo", "bob_demo", "admin_demo"));
        summary.put("demoPasswordHint", "See README — DemoUser123! (local only)");
        summary.put("adminNote", "admin_demo has ROLE_ADMIN for /api/admin/seed only");
        return summary;
    }

    private void clearBusinessCollections() {
        billRepository.deleteAll();
        shoppingCartRepository.deleteAll();
        shippingAddressRepository.deleteAll();
        discountEventRepository.deleteAll();
        productRepository.deleteAll();
        categoriesRepository.deleteAll();
        manufacturerRepository.deleteAll();
        userRepository.deleteAll();
        log.warn("Cleared business collections before force seed (rag_chunks untouched)");
    }

    private Manufacturer saveManufacturer(String id, String name, String description) {
        Manufacturer m = new Manufacturer(id, name, null, description);
        return manufacturerRepository.save(m);
    }

    private Categories saveCategory(String id, String name, String slug) {
        Categories c = new Categories(id, name, slug, "Seed category");
        return categoriesRepository.save(c);
    }

    private Product product(String id, String name, String description, Manufacturer mfr, Categories cat,
                            int qty, double original, double price, int percentDiscount, String ragKey) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setDescription(description);
        p.setManufacturer(mfr);
        p.setCategories(cat);
        p.setQuantity(qty);
        p.setOriginalPrice(original);
        p.setPrice(price);
        p.setPercentDiscount(percentDiscount);
        p.setPurchaseCount(0);
        p.setUrlImages(List.of());
        p.setRagDocumentKey(ragKey);
        return p;
    }

    private User saveUser(String id, String username, String passwordHash, String fullName,
                          String email, String phone, Set<String> roles) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setPassword(passwordHash);
        u.setFullName(fullName);
        u.setEmail(email);
        u.setPhoneNumber(phone);
        u.setGender("other");
        u.setBirthDate("1995-01-01");
        u.setIsActive(true);
        u.setRoles(new HashSet<>(roles));
        return userRepository.save(u);
    }

    private ShippingAddress saveAddress(String id, User user, String recipient, String phone,
                                        String line, String district, String city) {
        ShippingAddress a = new ShippingAddress();
        a.setId(id);
        a.setUser(user);
        a.setRecipientName(recipient);
        a.setNumPhone(phone);
        a.setAddressLine(line);
        a.setDistrict(district);
        a.setCity(city);
        return shippingAddressRepository.save(a);
    }
}
