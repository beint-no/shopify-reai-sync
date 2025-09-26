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
    fun persistInstallation(shopDomain: String, accessToken: String, scopes: String): ShopifyInstallation {
        val normalizedDomain = shopDomain.lowercase()
        val existingInstallation = shopifyInstallationRepository.findByShopDomain(normalizedDomain)
        val currentTimestamp = OffsetDateTime.now(ZoneOffset.UTC)
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
}