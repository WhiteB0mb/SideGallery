fun main() {
    val names = listOf("imported_1712345678901.gif", "imported_1712345678901", "imported_1712345678901.gif.gif", "imported_1712345678901 (1).gif")
    for (name in names) {
        val timeFromName = name.substringAfter("imported_").substringBefore(".").toLongOrNull()
        println("$name -> $timeFromName")
    }
}
