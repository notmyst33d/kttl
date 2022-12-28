package me.myst33d.kttl

open class TLSerializable(
    val name: String,
    val hash: String,
    val args: List<TLArgument>,
    val type: TLType,
    val namespace: String,
)
