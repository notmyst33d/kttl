package me.myst33d.io

import kotlinx.cinterop.*
import platform.posix.*

class File {
    companion object {
        val mode644 = S_IRUSR or S_IWUSR or S_IRGRP or S_IROTH
        val mode755 = S_IRWXU or S_IRGRP or S_IXGRP or S_IROTH or S_IXOTH

        fun readFileAsString(path: String): String? {
            val fd = open(path, O_RDONLY)
            if (fd < 0) {
                println("io.File: open returned $errno (${strerror(errno)?.toKString()})");
                return null;
            }

            val size = 4096
            var data = byteArrayOf()
            var bytesRead = size

            // TODO: Code assumes that read() syscall will succeed
            while (bytesRead == size) {
                val buf = nativeHeap.allocArray<ByteVar>(size)
                bytesRead = read(fd, buf, size.toULong()).toInt()
                data += buf.readBytes(size)
            }

            return data.decodeToString()
        }

        fun writeFileAsString(path: String, data: String): Boolean {
            val noFilePath = path.split("/").dropLast(1).joinToString("/")
            if (!ensurePath(noFilePath)) {
                println("io.File: Cannot ensure path $noFilePath")
                return false
            }

            val fd = open(path, O_WRONLY or O_CREAT or O_TRUNC, mode644)
            if (fd < 0) {
                println("io.File: open returned $errno (${strerror(errno)?.toKString()})")
                return false
            }

            data.chunked(4096).forEach {
                val encodedData = it.toString().encodeToByteArray().toCValues()
                val bytesWritten = write(fd, encodedData, encodedData.size.toULong())
                if (bytesWritten != encodedData.size.toLong()) {
                    println("io.File: write returned $errno (${strerror(errno)?.toKString()})")
                    return false
                }
            }

            return true
        }

        private fun ensurePath(path: String): Boolean {
            val pathSplit = path.split("/")
            for (i in pathSplit.indices) {
                val checkPath = pathSplit.slice(0..i).joinToString("/")
                val fd = opendir(checkPath)
                if (fd == null) {
                    if (errno == ENOENT) {
                        mkdir(checkPath, (mode755).toUInt())
                    } else {
                        return false
                    }
                }
            }
            return true
        }
    }
}
