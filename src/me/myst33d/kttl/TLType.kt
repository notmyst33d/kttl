package me.myst33d.kttl

data class TLType(
    val name: String,
    val optional: Boolean,
    val generic: Boolean,
    val genericChildren: List<TLType>? = null,
    val flags: String? = null,
    val flagsBit: Int? = null,
)
