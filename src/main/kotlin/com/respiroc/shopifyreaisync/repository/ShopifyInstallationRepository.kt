package com.respiroc.shopifyreaisync.repository

import com.respiroc.shopifyreaisync.model.ShopifyInstallation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ShopifyInstallationRepository : JpaRepository<ShopifyInstallation, Int> {
    fun findByShopDomain(shopDomain: String): ShopifyInstallation?

    @Query("SELECT si FROM ShopifyInstallation si JOIN ReaiConnection rc ON si.id = rc.shopifyInstallation.id WHERE rc.tenantId = :tenantId")
    fun findByReaiConnectionTenantId(tenantId: Long): List<ShopifyInstallation>

    @Query("SELECT si FROM ShopifyInstallation si JOIN ReaiConnection rc ON si.id = rc.shopifyInstallation.id WHERE si.shopDomain = :shopDomain AND rc.tenantId = :tenantId")
    fun findByShopDomainAndReaiConnectionTenantId(shopDomain: String, tenantId: Long): ShopifyInstallation?
}