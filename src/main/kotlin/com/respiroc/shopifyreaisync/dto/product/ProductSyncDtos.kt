package com.respiroc.shopifyreaisync.dto.product

import com.respiroc.shopifyreaisync.model.ReaiProductSyncRecord
import com.respiroc.shopifyreaisync.dto.reai.ReaiProductVariantSyncResponse
import java.time.OffsetDateTime

data class ProductSyncResult(
    val productDetails: ShopifyProductDetails,
    val status: ProductSyncStatus
)

data class ProductSyncStatus(
    val reaiProductId: Long,
    val syncedAt: OffsetDateTime,
    val variantMappings: List<ReaiProductVariantSyncResponse>
)

data class ProductSearchComparison(
    val product: ShopifyProductDetails,
    val syncRecord: ReaiProductSyncRecord?,
    val syncRequired: Boolean
)
