package com.respiroc.shopifyreaisync.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties(prefix = "shopify")
data class ShopifyProperties(
    val apiKey: String,
    val apiSecret: String,
    val scopes: String = "read_orders,read_customers",
    val apiVersion: String = "2025-07",
    val appBaseUrl: URI
)
