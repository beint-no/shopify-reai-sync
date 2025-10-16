package com.respiroc.shopifyreaisync.controller

import com.respiroc.shopifyreaisync.service.ReaiConnectionService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class StoreController(
    private val reaiConnectionService: ReaiConnectionService
) {
    @PostMapping("/stores/toggle-auto-sync")
    fun toggleAutoSync(
        @RequestParam("tenant_id") tenantId: Long,
        @RequestParam("shop_domain") shopDomain: String
    ): String {
        reaiConnectionService.toggleAutoSync(tenantId, shopDomain)
        return "redirect:/stores"
    }
}
