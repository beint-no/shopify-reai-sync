package com.respiroc.shopifyreaisync.controller

import com.respiroc.shopifyreaisync.model.ReaiConnection
import com.respiroc.shopifyreaisync.config.RequestContext
import com.respiroc.shopifyreaisync.service.CookieService
import com.respiroc.shopifyreaisync.service.OrderNotFoundException
import com.respiroc.shopifyreaisync.service.ReaiConnectionService
import com.respiroc.shopifyreaisync.service.ReaiOrderSyncService
import com.respiroc.shopifyreaisync.service.ReaiSyncResult
import com.respiroc.shopifyreaisync.service.ShopifyOrderService
import com.respiroc.shopifyreaisync.service.ShopifyInstallationService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.OffsetDateTime
import kotlin.math.log

@Controller
class OrderSearchController(
    private val shopifyInstallationService: ShopifyInstallationService,
    private val shopifyOrderService: ShopifyOrderService,
    private val reaiConnectionService: ReaiConnectionService,
    private val reaiOrderSyncService: ReaiOrderSyncService,
    private val cookieService: CookieService
    ) {
    @GetMapping("/")
    fun home(
        @RequestParam(value = "shop", required = false) shopDomain: String?,
        @RequestParam(value = "installed", required = false) installed: String?,
        @RequestParam(value = "error", required = false) errorMessage: String?,
        @RequestParam(value = "access_token", required = false) accessTokenParam: String?,
        @RequestParam(value = "reaiConnected", required = false) reaiConnectedFlag: String?,
        model: Model,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): String {
        val normalizedShop = shopDomain?.lowercase()
        var reaiConnection: ReaiConnection? = null
        var reaiConnected = false
        var currentTenantId: Long? = null
        var currentAccessToken: String? = null

        if (!accessTokenParam.isNullOrBlank()) {
            try {
                reaiConnection = reaiConnectionService.storeAccessToken(accessTokenParam, normalizedShop)
                currentTenantId = reaiConnection.tenantId
                currentAccessToken = accessTokenParam
                reaiConnected = reaiConnectionService.hasValidToken(reaiConnection)
                if (reaiConnected) {
                    currentAccessToken?.let { cookieService.setCookie(request, response, "reai_access_token", it, 3600, true, "Lax") }
                    RequestContext.setTenantId(currentTenantId)
                }
            } catch (e: Exception) {
            }
        } else {
            val cookieAccessToken = cookieService.getCookie(request, "reai_access_token")

            if (!cookieAccessToken.isNullOrBlank()) {
                try {
                    reaiConnection = reaiConnectionService.storeAccessToken(cookieAccessToken, null)
                    currentTenantId = reaiConnection.tenantId
                    RequestContext.setTenantId(currentTenantId)
                    reaiConnected = reaiConnectionService.hasValidToken(reaiConnection)
                } catch (e: Exception) {
                    cookieService.clearCookie(response, "reai_access_token")
                }
            }
        }


        model.addAttribute("reaiConnected", reaiConnected)
        model.addAttribute("currentTenantId", currentTenantId)
        model.addAttribute("errorMessage", errorMessage)

        if (!reaiConnected) {
            model.addAttribute("message", "You should connect from ReAI to use the app.")
            return "index"
        }

        val installationsForTenant = if (currentTenantId != null) {
            shopifyInstallationService.findByReaiConnectionTenantId(currentTenantId)
        } else {
            emptyList()
        }

        model.addAttribute("installationsForTenant", installationsForTenant)

        val shopifyStoreLinked = installationsForTenant.isNotEmpty()
        model.addAttribute("shopifyStoreLinked", shopifyStoreLinked)

        if (!shopifyStoreLinked) {
            model.addAttribute("promptInstallShopify", true)
        } else {
            model.addAttribute("promptInstallShopify", false)
        }
        val allInstallations = shopifyInstallationService.findAll()
        val reaiStatuses = allInstallations.associate { installation ->
            val connection = reaiConnectionService.findByShopifyInstallation(installation)
            installation.shopDomain to ReaiConnectionStatus(
                connected = reaiConnectionService.hasValidToken(connection),
                expiresAt = connection?.accessTokenExpiresAt,
                tenantId = connection?.tenantId
            )
        }
        model.addAttribute("installations", allInstallations)
        model.addAttribute("reaiStatuses", reaiStatuses)


        model.addAttribute("selectedShop", normalizedShop)
        model.addAttribute("installedShop", if (installed == "1") normalizedShop else null)
        model.addAttribute("orderNumber", "")
        model.addAttribute("reaiRecentlyConnected", reaiConnectedFlag == "1")
        return "index"
    }

    @PostMapping("/orders/search")
    fun searchOrder(
        @RequestParam("shop") shopDomain: String,
        @RequestParam("orderNumber") orderNumber: String,
        @RequestParam("tenant_id") tenantId: Long,
        model: Model
    ): String {
        RequestContext.setTenantId(tenantId)

        return try {
            val orderDetails = shopifyOrderService.fetchOrderByNumber(shopDomain, orderNumber,tenantId)
            model.addAttribute("orderDetails", orderDetails)
            model.addAttribute("orderNumber", orderNumber)
            model.addAttribute("selectedShop", shopDomain)
            model.addAttribute("tenantId", tenantId)
            val connection = reaiConnectionService.findByShopDomainAndTenantId(shopDomain, tenantId)
                ?: throw IllegalArgumentException("ReAI connection not found for shop: $shopDomain and tenant: $tenantId")
            model.addAttribute("reaiConnectionConfigured", reaiConnectionService.hasValidToken(connection))
            model.addAttribute("reaiSyncStatus", reaiOrderSyncService.findSyncRecord(shopDomain, orderDetails.orderNumber, tenantId))
            "orders/search-result :: orderResult"
        } catch (exception: OrderNotFoundException) {
            model.addAttribute("orderNumber", orderNumber)
            model.addAttribute("selectedShop", shopDomain)
            model.addAttribute("tenantId", tenantId)
            model.addAttribute("orderNotFound", true)
            "orders/search-result :: orderResult"
        }
    }

    @PostMapping("/orders/sync-reai")
    fun syncOrder(
        @RequestParam("shop") shopDomain: String,
        @RequestParam("orderNumber") orderNumber: String,
        @RequestParam("tenantId") tenantId: Long,
        model: Model
    ): String {
        val result = reaiOrderSyncService.syncOrder(shopDomain, orderNumber, tenantId)
        model.addAttribute("selectedShop", shopDomain)
        enrichModelWithSyncResult(model, result)
        return "orders/search-result :: orderResult"
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleValidationErrors(exception: IllegalArgumentException, model: Model): String {
        model.addAttribute("validationError", exception.message)
        model.addAttribute("orderNumber", "")
        return "orders/search-result :: orderResult"
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIntegrationErrors(exception: IllegalStateException, model: Model): String {
        model.addAttribute("technicalError", exception.message)
        model.addAttribute("orderNumber", "")
        model.addAttribute("reaiConnectionConfigured", false)
        return "orders/search-result :: orderResult"
    }

    private fun enrichModelWithSyncResult(model: Model, result: ReaiSyncResult) {
        model.addAttribute("orderDetails", result.orderDetails)
        model.addAttribute("orderNumber", result.orderDetails.orderNumber)
        model.addAttribute("reaiConnectionConfigured", true)
        model.addAttribute("reaiSyncStatus", result.status)
    }

    @PostMapping("/shopify/disconnect")
    fun disconnectShopify(@RequestParam("tenant_id") tenantId: Long, redirectAttributes: RedirectAttributes, request: HttpServletRequest, response: HttpServletResponse): String {
        reaiConnectionService.disconnectShopifyInstallation(tenantId)
        redirectAttributes.addAttribute("message", "Shopify store disconnected successfully!")
        RequestContext.clear()
        return "redirect:/"
    }

}

data class ReaiConnectionStatus(
    val connected: Boolean,
    val expiresAt: OffsetDateTime?,
    val tenantId: Long?
)
