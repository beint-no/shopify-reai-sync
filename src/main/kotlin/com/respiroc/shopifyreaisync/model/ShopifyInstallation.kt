package com.respiroc.shopifyreaisync.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "shopify_installation")
class ShopifyInstallation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,
    @Column(name = "shop_domain", nullable = false, unique = true, length = 255)
    val shopDomain: String,
    @Column(name = "access_token", nullable = false, columnDefinition = "text")
    var accessToken: String,
    @Column(name = "scopes", nullable = false, columnDefinition = "text")
    var scopes: String,
    @Column(name = "installed_at", nullable = false)
    val installedAt: OffsetDateTime,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime
)