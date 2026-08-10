package com.tizo.ecommerce.sales.adapter.in.web;

import com.tizo.ecommerce.generated.api.CartApi;
import com.tizo.ecommerce.generated.model.Cart;
import com.tizo.ecommerce.generated.model.UpdateCartItemRequest;
import com.tizo.ecommerce.sales.application.CartService;
import com.tizo.ecommerce.shared.web.RequestIdentityResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CartController implements CartApi {

    private final CartService carts;
    private final CartWebMapper mapper;
    private final RequestIdentityResolver identity;

    public CartController(CartService carts, CartWebMapper mapper, RequestIdentityResolver identity) {
        this.carts = carts;
        this.mapper = mapper;
        this.identity = identity;
    }

    @Override
    public ResponseEntity<Void> deleteCartItem(String productId) {
        carts.delete(identity.customerId(), productId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Cart> getCart() {
        return ResponseEntity.ok(mapper.toResponse(carts.get(identity.customerId())));
    }

    @Override
    public ResponseEntity<Cart> updateCartItem(String productId, UpdateCartItemRequest request) {
        return ResponseEntity.ok(mapper.toResponse(carts.put(identity.customerId(), productId, request.getQuantity())));
    }
}
