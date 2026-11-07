package io.github.sharveshar.spendly.domain.voice

import io.github.sharveshar.spendly.core.money.Money
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.CategoryKind
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType

data class VoiceAccount(val id: Long, val name: String, val institution: String, val type: AccountType)
data class VoiceCategory(val id: Long, val key: String?, val name: String, val kind: CategoryKind)

enum class VoiceIssue(val message: String) {
    MISSING_AMOUNT("No amount was heard. Enter it below."),
    MULTIPLE_AMOUNTS("More than one number was heard. Check the amount."),
    AMOUNT_OUT_OF_RANGE("The amount heard is out of range."),
    ACCOUNT_AMBIGUOUS("More than one account matches. Choose the right one."),
    ACCOUNT_NOT_FOUND("The account you mentioned wasn't found. Choose one below."),
}

data class VoiceParseResult(
    val transcript: String,
    val type: TransactionType,
    val amountPaise: Long?,
    val description: String,
    val categoryId: Long?,
    val channel: PaymentChannel?,
    val provider: PaymentProvider?,
    val accountId: Long?,
    /** 0 for today, −1 when "yesterday" was said. */
    val dayOffset: Int,
    val issues: List<VoiceIssue>,
)

/**
 * Deterministic, fully on-device parser for short spoken expense phrases, for example
 * "Spent 500 on petrol using PhonePe from SBI". It never saves anything: the result only
 * pre-fills the Quick Add form, which the user must confirm.
 */
object VoiceParser {

    private val CURRENCY = setOf("rs", "rs.", "rupee", "rupees", "inr", "₹", "bucks", "rupaye", "rupay")
    private val MULTIPLIERS = mapOf(
        "k" to 1_000L, "thousand" to 1_000L, "thousands" to 1_000L,
        "lakh" to 1_00_000L, "lakhs" to 1_00_000L, "lac" to 1_00_000L, "lacs" to 1_00_000L,
        "crore" to 1_00_00_000L, "crores" to 1_00_00_000L, "hundred" to 100L,
    )
    private val SMALL_NUMBERS = mapOf(
        "zero" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L, "six" to 6L,
        "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L, "eleven" to 11L, "twelve" to 12L,
        "thirteen" to 13L, "fourteen" to 14L, "fifteen" to 15L, "sixteen" to 16L, "seventeen" to 17L,
        "eighteen" to 18L, "nineteen" to 19L, "twenty" to 20L, "thirty" to 30L, "forty" to 40L,
        "fifty" to 50L, "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L,
    )
    private val FILLER = setOf(
        "spent", "spend", "spending", "paid", "pay", "paying", "for", "on", "using", "use", "used", "via", "with",
        "from", "by", "through", "of", "to", "the", "a", "an", "i", "my", "just", "today", "yesterday", "and",
        "bought", "buy", "purchase", "purchased", "rupees", "account", "bank", "card", "credit", "debit", "in",
        "at", "was", "is", "it", "me", "got", "received", "receive", "income", "credited", "earned", "refund",
        "refunded", "upi", "cash", "wallet", "payment", "expense", "added", "add", "please", "worth", "only",
    )
    private val GENERIC_ACCOUNT_WORDS = setOf(
        "bank", "account", "card", "savings", "saving", "current", "credit", "debit", "the", "of", "wallet",
        "cash", "my", "a", "and", "ltd", "limited", "india", "first", "small", "finance", "other",
    )

    private val PROVIDERS: List<Pair<List<String>, PaymentProvider>> = listOf(
        listOf("google pay", "gpay", "g pay", "googlepay") to PaymentProvider.GOOGLE_PAY,
        listOf("phonepe", "phone pe", "phone pay", "phonepay") to PaymentProvider.PHONEPE,
        listOf("paytm", "pay tm") to PaymentProvider.PAYTM,
        listOf("bhim") to PaymentProvider.BHIM,
        listOf("amazon pay", "amazonpay") to PaymentProvider.AMAZON_PAY,
        listOf("cred") to PaymentProvider.CRED,
        listOf("whatsapp pay", "whatsapp") to PaymentProvider.WHATSAPP_PAY,
        listOf("mobikwik", "mobi kwik") to PaymentProvider.MOBIKWIK,
    )

