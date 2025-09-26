package com.respiroc.shopifyreaisync.repository

import com.respiroc.shopifyreaisync.model.ShopifyInstallation
import org.springframework.data.jpa.repository.JpaRepository

interface ShopifyInstallationRepository : JpaRepository<ShopifyInstallation, Int> {
    fun findByShopDomain(shopDomain: String): ShopifyInstallation?
}