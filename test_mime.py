import re

with open('app/src/main/java/com/example/MainViewModel.kt', 'r') as f:
    content = f.read()

target = """                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        val ext = when {
                            mimeType.contains("gif") -> "gif"
                            mimeType.contains("png") -> "png"
                            mimeType.contains("webp") -> "webp"
                            else -> "jpg"
                        }
                        val fileName = "imported_${System.currentTimeMillis()}.$ext"
                        val newFile = folder.createFile(mimeType, fileName)"""

replacement = """                        var mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        var ext = "jpg"
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (nameIndex != -1) {
                                    val originalName = cursor.getString(nameIndex)
                                    if (originalName != null) {
                                        if (originalName.lowercase().endsWith(".gif")) {
                                            mimeType = "image/gif"
                                            ext = "gif"
                                        } else if (originalName.lowercase().endsWith(".png")) {
                                            mimeType = "image/png"
                                            ext = "png"
                                        } else if (originalName.lowercase().endsWith(".webp")) {
                                            mimeType = "image/webp"
                                            ext = "webp"
                                        } else if (originalName.lowercase().endsWith(".jpg") || originalName.lowercase().endsWith(".jpeg")) {
                                            mimeType = "image/jpeg"
                                            ext = "jpg"
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (ext == "jpg") {
                            ext = when {
                                mimeType.contains("gif") -> "gif"
                                mimeType.contains("png") -> "png"
                                mimeType.contains("webp") -> "webp"
                                else -> "jpg"
                            }
                        }
                        
                        val fileName = "imported_${System.currentTimeMillis()}.$ext"
                        val newFile = folder.createFile(mimeType, fileName)"""

content = content.replace(target, replacement)

with open('app/src/main/java/com/example/MainViewModel.kt', 'w') as f:
    f.write(content)
