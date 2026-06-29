package com.aistudio.clinicsystem.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design Tokens — единая система типографических и пространственных значений.
 *
 * P-11 / P-12 / P-13 refactor: вводит шкалы radius, spacing и font size,
 * чтобы избавиться от хаоса произвольных значений (8/10/12/16/20/24 dp для
 * radius, 4/6/8/10/12/14/16/20/24 dp для padding, 8-24 sp для шрифтов).
 *
 * Использование:
 *   RoundedCornerShape(Radius.small)      // вместо RoundedCornerShape(Radius.small)
 *   padding(Spacing.l)                    // вместо padding(16.dp)
 *   fontSize = AppFontSize.body           // вместо fontSize = AppFontSize.body
 *
 * Правило: любые .dp / .sp в UI-коде должны использовать эти токены.
 * Исключения допускаются только для редких случаев (например, aspect ratio
 * 0.95f, толщина границы 1.dp).
 */
object Radius {
    /** Маленькие элементы: badge, chips, иконки-контейнеры */
    val small = 8.dp

    /** Средние элементы: кнопки, поля ввода, маленькие карточки */
    val medium = 12.dp

    /** Большие элементы: стандартные карточки */
    val large = 16.dp

    /** Очень большие элементы: диалоги, бэннеры */
    val xl = 28.dp
}

object Spacing {
    /** Минимальный отступ — между иконкой и текстом, между связанными элементами */
    val xs = 4.dp

    /** Малый отступ — внутри карточек, между элементами списка */
    val s = 8.dp

    /** Средний отступ — между секциями внутри карточки */
    val m = 12.dp

    /** Стандартный отступ — padding карточек, между секциями экрана */
    val l = 16.dp

    /** Большой отступ — между крупными блоками, padding экрана */
    val xl = 24.dp

    /** Очень большой отступ — для просторных экранов */
    val xxl = 32.dp
}

object AppFontSize {
    /** Caption — badge, метки, мелкие подписи (минимум для читаемости) */
    val caption = 10.sp

    /** Body small — второстепенный текст, описания */
    val bodySmall = 11.sp

    /** Body — основной мелкий текст */
    val body = 12.sp

    /** Body large — основной текст в карточках */
    val bodyLarge = 13.sp

    /** Title — заголовки элементов, имена врачей */
    val title = 14.sp

    /** Title large — заголовки секций */
    val titleLarge = 16.sp

    /** Headline — крупные числа, статистика */
    val headline = 20.sp

    /** Display — большие заголовки экранов, имена пользователей */
    val display = 24.sp
}
