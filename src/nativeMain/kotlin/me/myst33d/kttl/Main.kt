/*
 * Copyright (C) 2022 Myst33d
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
