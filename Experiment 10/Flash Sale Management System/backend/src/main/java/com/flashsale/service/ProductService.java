package com.flashsale.service;

import com.flashsale.dto.CreateProductRequest;
import com.flashsale.entity.Product;
import com.flashsale.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product create(CreateProductRequest request) {
        Product product = new Product();
        product.setSku(request.sku());
        product.setName(request.name());
        product.setTotalStock(request.totalStock());
        product.setRegularPrice(request.regularPrice());
        return productRepository.save(product);
    }

    public List<Product> listAll() {
        return productRepository.findAll();
    }
}
