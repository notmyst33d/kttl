package me.myst33d.kttl

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

fun main(args: Array<String>) {
    if (args.size < 3) {
        println("Usage: kttl [source] [package namespace] [output folder]")
        return
    }

    println("Compiling schema")
    val readStart = System.currentTimeMillis()
    val data = File(args[0]).readText(Charsets.UTF_8).replace("\r\n", "\n").split("\n")
    println("Read schema in ${System.currentTimeMillis() - readStart} ms")

    val parseStart = System.currentTimeMillis()
    val parser = TLParser()
    val schema = parser.parse(data)
    println("Parsed schema in ${System.currentTimeMillis() - parseStart} ms")

    val compileStart = System.currentTimeMillis()
    val compiler = TLCompiler(schema, args[1], args[2])
    compiler.compile()
    println("Compiled schema in ${System.currentTimeMillis() - compileStart} ms")
}
