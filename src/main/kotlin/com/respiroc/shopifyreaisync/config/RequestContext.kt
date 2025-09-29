package com.respiroc.shopifyreaisync.config

object RequestContext {
    private val tenantIdHolder = ThreadLocal<Long>()
    fun setTenantId(tenantId: Long?) {
        if (tenantId != null) {
            tenantIdHolder.set(tenantId)
        } else {
            tenantIdHolder.remove()
        }
    }


    fun clear() {
        tenantIdHolder.remove()
    }
}
