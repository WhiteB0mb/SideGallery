with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    code = f.read()

code = code.replace("androidx.compose.material.icons.automirrored.filled.Sort", "Icons.Default.Sort")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(code)
