package com.respiroc.shopifyreaisync.dto.reai

enum class ReaiVariantOptionType(val displayName: String) {
    SIZE("Size"),
    SHOE_SIZE("Shoe size"),
    COLOR("Color"),
    VELG_VERDI("Velg verdi"),
    STYLE("Style"),
    LENGTH("Length"),
    INSEAM_LENGTH("Inseam Length");

    companion object {
        private val byDisplayName = entries.associateBy { it.displayName.lowercase() }
        private val byName = entries.associateBy { it.name.lowercase() }

        fun fromDisplayName(value: String): ReaiVariantOptionType? {
            val key = value.trim().lowercase()
            if (key.isEmpty()) return null
            return byDisplayName[key]
        }

        fun fromSerializedKey(value: String): ReaiVariantOptionType? {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return null
            val normalized = trimmed.lowercase()
            return byName[normalized] ?: byDisplayName[normalized]
        }
    }
}
