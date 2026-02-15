package com.purityguard.app

import kotlin.math.abs
import kotlin.random.Random

object MotivationVerses {

    private val bibleVerses = listOf(
        "\"Flee from sexual immorality. Every other sin a person commits is outside the body, but the sexually immoral person sins against his own body.\" — 1 Corinthians 6:18",
        "\"But I say to you that everyone who looks at a woman with lustful intent has already committed adultery with her in his heart.\" — Matthew 5:28",
        "\"For all that is in the world—the desires of the flesh and the desires of the eyes and pride of life—is not from the Father but is from the world.\" — 1 John 2:16",
        "\"Wine is a mocker, strong drink a brawler, and whoever is led astray by it is not wise.\" — Proverbs 20:1",
        "\"But each person is tempted when he is lured and enticed by his own desire. Then desire when it has conceived gives birth to sin, and sin when it is fully grown brings forth death.\" — James 1:14-15",
        "\"All things are lawful for me, but not all things are helpful. All things are lawful for me, but I will not be dominated by anything.\" — 1 Corinthians 6:12",
        "\"But I say, walk by the Spirit, and you will not gratify the desires of the flesh. For the desires of the flesh are against the Spirit, and the desires of the Spirit are against the flesh.\" — Galatians 5:16-17",
        "\"And do not get drunk with wine, for that is debauchery, but be filled with the Spirit.\" — Ephesians 5:18",
        "\"So flee youthful passions and pursue righteousness, faith, love, and peace, along with those who call on the Lord from a pure heart.\" — 2 Timothy 2:22",
        "\"Who has woe? Who has sorrow? Who has strife? Who has complaining? Who has wounds without cause? Who has redness of eyes? Those who tarry long over wine; those who go to try mixed wine.\" — Proverbs 23:29-30",
        "\"Put to death therefore what is earthly in you: sexual immorality, impurity, passion, evil desire, and covetousness, which is idolatry.\" — Colossians 3:5",
        "\"Beloved, I urge you as sojourners and exiles to abstain from the passions of the flesh, which wage war against your soul.\" — 1 Peter 2:11",
        "\"Let no one say when he is tempted, 'I am being tempted by God,' for God cannot be tempted with evil, and he himself tempts no one.\" — James 1:13"
    )

    private val quranVerses = listOf(
        "\"And do not approach unlawful sexual intercourse. Indeed, it is ever an immorality and is evil as a way.\" — Al-Isra 17:32",
        "\"Tell the believing men to reduce [some] of their vision and guard their private parts. That is purer for them. Indeed, Allah is Acquainted with what they do.\" — An-Nur 24:30",
        "\"O you who have believed, indeed, intoxicants, gambling, [sacrificing on] stone alters [to other than Allah], and divining arrows are but defilement from the work of Satan, so avoid it that you may be successful.\" — Al-Ma'idah 5:90",
        "\"Satan only wants to cause between you animosity and hatred through intoxicants and gambling and to avert you from the remembrance of Allah and from prayer. So will you not desist?\" — Al-Ma'idah 5:91",
        "\"Allah wants to accept your repentance, but those who follow [their] passions want you to digress [into] a great deviation.\" — An-Nisa 4:27",
        "\"They ask you about wine and gambling. Say, 'In them is great sin and [yet, some] benefit for people. But their sin is greater than their benefit.'\" — Al-Baqarah 2:219",
        "\"But if they do not respond to you - then know that they only follow their desires. And who is more astray than one who follows his desire without guidance from Allah?\" — Al-Qasas 28:50",
        "\"But there came after them successors who neglected prayer and pursued desires; so they are going to meet evil.\" — Maryam 19:59",
        "\"And I do not acquit myself. Indeed, the soul is a persistent enjoiner of evil, except those upon which my Lord has bestowed mercy.\" — Yusuf 12:53",
        "\"Have you seen him who takes his own lust as his god? And Allah knowing (him as such), left him astray, and sealed his hearing and his heart (and understanding), and put a cover on his sight.\" — Al-Jathiyah 45:23",
        "\"Say, 'My Lord has only forbidden immoralities - what is apparent of them and what is concealed - and sin, and oppression without right.'\" — Al-Ma'idat 7:33",
        "\"But as for he who feared the position of his Lord and prevented the soul from [unlawful] inclination, then indeed, Paradise will be [his] refuge.\" — An-Nazi'at 79:40-41",
        "\"Beautified for people is the love of that which they desire - of women and sons, heaped-up sums of gold and silver, fine branded horses, and cattle and tilled land.\" — Ali 'Imran 3:14"
    )

    fun pick(mode: InspirationMode, seed: String? = null): String? {
        val pool = when (mode) {
            InspirationMode.NONE -> return null
            InspirationMode.BIBLE -> bibleVerses
            InspirationMode.QURAN -> quranVerses
        }

        if (pool.isEmpty()) return deterministicFallback(mode)

        val index = if (seed != null) {
            val h = seed.hashCode()
            val nonNeg = if (h == Int.MIN_VALUE) 0 else abs(h)
            nonNeg % pool.size
        } else {
            Random.nextInt(pool.size)
        }

        return try {
            pool[index]
        } catch (_: Exception) {
            deterministicFallback(mode)
        }
    }

    private fun deterministicFallback(mode: InspirationMode): String? {
        return when (mode) {
            InspirationMode.NONE -> null
            InspirationMode.BIBLE -> bibleVerses.firstOrNull()
            InspirationMode.QURAN -> quranVerses.firstOrNull()
        }
    }
}
