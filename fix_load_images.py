import re

with open('app/src/main/java/com/example/MainViewModel.kt', 'r') as f:
    content = f.read()

target = """                        while (cursor.moveToNext()) {
                            val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) else null
                            if (mimeType != null && mimeType.startsWith("image/")) {"""

replacement = """                        while (cursor.moveToNext()) {
                            var mimeType = if (mimeCol != -1) cursor.getString(mimeCol) else null
                            val name = if (nameCol != -1) cursor.getString(nameCol) ?: "" else ""
                            
                            val isImageByExtension = name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") || 
                                                     name.lowercase().endsWith(".png") || name.lowercase().endsWith(".gif") || 
                                                     name.lowercase().endsWith(".webp")
                            
                            if (mimeType == null || (!mimeType.startsWith("image/") && isImageByExtension)) {
                                mimeType = if (name.lowercase().endsWith(".gif")) "image/gif" else "image/jpeg"
                            }

                            if (mimeType != null && mimeType.startsWith("image/")) {"""

content = content.replace(target, replacement)

target2 = """                    documentFile?.listFiles()?.forEach { file ->
                        val mimeType = file.type
                        if (mimeType != null && mimeType.startsWith("image/")) {
                            val name = file.name ?: "" """

replacement2 = """                    documentFile?.listFiles()?.forEach { file ->
                        var mimeType = file.type
                        val name = file.name ?: ""
                        
                        val isImageByExtension = name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") || 
                                                 name.lowercase().endsWith(".png") || name.lowercase().endsWith(".gif") || 
                                                 name.lowercase().endsWith(".webp")
                        
                        if (mimeType == null || (!mimeType.startsWith("image/") && isImageByExtension)) {
                            mimeType = if (name.lowercase().endsWith(".gif")) "image/gif" else "image/jpeg"
                        }
                        
                        if (mimeType != null && mimeType.startsWith("image/")) {"""

content = content.replace(target2, replacement2)

with open('app/src/main/java/com/example/MainViewModel.kt', 'w') as f:
    f.write(content)
