import re

with open('app/src/main/java/com/example/MainViewModel.kt', 'r') as f:
    content = f.read()

# Fix cursor block
cursor_target = """                                if (name.startsWith("imported_")) {
                                    val timeFromName = name.substringAfter("imported_").substringBefore(".").toLongOrNull()
                                    if (timeFromName != null && timeFromName > 0) {
                                        mod = timeFromName
                                    }
                                }"""

cursor_replacement = """                                val match = Regex("imported_(\\\\d+)").find(name)
                                if (match != null) {
                                    val timeFromName = match.groupValues[1].toLongOrNull()
                                    if (timeFromName != null && timeFromName > 0) {
                                        mod = timeFromName
                                    }
                                } else if (mod == 0L) {
                                    mod = System.currentTimeMillis() // Fallback so it appears at top if we can't read date
                                }"""

# Fix fallback block
fallback_target = """                            if (name.startsWith("imported_")) {
                                val timeFromName = name.substringAfter("imported_").substringBefore(".").toLongOrNull()
                                if (timeFromName != null && timeFromName > 0) {
                                    mod = timeFromName
                                }
                            }"""

fallback_replacement = """                            val match = Regex("imported_(\\\\d+)").find(name)
                            if (match != null) {
                                val timeFromName = match.groupValues[1].toLongOrNull()
                                if (timeFromName != null && timeFromName > 0) {
                                    mod = timeFromName
                                }
                            } else if (mod == 0L) {
                                mod = System.currentTimeMillis() // Fallback so it appears at top
                            }"""

content = content.replace(cursor_target, cursor_replacement)
content = content.replace(fallback_target, fallback_replacement)

with open('app/src/main/java/com/example/MainViewModel.kt', 'w') as f:
    f.write(content)
