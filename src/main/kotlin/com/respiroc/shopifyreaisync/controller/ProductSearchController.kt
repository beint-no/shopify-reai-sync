package com.respiroc.shopifyreaisync.controller

import com.respiroc.shopifyreaisync.config.RequestContext
import com.respiroc.shopifyreaisync.dto.product.ProductSearchComparison
import com.respiroc.shopifyreaisync.dto.product.ProductSearchRequest
import com.respiroc.shopifyreaisync.dto.product.ProductSearchResultView
import com.respiroc.shopifyreaisync.dto.product.ProductSyncRequest
import com.respiroc.shopifyreaisync.service.ProductNotFoundException
import com.respiroc.shopifyreaisync.service.ReaiConnectionService
import com.respiroc.shopifyreaisync.service.ReaiProductSyncService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class ProductSearchController(
    private val reaiProductSyncService: ReaiProductSyncService,
    private val reaiConnectionService: ReaiConnectionService
) {
    @PostMapping("/products/search")
    fun searchProducts(
        @RequestParam("shop") shopDomain: String,
        @RequestParam("productName") productName: String,
        @RequestParam("tenant_id") tenantId: Long,
        model: Model
    ): String {
        RequestContext.setTenantId(tenantId)
        val request = ProductSearchRequest(
            shopDomain = shopDomain,
            productName = productName,
            tenantId = tenantId
        )
        val connection = reaiConnectionService.findByShopDomainAndTenantId(shopDomain, tenantId)
        val accessToken = reaiConnectionService.fetchValidAccessToken(connection)
        val hasValidToken = !accessToken.isNullOrBlank()
        val comparisons = reaiProductSyncService.searchProducts(request, accessToken)
        model.addAttribute("productResults", mapToView(comparisons))
        model.addAttribute("productSearchTerm", productName)
        model.addAttribute("selectedShop", shopDomain)
        model.addAttribute("tenantId", tenantId)
        model.addAttribute("reaiConnectionConfigured", hasValidToken)
        model.addAttribute("recentProductSync", null)
        return "products/search-result :: productResult"
    }

    @PostMapping("/products/sync")
    fun syncProduct(
        @RequestParam("shop") shopDomain: String,
        @RequestParam("productGid") productGid: String,
        @RequestParam("productName") productName: String,
        @RequestParam("tenant_id") tenantId: Long,
        model: Model
    ): String {
        RequestContext.setTenantId(tenantId)
        val syncRequest = ProductSyncRequest(
            shopDomain = shopDomain,
            productGid = productGid,
            tenantId = tenantId
        )
        val syncResult = reaiProductSyncService.syncProduct(syncRequest)
        val connection = reaiConnectionService.findByShopDomainAndTenantId(shopDomain, tenantId)
        val accessToken = reaiConnectionService.fetchValidAccessToken(connection)
        val hasValidToken = !accessToken.isNullOrBlank()
        val comparisons = reaiProductSyncService.searchProducts(
            ProductSearchRequest(
                shopDomain = shopDomain,
                productName = productName,
                tenantId = tenantId
            ),
            accessToken
        )
        model.addAttribute("productResults", mapToView(comparisons))
        model.addAttribute("productSearchTerm", productName)
        model.addAttribute("selectedShop", shopDomain)
        model.addAttribute("tenantId", tenantId)
        model.addAttribute("reaiConnectionConfigured", hasValidToken)
        model.addAttribute("recentProductSync", syncResult)
        return "products/search-result :: productResult"
    }

    @ExceptionHandler(ProductNotFoundException::class)
    fun handleProductNotFound(exception: ProductNotFoundException, model: Model): String {
        model.addAttribute("productNotFound", exception.message)
        model.addAttribute("productResults", emptyList<ProductSearchResultView>())
        model.addAttribute("productSearchTerm", "")
        model.addAttribute("selectedShop", null)
        model.addAttribute("tenantId", null)
        model.addAttribute("reaiConnectionConfigured", false)
        model.addAttribute("recentProductSync", null)
        return "products/search-result :: productResult"
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleValidationErrors(exception: IllegalArgumentException, model: Model): String {
        model.addAttribute("validationError", exception.message)
        model.addAttribute("productResults", emptyList<ProductSearchResultView>())
        model.addAttribute("productSearchTerm", "")
        model.addAttribute("selectedShop", null)
        model.addAttribute("tenantId", null)
        model.addAttribute("reaiConnectionConfigured", false)
        model.addAttribute("recentProductSync", null)
        return "products/search-result :: productResult"
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIntegrationErrors(exception: IllegalStateException, model: Model): String {
        model.addAttribute("technicalError", exception.message)
        model.addAttribute("productResults", emptyList<ProductSearchResultView>())
        model.addAttribute("productSearchTerm", "")
        model.addAttribute("selectedShop", null)
        model.addAttribute("tenantId", null)
        model.addAttribute("reaiConnectionConfigured", false)
        model.addAttribute("recentProductSync", null)
        return "products/search-result :: productResult"
    }

    private fun mapToView(comparisons: List<ProductSearchComparison>): List<ProductSearchResultView> {
        return comparisons.map { comparison ->
            ProductSearchResultView(
                product = comparison.product,
                syncRecord = comparison.syncRecord,
                syncEnabled = comparison.syncRequired
            )
        }
    }
}
