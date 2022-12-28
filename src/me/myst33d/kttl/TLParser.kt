package me.myst33d.kttl

class TLParser {
    private fun parseGeneric(source: List<String>): TLConstructor {
        val hash = source[0].split("#")[1]
        val args = mutableListOf<TLArgument>()
        val type = TLType(source.last().replace(";", ""), optional = false, generic = false)

        for (arg in source.slice(1 until source.indexOf("="))) {
            var name = arg.split(":").last()
            var optional = false
            var generic = false
            val genericChildren = mutableListOf<TLType>()
            var flags: String? = null
            var flagsBit: Int? = null
            if (name.contains("?")) {
                val optionalType = name.split("?")
                val optionalData = optionalType[0].split(".")
                name = optionalType[1]
                optional = true
                flags = optionalData[0]
                flagsBit = optionalData[1].toInt()
            }

            if (name.contains("<")) {
                for (genericName in name.substring(name.indexOf("<"), name.indexOf(">")).replace(" ", "").split(",")) {
                    genericChildren += TLType(genericName, optional = false, generic = false)
                }
                generic = true
                name = name.substring(0, name.indexOf("<"))
            }

            args += TLArgument(arg.split(":")[0], TLType(name, optional, generic, genericChildren, flags, flagsBit))
        }

        val name: String
        val namespace: String
        val nameSplit = source[0].split("#")[0].split(".")
        if (nameSplit.size == 2) {
            name = nameSplit[1]
            namespace = nameSplit[0]
        } else {
            name = nameSplit[0]
            namespace = "base"
        }

        return TLConstructor(name, hash, args, type, namespace)
    }

    private fun parseConstructor(source: List<String>): TLConstructor {
        return parseGeneric(source)
    }

    private fun parseFunction(source: List<String>): TLFunction {
        val data = parseGeneric(source)
        return TLFunction(data.name, data.hash, data.args, data.type, data.namespace)
    }

    fun parse(source: List<String>): TLSchema {
        var section: String? = null

        val types = mutableListOf<TLType>()
        val constructors = mutableListOf<TLConstructor>()
        val functions = mutableListOf<TLFunction>()
        val namespaces = mutableListOf<String>()

        // Parse the source
        for (line in source) {
            if (line.isEmpty() || line.startsWith("//")) continue
            if (line.startsWith("---")) {
                section = line.replace("---", "")
                continue
            }

            when (section) {
                "types" -> {
                    try {
                        constructors += parseConstructor(line.split(" "))
                    } catch (e: Exception) {
                        println("Warning: Failed to parse constructor: $line ($e)")
                    }
                }
                "functions" -> {
                    try {
                        functions += parseFunction(line.split(" "))
                    } catch (e: Exception) {
                        println("Warning: Failed to parse function: $line ($e)")
                    }
                }
                else -> {
                    println("Warning: Unknown section type \"$section\"")
                }
            }
        }

        // Fill the types and namespaces
        for (constructor in constructors) {
            types += constructor.type
            if (!namespaces.contains(constructor.namespace)) {
                namespaces += constructor.namespace
            }
        }
        for (function in functions) {
            types += function.type
            if (!namespaces.contains(function.namespace)) {
                namespaces += function.namespace
            }
        }

        return TLSchema(types, constructors, functions, namespaces)
    }
}
