import re

with open('app/src/main/java/com/example/MainViewModel.kt', 'r') as f:
    content = f.read()

# Fix 1: Timestamp parsing for cursor
cursor_target = """                                val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                                val mod = if (modCol != -1) cursor.getLong(modCol) else 0L
                                val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L"""

cursor_replacement = """                                val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                                var mod = if (modCol != -1) cursor.getLong(modCol) else 0L
                                if (name.startsWith("imported_")) {
                                    val timeFromName = name.substringAfter("imported_").substringBefore(".").toLongOrNull()
                                    if (timeFromName != null && timeFromName > 0) {
                                        mod = timeFromName
                                    }
                                }
                                val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L"""

# Fix 2: Timestamp parsing for fallback
fallback_target = """                            items.add(
                                GalleryItem(
                                    uri = file.uri,
                                    name = file.name ?: "",
                                    dateModified = file.lastModified(),
                                    size = file.length(),
                                    isGif = mimeType == "image/gif"
                                )
                            )"""

fallback_replacement = """                            val name = file.name ?: ""
                            var mod = file.lastModified()
                            if (name.startsWith("imported_")) {
                                val timeFromName = name.substringAfter("imported_").substringBefore(".").toLongOrNull()
                                if (timeFromName != null && timeFromName > 0) {
                                    mod = timeFromName
                                }
                            }
                            items.add(
                                GalleryItem(
                                    uri = file.uri,
                                    name = name,
                                    dateModified = mod,
                                    size = file.length(),
                                    isGif = mimeType == "image/gif"
                                )
                            )"""

content = content.replace(cursor_target, cursor_replacement)
content = content.replace(fallback_target, fallback_replacement)

with open('app/src/main/java/com/example/MainViewModel.kt', 'w') as f:
    f.write(content)
