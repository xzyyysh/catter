package io.tabler.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object TablerIcons {

    private val pathData: Map<String, List<String>> by lazy {
        val target = HashMap<String, List<String>>(6000)
        loadCatalog1(target)
        loadCatalog2(target)
        loadCatalog3(target)
        loadCatalog4(target)
        loadCatalog5(target)
        loadCatalog6(target)
        loadCatalog7(target)
        loadCatalog8(target)
        target
    }

    private val cache = HashMap<String, ImageVector>()

    val names: Set<String> get() = pathData.keys

    operator fun get(name: String): ImageVector = synchronized(cache) {
        cache[name] ?: build(name).also { cache[name] = it }
    }

    val Home: ImageVector get() = get("home")
    val Settings: ImageVector get() = get("settings")
    val InfoCircle: ImageVector get() = get("info-circle")
    val Cat: ImageVector get() = get("cat")
    val Github: ImageVector get() = get("brand-github")
    val User: ImageVector get() = get("user")
    val Link: ImageVector get() = get("link")
    val Clipboard: ImageVector get() = get("clipboard")
    val X: ImageVector get() = get("x")
    val Youtube: ImageVector get() = get("brand-youtube")

    private fun build(name: String): ImageVector {
        val paths = pathData[name]
            ?: throw IllegalArgumentException("Unknown Tabler icon: $name")

        return ImageVector.Builder(
            name = "Tabler.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }
}
