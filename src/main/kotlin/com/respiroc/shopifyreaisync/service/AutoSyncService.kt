package com.respiroc.shopifyreaisync.service

import com.respiroc.shopifyreaisync.dto.order.OrderSyncRequest
import com.respiroc.shopifyreaisync.dto.product.ProductSyncRequest
import com.respiroc.shopifyreaisync.repository.ReaiConnectionRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class AutoSyncService(
    private val reaiConnectionRepository: ReaiConnectionRepository,
    private val reaiProductSyncService: ReaiProductSyncService,
    private val reaiOrderSyncService: ReaiOrderSyncService,
    private val shopifyProductService: ShopifyProductService,
    private val shopifyOrderService: ShopifyOrderService
) {

    @Scheduled(cron = "0 0 0 * * *")
    fun syncAll() {
        val connections = reaiConnectionRepository.findAll()
        connections.forEach { connection ->
            if (connection.autoSync) {
                val shopDomain = connection.shopifyInstallation?.shopDomain
                val tenantId = connection.tenantId
                if (shopDomain != null && tenantId != null) {
                    shopifyProductService.fetchAllProducts(shopDomain, tenantId) { product ->
                        val request = ProductSyncRequest(
                            shopDomain = shopDomain,
                            productGid = product.productGid,
                            tenantId = tenantId
                        )
                        reaiProductSyncService.syncProduct(request)
                    }

                    shopifyOrderService.fetchAllOrders(shopDomain, tenantId) { order ->
                        val request = OrderSyncRequest(
                            shopDomain = shopDomain,
                            orderNumber = order.orderNumber,
                            tenantId = tenantId
                        )
                        reaiOrderSyncService.syncOrder(request)
                    }
                }
            }
        }
    }
}
