package com.respiroc.shopifyreaisync.dto.product

import com.respiroc.shopifyreaisync.dto.shared.MoneyAmount

data class ShopifyProductDetails(
    val productGid: String,
    val title: String,
    val description: String?,
    val variants: List<ShopifyProductVariantDetails>
)

data class ShopifyProductVariantDetails(
    val variantGid: String,
    val title: String?,
    val sku: String?,
    val barcode: String?,
    val sellingPrice: MoneyAmount?,
    val costPrice: MoneyAmount?,
    val inventoryQuantity: Int?,
    val position: Int,
    val selectedOptions: List<ShopifySelectedOption>,
    val warehouseName: String?
)

data class ShopifySelectedOption(
    val name: String,
    val value: String
)
