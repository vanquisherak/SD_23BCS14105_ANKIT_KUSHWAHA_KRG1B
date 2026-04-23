package com.flashsale.controller;

import com.flashsale.dto.CreateProductRequest;
import com.flashsale.entity.Product;
import com.flashsale.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public Product createProduct(@RequestBody @Valid CreateProductRequest request) {
        return productService.create(request);
    }

    @GetMapping
    public List<Product> listProducts() {
        return productService.listAll();
    }
}
