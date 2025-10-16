package com.respiroc.shopifyreaisync.service

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.respiroc.shopifyreaisync.config.ShopifyProperties
import com.respiroc.shopifyreaisync.dto.product.ProductSearchRequest
import com.respiroc.shopifyreaisync.dto.product.ShopifyProductDetails
import com.respiroc.shopifyreaisync.dto.product.ShopifyProductVariantDetails
import com.respiroc.shopifyreaisync.dto.product.ShopifySelectedOption
import com.respiroc.shopifyreaisync.dto.shared.MoneyAmount
import com.respiroc.shopifyreaisync.graphql.GetProductByIdQuery
import com.respiroc.shopifyreaisync.graphql.SearchProductsQuery
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Service

@Service
class ShopifyProductService(
    private val shopifyInstallationService: ShopifyInstallationService,
    private val shopifyProperties: ShopifyProperties
) {
    fun searchProducts(request: ProductSearchRequest, limit: Int = 10): List<ShopifyProductDetails> {
        val installation = shopifyInstallationService.findByShopDomainAndTenantId(request.shopDomain, request.tenantId)
            ?: throw IllegalArgumentException("Shop installation not found for ${request.shopDomain}")
        val sanitizedQuery = request.productName.trim()
        if (sanitizedQuery.isBlank()) {
            throw IllegalArgumentException("Product name is required")
        }
        val graphqlQuery = buildString {
            append("title:*")
            append(escapeGraphqlValue(sanitizedQuery))
            append('*')
        }
        val apolloClient = buildApolloClient(installation.shopDomain, installation.accessToken)
        val response = runBlocking {
            apolloClient.query(SearchProductsQuery(query = graphqlQuery, first = Optional.Present(limit), after = null)).execute()
        }
        if (response.hasErrors()) {
            val aggregatedMessage = response.errors!!.joinToString(",") { it.message }
            throw IllegalStateException("Shopify returned errors: $aggregatedMessage")
        }
        val currencyCode = response.data?.shop?.currencyCode?.rawValue
        val products = response.data?.products?.edges?.mapNotNull { it?.node }
            ?: return emptyList()
        return products.map { node: SearchProductsQuery.Node -> mapSearchProductDetails(node, currencyCode) }
    }

    fun fetchAllProducts(shopDomain: String, tenantId: Long, productConsumer: (ShopifyProductDetails) -> Unit) {
        val installation = shopifyInstallationService.findByShopDomainAndTenantId(shopDomain, tenantId)
            ?: throw IllegalArgumentException("Shop installation not found for $shopDomain")
        val apolloClient = buildApolloClient(installation.shopDomain, installation.accessToken)
        var hasNextPage = true
        var cursor: String? = null

        while (hasNextPage) {
            val response = runBlocking {
                apolloClient.query(SearchProductsQuery(query = "", first = Optional.Present(50), after = cursor)).execute()
            }

            if (response.hasErrors()) {
                val aggregatedMessage = response.errors!!.joinToString(",") { it.message }
                throw IllegalStateException("Shopify returned errors: $aggregatedMessage")
            }

            Thread.sleep(1000)

            val currencyCode = response.data?.shop?.currencyCode?.rawValue
            val products = response.data?.products?.edges?.mapNotNull { it?.node }
                ?: emptyList()
            products.forEach { node: SearchProductsQuery.Node ->
                val productDetails = mapSearchProductDetails(node, currencyCode)
                productConsumer(productDetails)
            }

            hasNextPage = response.data?.products?.pageInfo?.hasNextPage ?: false
            cursor = response.data?.products?.pageInfo?.endCursor
        }
    }

    fun fetchProductById(shopDomain: String, productId: String, tenantId: Long): ShopifyProductDetails {
        val installation = shopifyInstallationService.findByShopDomainAndTenantId(shopDomain, tenantId)
            ?: throw IllegalArgumentException("Shop installation not found for $shopDomain")
        val apolloClient = buildApolloClient(installation.shopDomain, installation.accessToken)
        val response = runBlocking {
            apolloClient.query(GetProductByIdQuery(id = productId)).execute()
        }
        if (response.hasErrors()) {
            val aggregatedMessage = response.errors!!.joinToString(",") { it.message }
            throw IllegalStateException("Shopify returned errors: $aggregatedMessage")
        }
        val product = response.data?.product ?: throw ProductNotFoundException(productId)
        val currencyCode = response.data?.shop?.currencyCode?.rawValue
        return mapProductDetails(product, currencyCode)
    }

    private fun buildApolloClient(shopDomain: String, accessToken: String): ApolloClient {
        return ApolloClient.Builder()
            .serverUrl("https://$shopDomain/admin/api/${shopifyProperties.apiVersion}/graphql.json")
            .addHttpHeader("X-Shopify-Access-Token", accessToken)
            .build()
    }

    private fun mapSearchProductDetails(node: SearchProductsQuery.Node, currencyCode: String?): ShopifyProductDetails {
        val variantDetails = node.variants?.edges.orEmpty()
            .mapNotNull { edge -> edge?.node }
            .mapIndexed { index, variant ->
                val sellingPrice = variant.price?.toString()?.toBigDecimalOrNull()?.let { amount ->
                    val code = currencyCode ?: variant.inventoryItem?.unitCost?.currencyCode?.rawValue
                    code?.let { MoneyAmount(amount = amount, currencyCode = it) }
                }
                val costPrice = variant.inventoryItem?.unitCost?.let { unitCost ->
                    unitCost.amount.toString().toBigDecimalOrNull()?.let { amount ->
                        MoneyAmount(amount = amount, currencyCode = unitCost.currencyCode.rawValue)
                    }
                }
                val selectedOptions = variant.selectedOptions
                    .mapNotNull { option ->
                        val name = option.name.trim().takeIf { it.isNotEmpty() }
                        val value = option.value.trim().takeIf { it.isNotEmpty() }
                        if (name != null && value != null) ShopifySelectedOption(name, value) else null
                    }
                val primaryWarehouseName = variant.inventoryItem?.inventoryLevels?.edges.orEmpty()
                    .firstNotNullOfOrNull { edge ->
                        edge?.node?.location?.name?.trim()?.takeIf { it.isNotEmpty() }
                    }
                ShopifyProductVariantDetails(
                    variantGid = variant.id,
                    title = variant.title,
                    sku = variant.sku,
                    barcode = variant.barcode,
                    sellingPrice = sellingPrice,
                    costPrice = costPrice,
                    inventoryQuantity = variant.inventoryQuantity,
                    position = index,
                    selectedOptions = selectedOptions,
                    warehouseName = primaryWarehouseName
                )
            }
        return ShopifyProductDetails(
            productGid = node.id,
            title = node.title,
            description = node.description,
            variants = variantDetails
        )
    }

    private fun mapProductDetails(node: GetProductByIdQuery.Product, currencyCode: String?): ShopifyProductDetails {
        val variantDetails = node.variants?.edges.orEmpty()
            .mapNotNull { edge -> edge?.node }
            .mapIndexed { index, variant ->
                val sellingPrice = variant.price?.let { price ->
                    price.toString().toBigDecimalOrNull()?.let { amount ->
                        val code = currencyCode ?: variant.inventoryItem?.unitCost?.currencyCode?.rawValue
                        code?.let { MoneyAmount(amount = amount, currencyCode = it) }
                    }
                }
                val costPrice = variant.inventoryItem?.unitCost?.let { unitCost ->
                    unitCost.amount.toString().toBigDecimalOrNull()?.let { amount ->
                        MoneyAmount(amount = amount, currencyCode = unitCost.currencyCode.rawValue)
                    }
                }
                val selectedOptions = variant.selectedOptions
                    .mapNotNull { option ->
                        val name = option.name.trim().takeIf { it.isNotEmpty() }
                        val value = option.value.trim().takeIf { it.isNotEmpty() }
                        if (name != null && value != null) ShopifySelectedOption(name, value) else null
                    }
                val primaryWarehouseName = variant.inventoryItem?.inventoryLevels?.edges.orEmpty()
                    .firstNotNullOfOrNull { edge ->
                        edge?.node?.location?.name?.trim()?.takeIf { it.isNotEmpty() }
                    }
                ShopifyProductVariantDetails(
                    variantGid = variant.id,
                    title = variant.title,
                    sku = variant.sku,
                    barcode = variant.barcode,
                    sellingPrice = sellingPrice,
                    costPrice = costPrice,
                    inventoryQuantity = variant.inventoryQuantity,
                    position = index,
                    selectedOptions = selectedOptions,
                    warehouseName = primaryWarehouseName
                )
            }
        return ShopifyProductDetails(
            productGid = node.id,
            title = node.title,
            description = node.description,
            variants = variantDetails
        )
    }

    private fun escapeGraphqlValue(value: String): String {
        val builder = StringBuilder()
        value.forEach { character ->
            when (character) {
                '\\' -> builder.append("\\")
                '\"' -> builder.append("\"")
                else -> builder.append(character)
            }
        }
        return builder.toString()
    }
}

class ProductNotFoundException(productId: String) : RuntimeException("Product $productId not found")
