package com.respiroc.shopifyreaisync.repository

import com.respiroc.shopifyreaisync.model.ReaiConnection
import com.respiroc.shopifyreaisync.model.ShopifyInstallation
import org.springframework.data.jpa.repository.JpaRepository

interface ReaiConnectionRepository : JpaRepository<ReaiConnection, Int> {
    fun findByShopifyInstallation(shopifyInstallation: ShopifyInstallation): ReaiConnection?
    fun findByTenantId(tenantId: Long): List<ReaiConnection>
}
