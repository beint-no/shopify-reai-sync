package com.respiroc.shopifyreaisync.dto.product

import com.respiroc.shopifyreaisync.model.ReaiProductSyncRecord

data class ProductSearchResultView(
    val product: ShopifyProductDetails,
    val syncRecord: ReaiProductSyncRecord?,
    val syncEnabled: Boolean
)