    /** Well-known Indian bank short names → words that may appear in a stored institution/name. */
    private val BANK_ALIASES: Map<String, List<String>> = mapOf(
        "sbi" to listOf("sbi", "state bank"),
        "hdfc" to listOf("hdfc"),
        "icici" to listOf("icici"),
        "axis" to listOf("axis"),
        "kotak" to listOf("kotak"),
        "pnb" to listOf("pnb", "punjab national"),
        "bob" to listOf("bob", "baroda"),
        "baroda" to listOf("bob", "baroda"),
        "canara" to listOf("canara"),
        "union" to listOf("union"),
        "idfc" to listOf("idfc"),
        "yes" to listOf("yes bank"),
        "indusind" to listOf("indusind"),
        "federal" to listOf("federal"),
        "au" to listOf("au bank", "au small"),
        "rbl" to listOf("rbl"),
        "amex" to listOf("amex", "american express"),
        "hsbc" to listOf("hsbc"),
        "citi" to listOf("citi"),
    )

    /** Category keyword dictionary keyed by the stable default category key. */
    private val CATEGORY_KEYWORDS: Map<String, List<String>> = mapOf(
        "food" to listOf(
            "lunch", "dinner", "breakfast", "food", "coffee", "tea", "chai", "snack", "snacks", "restaurant",
            "zomato", "swiggy", "pizza", "burger", "biryani", "meal", "cafe", "juice", "dosa", "canteen",
        ),
        "groceries" to listOf(
            "grocery", "groceries", "vegetables", "veggies", "milk", "fruits", "bigbasket", "blinkit",
            "zepto", "instamart", "kirana", "supermarket", "dmart",
        ),
        "fuel" to listOf("petrol", "diesel", "fuel", "cng", "gas station"),
        "transport" to listOf(
            "uber", "ola", "rapido", "auto", "cab", "taxi", "bus", "metro", "train", "parking", "toll", "rickshaw",
        ),
        "shopping" to listOf("shopping", "clothes", "amazon", "flipkart", "myntra", "shoes", "dress", "shirt", "ajio"),
        "bills" to listOf(
            "electricity", "bill", "bills", "recharge", "wifi", "internet", "broadband", "mobile", "phone bill",
            "water bill", "dth", "gas bill", "postpaid",
        ),
        "rent" to listOf("rent", "maintenance"),
        "health" to listOf("medicine", "medicines", "doctor", "pharmacy", "hospital", "clinic", "medical", "gym", "dentist"),
        "entertainment" to listOf("movie", "movies", "netflix", "spotify", "concert", "game", "games", "hotstar", "prime"),
        "travel" to listOf("flight", "hotel", "trip", "travel", "holiday", "irctc", "booking"),
        "education" to listOf("books", "book", "course", "fees", "tuition", "school", "college", "exam"),
        "personal_care" to listOf("salon", "haircut", "spa", "cosmetics", "grooming"),
        "gifts" to listOf("gift", "gifts", "donation", "charity"),
        "salary" to listOf("salary", "paycheck", "wages"),
        "interest" to listOf("interest", "dividend"),
        "business" to listOf("freelance", "business", "client", "invoice"),
    )

    private val INCOME_WORDS = listOf("received", "receive", "got paid", "salary", "credited", "income", "earned")
    private val REFUND_WORDS = listOf("refund", "refunded", "cashback returned")

