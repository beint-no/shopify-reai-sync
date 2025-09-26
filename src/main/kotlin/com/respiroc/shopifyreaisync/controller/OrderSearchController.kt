package com.respiroc.shopifyreaisync.controller

import com.respiroc.shopifyreaisync.service.OrderNotFoundException
import com.respiroc.shopifyreaisync.service.ShopifyOrderService
import com.respiroc.shopifyreaisync.service.ShopifyInstallationService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class OrderSearchController(
    private val shopifyInstallationService: ShopifyInstallationService,
    private val shopifyOrderService: ShopifyOrderService
) {
    @GetMapping("/")
    fun home(
        @RequestParam(value = "shop", required = false) shopDomain: String?,
        @RequestParam(value = "installed", required = false) installed: String?,
        @RequestParam(value = "error", required = false) errorMessage: String?,
        model: Model
    ): String {
        val installations = shopifyInstallationService.findAll()
        model.addAttribute("installations", installations)
        model.addAttribute("selectedShop", shopDomain?.lowercase())
        model.addAttribute("installedShop", if (installed == "1") shopDomain?.lowercase() else null)
        model.addAttribute("errorMessage", errorMessage)
        model.addAttribute("orderNumber", "")
        return "index"
    }

    @PostMapping("/orders/search")
    fun searchOrder(
        @RequestParam("shop") shopDomain: String,
        @RequestParam("orderNumber") orderNumber: String,
        model: Model
    ): String {
        return try {
            val orderDetails = shopifyOrderService.fetchOrderByNumber(shopDomain, orderNumber)
            model.addAttribute("orderDetails", orderDetails)
            model.addAttribute("orderNumber", orderNumber)
            "orders/search-result :: orderResult"
        } catch (exception: OrderNotFoundException) {
            model.addAttribute("orderNumber", orderNumber)
            model.addAttribute("orderNotFound", true)
            "orders/search-result :: orderResult"
        }
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
        return "orders/search-result :: orderResult"
    }
}