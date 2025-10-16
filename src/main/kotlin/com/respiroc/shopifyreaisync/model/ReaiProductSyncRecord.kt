package com.respiroc.shopifyreaisync.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime

@Entity
@Table(
    name = "reai_product_sync",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_product_sync", columnNames = ["shopify_product_gid", "tenant_id"])
    ]
)
class ReaiProductSyncRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int? = null,
    @Column(name = "shop_domain", nullable = false, length = 255)
    val shopDomain: String,
    @Column(name = "shopify_product_gid", nullable = false, length = 255)
    val shopifyProductGid: String,
    @Column(name = "tenant_id", nullable = false)
    val tenantId: Long,
    @Column(name = "reai_product_id")
    var reaiProductId: Long? = null,
    @Column(name = "product_title")
    var productTitle: String? = null,
    @Column(name = "synced_at", nullable = false)
    var syncedAt: OffsetDateTime
)
