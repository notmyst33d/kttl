package me.myst33d.kttl

import kotlin.random.Random
import kotlin.random.nextUInt
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Callable

class TLCompiler(
    private val schema: TLSchema,
    private val packageNamespace: String,
    private val outputPath: String,
) {
    private val templateGenericTlObject = """
    package $packageNamespace

    class [CLASS_NAME] (
    [FIELDS]
    ) : TLObject() {
        companion object {
            const val hash = [HASH]

            fun deserialize(buffer: TLBuffer, hasHash: Boolean = false): [CLASS_NAME] {
                if (hasHash) {
                    buffer.readInt()
                }
    [DESERIALIZATION_CODE]
                return [CLASS_NAME](
    [DESERIALIZATION_FIELDS]
                )
            }
        }

        override fun serialize(): ByteArray {
            val buffer = TLBuffer()
            buffer.writeInt(hash)
    [SERIALIZATION_CODE]
            return buffer.buffer
        }
    }
    """.trimIndent()

    private val templateTlObject = """
    package $packageNamespace

    abstract class TLObject {
        companion object {
            const val hash = 0

            val mapping = { hash: Int, buffer: TLBuffer ->
                when (hash) {
    [MAPPING]
                    else -> throw Exception("Hash ${"$"}hash not found in mapping")
                }
            }

            fun read(data: ByteArray): TLObject {
                val buffer = TLBuffer(data)
                val objHash = buffer.readInt()
                return mapping(objHash, buffer)
            }

            fun readBuffer(buffer: TLBuffer): TLObject {
                val objHash = buffer.readInt()
                return mapping(objHash, buffer)
            }

            fun deserialize(buffer: TLBuffer, hasHash: Boolean = false): TLObject {
                throw Exception("Not implemented")
            }
        }

        abstract fun serialize(): ByteArray
    }
    """.trimIndent()

    private val classTlFlags = """
    package $packageNamespace

    class TLFlags(var flags: Int = 0) {
        private fun intToBoolean(value: Int) = value == 1
        private fun booleanToInt(value: Boolean) = if (value) 1 else 0
        fun setValue(bit: Int, value: Boolean) = flags xor (booleanToInt(!value) shr bit)
        fun getValue(bit: Int) = intToBoolean((flags shr bit) and 1)
    }
    """.trimIndent()

    private val classTlBuffer = """
    package $packageNamespace

    class TLBuffer(var buffer: ByteArray = ByteArray(0)) {
        var position = 0

        fun writeArbitraryBigInteger(data: BigInteger, size: Int) {
            val newBuffer = ByteArray(size)
            for (i in 0 until size) newBuffer[i] = (data shr 8 * i).toByte()
            position += size
            buffer += newBuffer
        }

        fun writeArbitraryLong(data: Long, size: Int) {
            val newBuffer = ByteArray(size)
            for (i in 0 until size) newBuffer[i] = (data shr 8 * i).toByte()
            position += size
            buffer += newBuffer
        }

        fun writeArbitraryBytes(data: ByteArray) {
            position += data.size
            buffer += data
        }

        fun writeByte(data: Byte) = writeArbitraryLong(data.toLong(), 1)
        fun writeUByte(data: UByte) = writeArbitraryLong(data.toLong(), 1)
        fun writeShort(data: Short) = writeArbitraryLong(data.toLong(), 2)
        fun writeUShort(data: UShort) = writeArbitraryLong(data.toLong(), 2)
        fun writeInt(data: Int) = writeArbitraryLong(data.toLong(), 4)
        fun writeUInt(data: UInt) = writeArbitraryLong(data.toLong(), 4)
        fun writeLong(data: Long) = writeArbitraryLong(data, 8)
        fun writeULong(data: ULong) = writeArbitraryLong(data.toLong(), 8)
        fun writeDouble(data: Double) = writeArbitraryLong(data.toBits(), 8)
        fun writeInt128(data: BigInteger) = writeArbitraryBigInteger(data, 16)
        fun writeInt256(data: BigInteger) = writeArbitraryBigInteger(data, 32)

        fun readArbitraryBigInteger(size: Int): BigInteger {
            assert(position + size <= buffer.size)
            val value = BigInteger("0")
            for (i in size - 1 downTo 0) (buffer[i + position].toLong() and 0xff shl i * 8).toBigInteger() or value
            position += size
            return value
        }

        fun readArbitraryLong(size: Int): Long {
            assert(position + size <= buffer.size)
            var value = 0L
            for (i in size - 1 downTo 0) value = (buffer[i + position].toLong() and 0xff shl i * 8) or value
            position += size
            return value
        }

        fun readArbitraryBytes(size: Int): ByteArray {
            assert(position + size <= buffer.size)
            val data = buffer.slice(position until position + size).toByteArray()
            position += size
            return data
        }

        fun readByte() = readArbitraryLong(1).toByte()
        fun readUByte() = readArbitraryLong(1).toUByte()
        fun readShort() = readArbitraryLong(2).toShort()
        fun readUShort() = readArbitraryLong(2).toUShort()
        fun readInt() = readArbitraryLong(4).toInt()
        fun readUInt() = readArbitraryLong(4).toUInt()
        fun readLong() = readArbitraryLong(8)
        fun readULong() = readArbitraryLong(8).toULong()
        fun readDouble() = Double.fromBits(readArbitraryLong(8))
        fun readInt128() = readArbitraryBigInteger(16)
        fun readInt256() = readArbitraryBigInteger(32)
    }
    """.trimIndent()

    private val deserializationCodeGenerators = mapOf(
        "long" to { name: String, type: TLType ->
            if (type.optional) {
                """
                var $name: ${typeCodeGenerators[type.name]!!(type)} = null
                if (${kotlinFieldName(type.flags!!)}.getValue(${type.flagsBit})) {
                    $name = buffer.readLong()
                }
                """
            } else {
                "val $name = buffer.readLong()"
            }
        },
        "#" to { name: String, _: TLType ->
            val dataVariable = "data${Random.nextUInt()}"

            """
            val $dataVariable = buffer.readInt()
            val $name = TLFlags($dataVariable)
            """
        },
    )

    private val serializationCodeGenerators = mapOf(
        "long" to { name: String, type: TLType ->
            if (type.optional) {
                """
                if ($name != null) {
                    buffer.writeLong($name)
                }
                """
            } else {
                "buffer.writeLong($name)"
            }
        },
        "#" to { name: String, _: TLType ->
            "buffer.writeInt($name.flags)"
        },
        "_kttlflags" to { name: String, _: TLType ->
            "val $name = TLFlags()"
        },
        "_kttlflagscheck" to { name: String, type: TLType ->
            """
            if ($name != null) {
                ${type.flags}.setValue(${type.flagsBit}, true)
            }
            """
        },
    )

    private val typeCodeGenerators = mapOf(
        "long" to { type: TLType ->
            if (type.optional) {
                "Long?"
            } else {
                "Long"
            }
        },
        "#" to { _: TLType -> "TLFlags" },
    )

    private fun getFullOutputPath() = "$outputPath/${packageNamespace.replace(".", "/")}"

    private fun getFullOutputFilePath(namespace: String, name: String) =
        "${getFullOutputPath()}/${kotlinClassName(name, namespace)}.kt"

    private fun genericKotlinName(namespace: String, name: String, firstLetterCase: Boolean): String {
        var kotlinName = if (namespace.isEmpty()) {
            name.replaceFirstChar { if (firstLetterCase) it.uppercase() else it.lowercase() }
        } else {
            namespace.replaceFirstChar { if (firstLetterCase) it.uppercase() else it.lowercase() } + name.replaceFirstChar { it.uppercase() }
        }

        var index = kotlinName.indexOf("_")
        while (index > 0) {
            kotlinName =
                kotlinName.substring(0, index) + kotlinName[index + 1].uppercase() + kotlinName.substring(index + 2)
            index = kotlinName.indexOf("_")
        }

        return kotlinName
    }

    private fun kotlinFieldName(name: String) = genericKotlinName("", name, false)

    private fun kotlinClassName(name: String, namespace: String = "") = genericKotlinName(namespace, name, true)

    private fun compileFields(args: List<TLArgument>): String {
        var code = ""

        for (arg in args) {
            if (arg.type.name == "#") continue
            val generator = typeCodeGenerators[arg.type.name]
                ?: throw Exception("Type code generator for type \"${arg.type.name}\" was not found")

            code += if (arg.type.optional) {
                "    val ${kotlinFieldName(arg.name)}: ${generator(arg.type)} = null,\n"
            } else {
                "    val ${kotlinFieldName(arg.name)}: ${generator(arg.type)},\n"
            }
        }

        return code
    }

    private fun compileDeserializationCode(args: List<TLArgument>): String {
        var code = ""
        for (arg in args) {
            val generator = deserializationCodeGenerators[arg.type.name]
                ?: throw Exception("Deserialization code generator for type \"${arg.type.name}\" was not found")
            code += generator(kotlinFieldName(arg.name), arg.type).trimIndent().prependIndent("            ") + "\n"
        }

        return code
    }

    private fun compileDeserializationFields(args: List<TLArgument>) =
        args.joinToString("") {
            if (it.type.name != "#") {
                "                ${kotlinFieldName(it.name)},\n"
            } else {
                ""
            }
        }

    private fun compileSerializationCode(args: List<TLArgument>): String {
        var code = ""
        val flagsGenerator = serializationCodeGenerators["_kttlflags"]!!
        val flagsCheckGenerator = serializationCodeGenerators["_kttlflagscheck"]!!

        for (arg in args) {
            if (arg.type.name == "#") {
                code += flagsGenerator(kotlinFieldName(arg.name), arg.type).trimIndent()
                    .prependIndent("        ") + "\n"
            }
        }

        for (arg in args) {
            if (arg.type.optional) code += flagsCheckGenerator(kotlinFieldName(arg.name), arg.type).trimIndent()
                .prependIndent("        ") + "\n"
        }

        for (arg in args) {
            val generator = serializationCodeGenerators[arg.type.name]
                ?: throw Exception("Serialization code generator for type \"${arg.type.name}\" was not found")
            code += generator(kotlinFieldName(arg.name), arg.type).trimIndent().prependIndent("        ") + "\n"
        }

        return code
    }

    private fun compileMapping(name: String, namespace: String): String {
        val kotlinName = kotlinClassName(name, namespace)
        return "$kotlinName.hash -> $kotlinName.deserialize(buffer)".prependIndent("                ")
    }

    private fun compileGeneric(
        name: String,
        hash: String,
        args: List<TLArgument>,
        type: TLType,
        namespace: String
    ): String {
        var code = templateGenericTlObject

        code = code.replace("[CLASS_NAME]", kotlinClassName(name, namespace))
        code = code.replace("[HASH]", hash.toUInt(16).toInt().toString())

        val fieldsCode = compileFields(args)
        code = code.replace("[FIELDS]", fieldsCode)

        val deserializationCode = compileDeserializationCode(args)
        code = code.replace("[DESERIALIZATION_CODE]", deserializationCode)

        val deserializationFieldsCode = compileDeserializationFields(args)
        code = code.replace("[DESERIALIZATION_FIELDS]", deserializationFieldsCode)

        val serializationCode = compileSerializationCode(args)
        code = code.replace("[SERIALIZATION_CODE]", serializationCode)

        return code
    }

    private fun writeStringToFile(path: String, data: String) {
        val file = File(path)
        file.parentFile.mkdirs()
        file.writeText(data)
    }

    fun compile() {
        val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        val tasks =
            schema.constructors.map {
                Callable {
                    try {
                        writeStringToFile(
                            getFullOutputFilePath(it.namespace, it.name),
                            compileGeneric(it.name, it.hash, it.args, it.type, it.namespace)
                        )
                    } catch (e: Exception) {
                        throw Exception("${it.name}#${it.hash}: ${e.message}")
                    }
                    compileMapping(it.name, it.namespace)
                }
            } + schema.functions.map {
                Callable {
                    try {
                        writeStringToFile(
                            getFullOutputFilePath(it.namespace, it.name),
                            compileGeneric(it.name, it.hash, it.args, it.type, it.namespace)
                        )
                    } catch (e: Exception) {
                        throw Exception("${it.name}#${it.hash}: ${e.message}")
                    }
                    compileMapping(it.name, it.namespace)
                }
            }

        val results = executor.invokeAll(tasks)
        executor.shutdown()

        writeStringToFile(
            "${getFullOutputPath()}/TLObject.kt",
            templateTlObject.replace("[MAPPING]", results.joinToString("\n") { it.get() })
        )
        writeStringToFile("${getFullOutputPath()}/TLBuffer.kt", classTlBuffer)
        writeStringToFile("${getFullOutputPath()}/TLFlags.kt", classTlFlags)
    }
}
