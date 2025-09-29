package com.respiroc.shopifyreaisync.service

import com.respiroc.shopifyreaisync.repository.ShopifyInstallationRepository
import com.respiroc.shopifyreaisync.model.ShopifyInstallation
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ShopifyInstallationService(
    private val shopifyInstallationRepository: ShopifyInstallationRepository
) {
    @Transactional(readOnly = true)
    fun findByShopDomain(shopDomain: String): ShopifyInstallation? {
        return shopifyInstallationRepository.findByShopDomain(shopDomain.lowercase())
    }

    @Transactional
    fun persistInstallation(shopDomain: String, accessToken: String, scopes: String, tenantId: Long?): ShopifyInstallation {
        val normalizedDomain = shopDomain.lowercase()
        val currentTimestamp = OffsetDateTime.now(ZoneOffset.UTC)

        val existingInstallation = if (tenantId != null) {
            shopifyInstallationRepository.findByShopDomainAndReaiConnectionTenantId(normalizedDomain, tenantId)
        } else {
            shopifyInstallationRepository.findByShopDomain(normalizedDomain)
        }

        return if (existingInstallation != null) {
            existingInstallation.accessToken = accessToken
            existingInstallation.scopes = scopes
            existingInstallation.updatedAt = currentTimestamp
            shopifyInstallationRepository.save(existingInstallation)
        } else {
            val newInstallation = ShopifyInstallation(
                shopDomain = normalizedDomain,
                accessToken = accessToken,
                scopes = scopes,
                installedAt = currentTimestamp,
                updatedAt = currentTimestamp
            )
            shopifyInstallationRepository.save(newInstallation)
        }
    }

    @Transactional(readOnly = true)
    fun findAll(): List<ShopifyInstallation> {
        return shopifyInstallationRepository.findAll().sortedBy { it.shopDomain }
    }

    @Transactional(readOnly = true)
    fun findByReaiConnectionTenantId(tenantId: Long): List<ShopifyInstallation> {
        return shopifyInstallationRepository.findByReaiConnectionTenantId(tenantId)
    }

    @Transactional(readOnly = true)
    fun findByShopDomainAndTenantId(shopDomain: String, tenantId: Long): ShopifyInstallation? {
        return shopifyInstallationRepository.findByShopDomainAndReaiConnectionTenantId(shopDomain.lowercase(), tenantId)
    }

    @Transactional
    fun deleteInstallation(installation: ShopifyInstallation) {
        shopifyInstallationRepository.delete(installation)
    }

}