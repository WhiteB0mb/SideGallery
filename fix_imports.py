with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    code = f.read()

code = code.replace("Icons.AutoMirrored.Filled.Sort", "androidx.compose.material.icons.automirrored.filled.Sort")
code = code.replace("Divider()", "HorizontalDivider()")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(code)