    fun parse(
        transcript: String,
        accounts: List<VoiceAccount>,
        categories: List<VoiceCategory>,
    ): VoiceParseResult {
        val issues = mutableListOf<VoiceIssue>()
        val text = normalize(transcript)
        val tokens = text.split(' ').filter { it.isNotBlank() }.toMutableList()
        val consumed = BooleanArray(tokens.size)
        val joined = " ${tokens.joinToString(" ")} "

        // Type
        val type = when {
            REFUND_WORDS.any { joined.contains(" $it ") } -> TransactionType.REFUND
            INCOME_WORDS.any { joined.contains(" $it ") } -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }
        val dayOffset = if (joined.contains(" yesterday ")) -1 else 0

        // Provider
        var provider: PaymentProvider? = null
        for ((phrases, p) in PROVIDERS) {
            val hit = phrases.firstOrNull { containsPhrase(tokens, it) }
            if (hit != null) {
                provider = p
                markPhrase(tokens, consumed, hit)
                break
            }
        }

        // Channel hints
        val saysCredit = containsPhrase(tokens, "credit card") || containsPhrase(tokens, "cc")
        val saysDebit = containsPhrase(tokens, "debit card")
        val saysCash = tokens.contains("cash")
        var channel: PaymentChannel? = when {
            saysCredit -> PaymentChannel.CREDIT_CARD
            saysDebit -> PaymentChannel.DEBIT_CARD
            containsPhrase(tokens, "net banking") || tokens.contains("netbanking") -> PaymentChannel.NET_BANKING
            tokens.any { it in setOf("neft", "imps", "rtgs") } || containsPhrase(tokens, "bank transfer") -> PaymentChannel.BANK_TRANSFER
            saysCash -> PaymentChannel.CASH
            tokens.contains("upi") || provider != null -> PaymentChannel.UPI
            tokens.contains("wallet") -> PaymentChannel.WALLET
            else -> null
        }
        if (channel == PaymentChannel.UPI && tokens.contains("wallet")) channel = PaymentChannel.WALLET
        for (w in listOf("cc", "upi", "neft", "imps", "rtgs", "netbanking")) markAll(tokens, consumed, w)

        // Account
        val typeHint: AccountType? = when (channel) {
            PaymentChannel.CREDIT_CARD -> AccountType.CREDIT_CARD
            PaymentChannel.DEBIT_CARD -> AccountType.DEBIT_CARD
            PaymentChannel.CASH -> AccountType.CASH
            else -> null
        }
        val accountMatch = matchAccount(tokens, consumed, accounts, typeHint)
        val accountId = accountMatch.accountId
        if (accountMatch.ambiguous) issues += VoiceIssue.ACCOUNT_AMBIGUOUS
        if (accountMatch.mentionedUnknownBank) issues += VoiceIssue.ACCOUNT_NOT_FOUND
        val matchedAccount = accounts.firstOrNull { it.id == accountId }
        if (matchedAccount != null && channel == null) {
            channel = when (matchedAccount.type) {
                AccountType.CASH -> PaymentChannel.CASH
                AccountType.CREDIT_CARD -> PaymentChannel.CREDIT_CARD
                AccountType.DEBIT_CARD -> PaymentChannel.DEBIT_CARD
                AccountType.WALLET -> PaymentChannel.WALLET
                else -> null
            }
        }
        if (matchedAccount?.type == AccountType.CREDIT_CARD && channel == PaymentChannel.DEBIT_CARD) channel = PaymentChannel.CREDIT_CARD
        if (provider != null && channel != PaymentChannel.UPI && channel != PaymentChannel.WALLET) {
            // A payment app was named but the channel says otherwise (e.g. "cash"); keep the explicit channel.
            provider = null
        }

        // Amount
        val candidates = findAmounts(tokens, consumed)
        var amount: Long? = null
        when {
            candidates.isEmpty() -> issues += VoiceIssue.MISSING_AMOUNT
            else -> {
                val withCurrency = candidates.filter { it.hasCurrency }
                val chosen = when {
                    candidates.size == 1 -> candidates[0]
                    withCurrency.size == 1 -> withCurrency[0]
                    else -> {
                        issues += VoiceIssue.MULTIPLE_AMOUNTS
                        candidates[0]
                    }
                }
                chosen.tokenIndexes.forEach { consumed[it] = true }
                if (chosen.paise == null || !Money.isValidAmount(chosen.paise)) {
                    issues += VoiceIssue.AMOUNT_OUT_OF_RANGE
                } else {
                    amount = chosen.paise
                }
            }
        }

        // Category
        val categoryKind = if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val categoryId = matchCategory(tokens, categories.filter { it.kind == categoryKind })

        // Description
        val description = tokens.indices
            .filter { !consumed[it] }
            .map { tokens[it] }
            .filter { it !in FILLER && it !in CURRENCY && it !in MULTIPLIERS && it !in SMALL_NUMBERS && !it.first().isDigit() }
            .joinToString(" ")
            .trim()
            .replaceFirstChar { it.uppercaseChar() }
            .take(80)

        return VoiceParseResult(
            transcript = transcript.trim(),
            type = type,
            amountPaise = amount,
            description = description,
            categoryId = categoryId,
            channel = channel,
            provider = provider,
            accountId = accountId,
            dayOffset = dayOffset,
            issues = issues.distinct(),
        )
    }

