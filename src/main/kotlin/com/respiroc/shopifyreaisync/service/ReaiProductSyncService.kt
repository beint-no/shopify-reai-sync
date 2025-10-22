package com.respiroc.shopifyreaisync.service

import com.respiroc.shopifyreaisync.dto.product.ProductSearchComparison
import com.respiroc.shopifyreaisync.dto.product.ProductSearchRequest
import com.respiroc.shopifyreaisync.dto.product.ProductSyncRequest
import com.respiroc.shopifyreaisync.dto.product.ProductSyncResult
import com.respiroc.shopifyreaisync.dto.product.ProductSyncStatus
import com.respiroc.shopifyreaisync.dto.product.ShopifyProductDetails
import com.respiroc.shopifyreaisync.dto.reai.ReaiProductSnapshotResponse
import com.respiroc.shopifyreaisync.dto.reai.ReaiProductSyncRequest
import com.respiroc.shopifyreaisync.dto.reai.ReaiProductVariantSyncRequest
import com.respiroc.shopifyreaisync.model.ReaiProductSyncRecord
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException
import java.math.BigDecimal
import java.util.Locale

@Service
class ReaiProductSyncService(
    private val shopifyProductService: ShopifyProductService,
    private val reaiConnectionService: ReaiConnectionService,
    private val reaiProductSyncRecordService: ReaiProductSyncRecordService,
    private val reaiApiClient: ReaiApiClient
) {
    @Transactional(readOnly = true)
    fun searchProducts(
        request: ProductSearchRequest,
        accessToken: String?
    ): List<ProductSearchComparison> {
        val products = shopifyProductService.searchProducts(request)
        return products.map { product ->
            val syncRecord = reaiProductSyncRecordService.findByShopifyProductGidAndTenantId(product.productGid, request.tenantId)
            val syncRequired = determineSyncRequired(product, syncRecord, accessToken)
            ProductSearchComparison(product, syncRecord, syncRequired)
        }
    }

    @Transactional
    fun syncProduct(request: ProductSyncRequest): ProductSyncResult {
        val productDetails = shopifyProductService.fetchProductById(request.shopDomain, request.productGid, request.tenantId)
        val connection = reaiConnectionService.findByShopDomainAndTenantId(request.shopDomain, request.tenantId)
            ?: throw IllegalArgumentException("ReAI connection not configured for ${request.shopDomain}")
        var accessToken = reaiConnectionService.ensureValidAccessToken(connection)
        val existingRecord = reaiProductSyncRecordService.findByShopifyProductGidAndTenantId(request.productGid, request.tenantId)
        val syncRequest = buildSyncRequest(productDetails, existingRecord?.reaiProductId)
        val syncResponse = try {
            reaiApiClient.syncProduct(accessToken, syncRequest)
        } catch (unauthorized: HttpClientErrorException.Unauthorized) {
            accessToken = reaiConnectionService.refreshAccessToken(connection)
            reaiApiClient.syncProduct(accessToken, syncRequest)
        }
        val savedRecord = reaiProductSyncRecordService.saveSyncResult(
            shopDomain = request.shopDomain,
            shopifyProductGid = productDetails.productGid,
            tenantId = request.tenantId,
            reaiProductId = syncResponse.productId,
            productTitle = productDetails.title
        )
        return ProductSyncResult(
            productDetails = productDetails,
            status = ProductSyncStatus(
                reaiProductId = syncResponse.productId,
                syncedAt = savedRecord.syncedAt,
                variantMappings = syncResponse.variants
            )
        )
    }

    private fun buildSyncRequest(
        productDetails: ShopifyProductDetails,
        reaiProductId: Long?
    ): ReaiProductSyncRequest {
        val variantOptionTypes = linkedSetOf<String>()
        val optionNamesByNormalized = linkedMapOf<String, String>()
        val invalidOptionNames = mutableSetOf<String>()
        val variantRequests = productDetails.variants
            .filter { it.sku?.isNotBlank() == true }
            .map { variant ->
                val sku = variant.sku!!.trim()
                val optionValues = linkedMapOf<String, String>()
                variant.selectedOptions.forEach { selectedOption ->
                    val trimmedName = selectedOption.name.trim()
                    if (trimmedName.isEmpty()) {
                        invalidOptionNames.add(selectedOption.name)
                        return@forEach
                    }
                    val normalizedName = trimmedName.lowercase(Locale.ROOT)
                    val canonicalName = optionNamesByNormalized.getOrPut(normalizedName) { trimmedName }
                    val value = selectedOption.value.trim()
                    variantOptionTypes.add(canonicalName)
                    optionValues[canonicalName] = value
                }
                ReaiProductVariantSyncRequest(
                    sku = sku,
                    barcode = variant.barcode?.trim()?.takeIf { it.isNotEmpty() },
                    costPrice = variant.costPrice?.amount ?: BigDecimal.ZERO,
                    sellingPrice = variant.sellingPrice?.amount ?: BigDecimal.ZERO,
                    options = optionValues,
                    inventory = variant.inventoryQuantity,
                    warehouseName = variant.warehouseName?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
        if (variantRequests.isEmpty()) {
            throw IllegalStateException("No variants with SKU found for product ${productDetails.title}")
        }
        if (invalidOptionNames.isNotEmpty()) {
            throw IllegalStateException(
                "Invalid variant option names for product ${productDetails.title}: ${invalidOptionNames.joinToString(", ")}"
            )
        }
        return ReaiProductSyncRequest(
            productId = reaiProductId,
            title = productDetails.title,
            description = productDetails.description?.trim()?.takeIf { it.isNotEmpty() },
            variantOptionTypes = variantOptionTypes,
            variants = variantRequests
        )
    }

    private fun determineSyncRequired(
        productDetails: ShopifyProductDetails,
        syncRecord: ReaiProductSyncRecord?,
        accessToken: String?
    ): Boolean {
        val reaiProductId = syncRecord?.reaiProductId ?: return true
        val validToken = accessToken?.takeIf { it.isNotBlank() } ?: return true
        val syncRequest = try {
            buildSyncRequest(productDetails, reaiProductId)
        } catch (exception: IllegalStateException) {
            return true
        }
        val reaiProduct = try {
            reaiApiClient.fetchProduct(validToken, reaiProductId)
        } catch (exception: RuntimeException) {
            return true
        } ?: return true
        return hasDifferences(syncRequest, reaiProduct)
    }

    private fun hasDifferences(
        syncRequest: ReaiProductSyncRequest,
        reaiProduct: ReaiProductSnapshotResponse
    ): Boolean {
        if (syncRequest.title != reaiProduct.title) {
            return true
        }
        if (normalize(syncRequest.description) != normalize(reaiProduct.description)) {
            return true
        }
        val requestedOptionTypes = syncRequest.variantOptionTypes.map { it.trim() }.toSet()
        val existingOptionTypes = reaiProduct.variantOptionTypes.map { it.trim() }.toSet()
        if (requestedOptionTypes != existingOptionTypes) {
            return true
        }
        val canonicalNamesByNormalized = syncRequest.variantOptionTypes.associateBy { it.lowercase(Locale.ROOT) }
        val requestVariants = syncRequest.variants.associateBy { it.sku }
        val reaiVariants = reaiProduct.variants.associateBy { it.sku }
        if (requestVariants.keys != reaiVariants.keys) {
            return true
        }
        requestVariants.forEach { (sku, requestVariant) ->
            val reaiVariant = reaiVariants.getValue(sku)
            val requestBarcode = normalize(requestVariant.barcode)
            val reaiBarcode = normalize(reaiVariant.barcode)
            if (requestBarcode != reaiBarcode) {
                return true
            }
            val requestCost = requestVariant.costPrice ?: BigDecimal.ZERO
            if (requestCost.compareTo(reaiVariant.costPrice) != 0) {
                return true
            }
            if (requestVariant.sellingPrice.compareTo(reaiVariant.sellingPrice) != 0) {
                return true
            }
            val requestOptions = requestVariant.options
            val reaiOptions = linkedMapOf<String, String>()
            for ((rawType, value) in reaiVariant.options) {
                val trimmedType = rawType.trim()
                val canonicalType = canonicalNamesByNormalized[trimmedType.lowercase(Locale.ROOT)] ?: trimmedType
                reaiOptions[canonicalType] = value.trim()
            }
            if (requestOptions != reaiOptions) {
                return true
            }
            val requestedInventory = requestVariant.inventory
            if (requestedInventory != null) {
                val reaiInventory = reaiVariant.inventoryQuantity
                if (reaiInventory == null || reaiInventory != requestedInventory) {
                    return true
                }
            }
        }
        return false
    }

    private fun normalize(value: String?): String? {
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }
}
