package com.respiroc.shopifyreaisync

import com.respiroc.shopifyreaisync.config.ShopifyProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(ShopifyProperties::class)
class ShopifyReaiSyncApplication

fun main(args: Array<String>) {
    runApplication<ShopifyReaiSyncApplication>(*args)
}
