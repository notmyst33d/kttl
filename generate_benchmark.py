schema_template = """
---types---
[CONSTRUCTORS]
---functions---
[FUNCTIONS]
"""

constructor_template = "benchmark.constructor[NUMBER]#[HASH] flags:# positional:long non_positional:flags.0?long = BenchmarkConstructorType;"
function_template = "benchmark.function[NUMBER]#[HASH] flags:# positional:long non_positional:flags.0?long = BenchmarkFunctionType;"

constructors_code = ""
functions_code = ""
loc = 0
for index in range(1, 100000 + 1):
    hash = "0" * (8 - len(str(index))) + str(index)
    constructors_code += constructor_template.replace("[NUMBER]", str(index)).replace("[HASH]", hash) + "\n"
    functions_code += function_template.replace("[NUMBER]", str(index)).replace("[HASH]", hash) + "\n"
    loc += 2

with open("benchmark.tl", "w") as file:
    file.write(schema_template.replace("[CONSTRUCTORS]", constructors_code).replace("[FUNCTIONS]", functions_code))

print(f"Generated {loc} lines of code")