    internal fun normalize(raw: String): String {
        var s = raw.lowercase().replace("₹", " rs ")
        s = s.replace(Regex("""(?<=[a-z])(?=\d)|(?<=[a-z]\.)(?=\d)|(?<=\d)(?=[a-z])"""), " ")
        s = s.replace(Regex("""[^a-z0-9.,\s]"""), " ")
        // Strip punctuation that is not inside a number: "lunch." / "lunch," / "rs."
        s = s.split(Regex("\\s+")).joinToString(" ") { tok ->
            if (Regex("""^\d[\d,]*(\.\d+)?$""").matches(tok)) tok else tok.trim('.', ',')
        }
        // "500." at sentence end, "1,500," in a list
        s = s.split(' ').joinToString(" ") { it.trimEnd('.', ',') }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private data class AmountCandidate(val paise: Long?, val tokenIndexes: List<Int>, val hasCurrency: Boolean)

    private fun findAmounts(tokens: List<String>, consumed: BooleanArray): List<AmountCandidate> {
        val out = mutableListOf<AmountCandidate>()
        var i = 0
        while (i < tokens.size) {
            if (consumed[i]) { i++; continue }
            val tok = tokens[i]
            var paise: Long?
            val indexes = mutableListOf(i)
            var j = i + 1
            if (Regex("""^(\d{1,3}(,\d{2,3})+|\d+)(\.\d{1,2})?$""").matches(tok)) {
                paise = Money.parseRupees(tok.replace(",", "")) // null when out of range
                // Optional multiplier: "5 thousand", "1.5 lakh", "2 k"
                val mult = tokens.getOrNull(j)?.let { MULTIPLIERS[it] }
                if (mult != null && paise != null) {
                    paise = safeMultiply(paise, mult)
                    indexes += j
                    j++
                }
            } else if (tok in SMALL_NUMBERS || (tok in MULTIPLIERS && tok != "k")) {
                // Spoken number words: "two hundred fifty", "five thousand"
                var total = 0L
                var current = 0L
                var any = false
                var k = i
                while (k < tokens.size) {
                    val w = tokens[k]
                    val small = SMALL_NUMBERS[w]
                    val mult = MULTIPLIERS[w]?.takeIf { w != "k" }
                    when {
                        small != null -> { current += small; any = true }
                        mult != null && any -> {
                            if (mult == 100L) current = maxOf(current, 1) * 100
                            else { total += maxOf(current, 1) * mult; current = 0 }
                        }
                        w == "and" && any && k + 1 < tokens.size && (tokens[k + 1] in SMALL_NUMBERS) -> Unit
                        else -> break
                    }
                    if (k != i) indexes += k
                    k++
                }
                if (!any) { i++; continue }
                j = k
                val rupees = total + current
                paise = if (rupees > Money.MAX_PAISE / 100) null else rupees * 100
                if (rupees == 0L) { i = j; continue }
            } else {
                i++
                continue
            }
            val hasCurrency = tokens.getOrNull(j) in CURRENCY || tokens.getOrNull(i - 1) in CURRENCY
            if (tokens.getOrNull(j) in CURRENCY) indexes += j
            if (tokens.getOrNull(i - 1) in CURRENCY) indexes += i - 1
            out += AmountCandidate(paise, indexes, hasCurrency)
            i = j
        }
        return out
    }

    private fun safeMultiply(paise: Long, mult: Long): Long? =
        if (paise > Money.MAX_PAISE / mult) null else paise * mult

    private data class AccountMatch(val accountId: Long?, val ambiguous: Boolean, val mentionedUnknownBank: Boolean)

    private fun distinctiveWords(a: VoiceAccount): Set<String> {
        val words = (a.name + " " + a.institution).lowercase()
            .split(Regex("[^a-z0-9]+")).filter { it.length >= 2 && it !in GENERIC_ACCOUNT_WORDS }
            .toMutableSet()
        // Expand known bank names so "SBI" matches "State Bank of India" and vice versa.
        val full = (a.name + " " + a.institution).lowercase()
        for ((short, longForms) in BANK_ALIASES) {
            if (words.contains(short) || longForms.any { full.contains(it) }) words += short
        }
        return words
    }

    private fun matchAccount(
        tokens: List<String>,
        consumed: BooleanArray,
        accounts: List<VoiceAccount>,
        typeHint: AccountType?,
    ): AccountMatch {
        // Spoken long forms → short form tokens, e.g. "state bank" → "sbi"
        val spoken = tokens.toMutableSet()
        val joined = " ${tokens.joinToString(" ")} "
        for ((short, longForms) in BANK_ALIASES) if (longForms.any { joined.contains(" $it ") }) spoken += short

        // Bank names weigh more than other name words so "salary" in "ICICI Salary" can't outvote "HDFC".
        val scored = accounts.map { a ->
            a to distinctiveWords(a).filter { it in spoken }.fold(0) { acc, w -> acc + if (w in BANK_ALIASES) 2 else 1 }
        }
        val best = scored.maxOfOrNull { it.second } ?: 0
        val mentionedBank = BANK_ALIASES.keys.any { it in spoken && it != "yes" && it != "union" && it != "au" } ||
            joined.contains(" yes bank ") || joined.contains(" union bank ")

        if (best == 0) {
            // No name mentioned: fall back on the type hint when exactly one account has that type.
            if (typeHint != null) {
                val ofType = accounts.filter { it.type == typeHint }
                if (ofType.size == 1) return AccountMatch(ofType[0].id, false, false)
                if (ofType.size > 1) return AccountMatch(null, true, false)
            }
            return AccountMatch(null, false, mentionedBank)
        }
        var top = scored.filter { it.second == best }.map { it.first }
        if (typeHint != null) {
            val typed = top.filter { it.type == typeHint }
            if (typed.isNotEmpty()) top = typed
        }
        if (top.size > 1) {
            // Prefer a balance-holding bank account over cards when nothing else disambiguates.
            val banks = top.filter { it.type.isBank }
            if (typeHint == null && banks.size == 1) top = banks
        }
        if (top.size != 1) return AccountMatch(null, true, false)
        val chosen = top[0]
        val words = distinctiveWords(chosen)
        tokens.forEachIndexed { i, t -> if (t in words) consumed[i] = true }
        // Also consume spoken long forms like "state bank"
        for ((short, longForms) in BANK_ALIASES) if (short in words) longForms.forEach { markPhrase(tokens, consumed, it) }
        return AccountMatch(chosen.id, false, false)
    }

    private fun matchCategory(tokens: List<String>, categories: List<VoiceCategory>): Long? {
        val joined = " ${tokens.joinToString(" ")} "
        // 1. A category whose own name was spoken (covers user-created categories).
        categories.firstOrNull { c ->
            val name = c.name.lowercase().replace("&", "and")
            name.length >= 3 && joined.contains(" $name ")
        }?.let { return it.id }
        // 2. Keyword dictionary for default categories.
        for ((key, words) in CATEGORY_KEYWORDS) {
            if (words.any { joined.contains(" $it ") }) {
                categories.firstOrNull { it.key == key }?.let { return it.id }
            }
        }
        // 3. Single distinctive word of a category name ("groceries", "health").
        return categories.firstOrNull { c ->
            c.name.lowercase().split(Regex("[^a-z]+")).any { it.length >= 4 && it in tokens }
        }?.id
    }

    private fun containsPhrase(tokens: List<String>, phrase: String): Boolean {
        val parts = phrase.split(' ')
        if (parts.size > tokens.size) return false
        for (i in 0..tokens.size - parts.size) {
            if (parts.indices.all { tokens[i + it] == parts[it] }) return true
        }
        return false
    }

    private fun markPhrase(tokens: List<String>, consumed: BooleanArray, phrase: String) {
        val parts = phrase.split(' ')
        if (parts.size > tokens.size) return
        for (i in 0..tokens.size - parts.size) {
            if (parts.indices.all { tokens[i + it] == parts[it] }) parts.indices.forEach { consumed[i + it] = true }
        }
    }

    private fun markAll(tokens: List<String>, consumed: BooleanArray, word: String) {
        tokens.forEachIndexed { i, t -> if (t == word) consumed[i] = true }
    }
}
