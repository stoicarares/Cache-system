package com.project.cacheEvict.impl.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_data")
public class ProductData {

    @Id
    @Column(name = "product_key")
    private String productKey;

    @Column(name = "product_value", length = 1024)
    private String productValue;

    public ProductData() {
    }

    public ProductData(String productKey, String productValue) {
        this.productKey = productKey;
        this.productValue = productValue;
    }

    public String getProductKey() {
        return productKey;
    }

    public void setProductKey(String productKey) {
        this.productKey = productKey;
    }

    public String getProductValue() {
        return productValue;
    }

    public void setProductValue(String productValue) {
        this.productValue = productValue;
    }
}