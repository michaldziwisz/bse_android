package eu.blueseaeye.bse.ui.common

/** Liczba stopni z polską odmianą rzeczownika: 1 stopień, 2 stopnie, 20 stopni. */
fun degreesPolish(n: Int): String = "$n ${degreeWord(n)}"

private fun degreeWord(n: Int): String {
    val abs = Math.abs(n)
    val mod100 = abs % 100
    val mod10 = abs % 10
    return when {
        abs == 1 -> "stopień"
        mod100 in 12..14 -> "stopni"
        mod10 in 2..4 -> "stopnie"
        else -> "stopni"
    }
}
