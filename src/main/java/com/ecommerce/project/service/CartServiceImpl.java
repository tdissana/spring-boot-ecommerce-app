package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repositories.CartItemRepository;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.util.AuthUtil;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CartServiceImpl implements CartService{

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private AuthUtil authUtil;

    @Autowired
    private ModelMapper modelMapper;

    private Cart createCart() {
        Cart userCart = cartRepository.findCartByEmail(authUtil.loggedInEmail());
        if (userCart != null) {
            return userCart;
        }
        Cart cart = new Cart();
        cart.setTotalPrice(0.0);
        cart.setUser(authUtil.loggedInUser());
        return cartRepository.save(cart);
    }

    private Cart getCurrentUserCart() {
        Cart cart = cartRepository.findCartByEmail(authUtil.loggedInEmail());
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "emailId", authUtil.loggedInEmail());
        }
        return cart;
    }

    @Override
    public CartDTO addProductToCart(Long productId, Integer quantity) {

        Cart cart = createCart();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        CartItem cartItem = cartItemRepository.findCartItemByCartIdAndProductId(cart.getCartId(), productId);

        if (cartItem != null) {
            throw new APIException("Product: " + product.getProductName() + " already exists in the cart!");
        }

        if (product.getQuantity() == 0) {
            throw new APIException(("Product: " + product.getProductName() + " is not available!"));
        }

        if (product.getQuantity() < quantity) {
            throw new APIException(("Please make an order of the " + product.getProductName()
                    + " less than or equal to " + product.getQuantity() + "."));
        }

        CartItem newCartItem = new CartItem();
        newCartItem.setProduct(product);
        newCartItem.setCart(cart);
        newCartItem.setQuantity(quantity);
        newCartItem.setDiscount(product.getDiscount());
        newCartItem.setProductPrice(product.getSpecialPrice());
        cartItemRepository.save(newCartItem);

        //product.setQuantity(product.getQuantity() - quantity);

        cart.setTotalPrice(cart.getTotalPrice() + product.getSpecialPrice() * quantity);
        Cart savedCart = cartRepository.save(cart);
        CartDTO cartDTO = modelMapper.map(savedCart, CartDTO.class);

        List<CartItem> cartItems = savedCart.getCartItems();
        Stream<ProductDTO> productDTOStream = cartItems.stream().map(item -> {
            ProductDTO map = modelMapper.map(item.getProduct(), ProductDTO.class);
            map.setQuantity(item.getQuantity());
            return map;
        });

        cartDTO.setProducts(productDTOStream.toList());

        return cartDTO;
    }

    @Override
    public List<CartDTO> getAllCarts() {
        List<Cart> carts = cartRepository.findAll();
        if (carts.isEmpty()) {
            throw new APIException("No carts exists");
        }

        List<CartDTO> cartDTOs = carts.stream()
                .map(cart -> {
                    CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);
                    List<ProductDTO> products = cart.getCartItems().stream()
                            .map(p -> modelMapper.map(p, ProductDTO.class))
                            .collect(Collectors.toList());
                    cartDTO.setProducts(products);
                    return cartDTO;
                }).collect(Collectors.toList());

        return cartDTOs;
    }

    @Override
    public CartDTO getCart() {
        Cart cart = getCurrentUserCart();
        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

        List<CartItem> cartItems = cart.getCartItems();
        Stream<ProductDTO> productDTOStream = cartItems.stream().map(item -> {
            ProductDTO map = modelMapper.map(item.getProduct(), ProductDTO.class);
            map.setQuantity(item.getQuantity());
            return map;
        });

        cartDTO.setProducts(productDTOStream.toList());

        return cartDTO;
    }

    @Transactional
    @Override
    public CartDTO updateProductQuantityInCart(Long productId, Integer quantity) {
        Cart cart = getCurrentUserCart();
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        if (product.getQuantity() == 0) {
            throw new APIException(("Product: " + product.getProductName() + " is not available!"));
        }

        CartItem cartItem = cartItemRepository.findCartItemByCartIdAndProductId(cart.getCartId(), productId);
        if (cartItem == null) {
            throw new APIException(("Product: " + product.getProductName() + " not available in the cart!"));
        }

        if (product.getQuantity() < cartItem.getQuantity() + quantity) {
            throw new APIException(("Please make an order of the " + product.getProductName()
                    + " less than or equal to " + product.getQuantity() + "."));
        }

        int newQuantity = cartItem.getQuantity() + quantity;
        if (newQuantity < 0) {
            throw new APIException("The resulting quantity cannot be less than zero!");
        }

        if (newQuantity == 0){
            deleteProductFromCart(cart.getCartId(), productId);
        }
        else {
            cartItem.setProductPrice(product.getSpecialPrice());
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
            cartItem.setDiscount(product.getDiscount());
            cart.setTotalPrice(cart.getTotalPrice() + (cartItem.getProductPrice() * quantity));
            cartRepository.save(cart);
        }

        CartItem updatedItem = cartItemRepository.save(cartItem);
        if(updatedItem.getQuantity() == 0){
            cartItemRepository.deleteById(updatedItem.getCartItemId());
        }

        CartDTO cartDTO = modelMapper.map(cart, CartDTO.class);

        List<CartItem> cartItems = cart.getCartItems();

        Stream<ProductDTO> productStream = cartItems.stream().map(item -> {
            ProductDTO productDTO = modelMapper.map(item.getProduct(), ProductDTO.class);
            productDTO.setQuantity(item.getQuantity());
            return productDTO;
        });


        cartDTO.setProducts(productStream.toList());

        return cartDTO;
    }

    @Transactional
    @Override
    public String deleteProductFromCart(Long cartId, Long productId) {

        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));

        CartItem cartItem = cartItemRepository.findCartItemByCartIdAndProductId(cartId, productId);
        if (cartItem == null) {
            throw new APIException(("Product does not exist in the cart!"));
        }

        cart.setTotalPrice(cart.getTotalPrice() - (cartItem.getProductPrice() * cartItem.getQuantity()));
        cartRepository.save(cart);

        cartItemRepository.deleteByCartItemByCartIdAndProductID(cartId, productId);

        return "Deleted product from cart";
    }

    @Override
    public void updateProductInCarts(Long cartId, Long productId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", "cartId", cartId));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "productId", productId));

        CartItem cartItem = cartItemRepository.findCartItemByCartIdAndProductId(cartId, productId);

        if (cartItem == null) {
            throw new APIException("Product " + product.getProductName() + " not available in the cart!");
        }

        double cartPrice = cart.getTotalPrice() - (cartItem.getProductPrice() * cartItem.getQuantity());

        cartItem.setProductPrice(product.getSpecialPrice());

        cart.setTotalPrice(cartPrice + (cartItem.getProductPrice() * cartItem.getQuantity()));

        cartItemRepository.save(cartItem);
    }
}
