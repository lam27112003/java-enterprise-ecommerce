package com.ecommerce.platform.cart.service;

import com.ecommerce.platform.cart.domain.Cart;
import com.ecommerce.platform.cart.domain.CartItem;
import com.ecommerce.platform.cart.dto.CartDtos.AddCartItemRequest;
import com.ecommerce.platform.cart.dto.CartDtos.CartItemResponse;
import com.ecommerce.platform.cart.dto.CartDtos.CartResponse;
import com.ecommerce.platform.cart.dto.CartDtos.UpdateCartItemRequest;
import com.ecommerce.platform.cart.exception.CartExceptions;
import com.ecommerce.platform.cart.repository.CartRepository;
import com.ecommerce.platform.catalog.domain.Product;
import com.ecommerce.platform.catalog.domain.ProductStatus;
import com.ecommerce.platform.catalog.repository.ProductRepository;
import com.ecommerce.platform.inventory.service.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    public CartService(
            CartRepository cartRepository,
            ProductRepository productRepository,
            InventoryService inventoryService) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> new Cart(UUID.randomUUID(), userId));
        if (cart.getCreatedAt() == null) {
            // unsaved empty cart view
            return emptyCart(userId, cart.getId());
        }
        return toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(UUID userId, AddCartItemRequest request) {
        if (request.quantity() == null || request.quantity() < 1) {
            throw CartExceptions.invalidQuantity();
        }
        Product product = requirePurchasableProduct(request.productId());
        Cart cart = getOrCreateCart(userId);
        CartItem existing = cart.findItemByProduct(product.getId()).orElse(null);
        int newQty = request.quantity() + (existing == null ? 0 : existing.getQuantity());
        assertAvailability(product.getId(), newQty);
        if (existing == null) {
            CartItem item = new CartItem(
                    UUID.randomUUID(),
                    cart,
                    product.getId(),
                    request.quantity(),
                    product.getPriceCents(),
                    product.getCurrency()
            );
            cart.addItem(item);
        } else {
            existing.setQuantity(newQty);
            existing.refreshUnitPrice(product.getPriceCents(), product.getCurrency());
            cart.touch();
        }
        cartRepository.save(cart);
        return toResponse(cartRepository.findByUserId(userId).orElse(cart));
    }

    @Transactional
    public CartResponse updateItem(UUID userId, UUID itemId, UpdateCartItemRequest request) {
        if (request.quantity() == null || request.quantity() < 1) {
            throw CartExceptions.invalidQuantity();
        }
        Cart cart = requireOwnedCart(userId);
        CartItem item = cart.findItem(itemId).orElseThrow(CartExceptions::itemNotFound);
        Product product = requirePurchasableProduct(item.getProductId());
        assertAvailability(product.getId(), request.quantity());
        item.setQuantity(request.quantity());
        // D6: keep existing unit price snapshot on qty-only update
        cart.touch();
        cartRepository.save(cart);
        return toResponse(cartRepository.findByUserId(userId).orElse(cart));
    }

    @Transactional
    public CartResponse removeItem(UUID userId, UUID itemId) {
        Cart cart = requireOwnedCart(userId);
        CartItem item = cart.findItem(itemId).orElseThrow(CartExceptions::itemNotFound);
        cart.removeItem(item);
        cartRepository.save(cart);
        return toResponse(cartRepository.findByUserId(userId).orElse(cart));
    }

    @Transactional
    public CartResponse clear(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return emptyCart(userId, UUID.randomUUID());
        }
        cart.clearItems();
        cartRepository.save(cart);
        return toResponse(cart);
    }

    private Cart getOrCreateCart(UUID userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart created = new Cart(UUID.randomUUID(), userId);
            return cartRepository.save(created);
        });
    }

    private Cart requireOwnedCart(UUID userId) {
        return cartRepository.findByUserId(userId).orElseThrow(CartExceptions::cartNotFound);
    }

    private Product requirePurchasableProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(CartExceptions::productNotAvailable);
        if (product.getDeletedAt() != null || product.getStatus() != ProductStatus.ACTIVE) {
            throw CartExceptions.productNotAvailable();
        }
        return product;
    }

    private void assertAvailability(UUID productId, int quantity) {
        inventoryService.ensureInventoryExists(productId);
        long available = inventoryService.getAvailableQuantity(productId);
        if (available < quantity) {
            throw CartExceptions.insufficientStock();
        }
    }

    private CartResponse emptyCart(UUID userId, UUID cartId) {
        return new CartResponse(cartId, userId, List.of(), 0L, "USD", null);
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();
        long subtotal = 0;
        String currency = "USD";
        for (CartItem item : cart.getItems()) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            boolean available = product != null
                    && product.getDeletedAt() == null
                    && product.getStatus() == ProductStatus.ACTIVE
                    && inventoryService.getAvailableQuantity(item.getProductId()) >= item.getQuantity();
            String name = product == null ? null : product.getName();
            String sku = product == null ? null : product.getSku();
            long line = item.lineTotalCents();
            subtotal += line;
            currency = item.getCurrency();
            items.add(new CartItemResponse(
                    item.getId(),
                    item.getProductId(),
                    name,
                    sku,
                    item.getQuantity(),
                    item.getUnitPriceCents(),
                    item.getCurrency(),
                    line,
                    available
            ));
        }
        return new CartResponse(
                cart.getId(),
                cart.getUserId(),
                items,
                subtotal,
                currency,
                cart.getUpdatedAt()
        );
    }
}
