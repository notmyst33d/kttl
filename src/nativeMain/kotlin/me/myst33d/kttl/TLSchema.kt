package me.myst33d.kttl

data class TLSchema(
    val types: List<TLType>,
    val constructors: List<TLConstructor>,
    val functions: List<TLFunction>,
    val namespaces: List<String>,
)
