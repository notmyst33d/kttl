package me.myst33d.kttl

import me.myst33d.io.File

fun main(args: Array<String>) {
    if (args.size < 3) {
        println("Usage: kttl [source] [package namespace] [output folder]")
        return
    }

    val data = File.readFileAsString(args[0])?.replace("\r\n", "\n")?.split("\n")
    if (data != null) {
        val parser = TLParser()
        val schema = parser.parse(data)
        val compiler = TLCompiler(schema, args[1], args[2])
        compiler.compile()
    } else {
        println("Error: Cannot read file")
    }

    println("Successfully compiled the schema")
}
