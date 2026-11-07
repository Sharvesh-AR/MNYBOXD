package io.github.sharveshar.spendly

import io.github.sharveshar.spendly.data.repo.AccountInput
import io.github.sharveshar.spendly.data.repo.SaveResult
import io.github.sharveshar.spendly.data.repo.WriteResult
import io.github.sharveshar.spendly.domain.ledger.TransactionDraft
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/**
 * DEBUG BUILDS ONLY. Inserts clearly fictional sample data through the normal validated
 * repositories, for screenshots and manual testing. This source set is not compiled into
 * release builds.
 */
object DebugTools {
    const val available = true

    suspend fun loadSampleData(repos: Repositories): String {
        if (repos.transactions.observeCount().first() > 0) return "Sample data is only added to an empty ledger."
        fun id(r: WriteResult) = (r as WriteResult.Ok).id
        val hdfc = id(repos.accounts.create(AccountInput("HDFC Savings", "HDFC Bank", AccountType.SAVINGS, 84_250_00, null, null, 0)))
        val sbi = id(repos.accounts.create(AccountInput("SBI Salary", "State Bank of India", AccountType.SAVINGS, 41_800_00, null, null, 2)))
        val card = id(repos.accounts.create(AccountInput("ICICI Coral", "ICICI Bank", AccountType.CREDIT_CARD, 6_420_00, null, 1_50_000_00, 4)))
        val debit = id(repos.accounts.create(AccountInput("HDFC Debit", "HDFC Bank", AccountType.DEBIT_CARD, 0, hdfc, null, 1)))
        val cash = repos.accounts.observeWithBalances().first().first { it.account.type == AccountType.CASH }.account.id
        val cats = repos.categories.observeAll().first().associateBy { it.key }
        fun c(key: String) = cats.getValue(key).id
        repos.budgets.setLimit(null, 45_000_00)
        repos.budgets.setLimit(c("food"), 9_000_00)
        repos.budgets.setLimit(c("shopping"), 6_000_00)

        val zone = ZoneId.systemDefault()
        val rnd = Random(7)
        val today = LocalDate.now(zone)
        suspend fun add(type: TransactionType, paise: Long, account: Long, daysAgo: Long, hour: Int, category: Long? = null, note: String = "", channel: PaymentChannel? = null, provider: PaymentProvider? = null, to: Long? = null) {
            val at = today.minusDays(daysAgo).atTime(hour, rnd.nextInt(0, 59)).atZone(zone).toInstant().toEpochMilli()
                .coerceAtMost(System.currentTimeMillis())
            val r = repos.transactions.save(TransactionDraft(type, paise, account, to, category, channel, provider, null, note, at), UUID.randomUUID().toString())
            check(r is SaveResult.Saved) { "Sample insert failed: $r" }
        }
        for (d in 0L..75L) {
            if (rnd.nextInt(10) < 8) add(TransactionType.EXPENSE, rnd.nextLong(80, 420) * 100, hdfc, d, 9, c("food"), listOf("Coffee", "Breakfast", "Lunch", "Chai & snacks").random(rnd), PaymentChannel.UPI, PaymentProvider.GOOGLE_PAY)
            if (rnd.nextInt(10) < 4) add(TransactionType.EXPENSE, rnd.nextLong(60, 260) * 100, cash, d, 18, c("transport"), listOf("Auto", "Metro", "Rapido").random(rnd), PaymentChannel.CASH)
            if (d % 6 == 2L) add(TransactionType.EXPENSE, rnd.nextLong(600, 2_400) * 100, sbi, d, 19, c("groceries"), listOf("Weekly groceries", "BigBasket", "Vegetables & milk").random(rnd), PaymentChannel.UPI, PaymentProvider.PHONEPE)
            if (d % 11 == 4L) add(TransactionType.EXPENSE, rnd.nextLong(900, 4_500) * 100, card, d, 21, c("shopping"), listOf("Myntra order", "Amazon", "Shoes").random(rnd), PaymentChannel.CREDIT_CARD)
            if (d % 9 == 1L) add(TransactionType.EXPENSE, rnd.nextLong(1_500, 3_200) * 100, debit, d, 8, c("fuel"), "Petrol")
            if (d % 10 == 3L) add(TransactionType.TRANSFER, 4_000_00, hdfc, d, 11, note = "ATM withdrawal", to = cash)
            if (d % 14 == 5L) add(TransactionType.EXPENSE, rnd.nextLong(300, 1_400) * 100, card, d, 20, c("entertainment"), listOf("Movie night", "Concert tickets").random(rnd), PaymentChannel.CREDIT_CARD)
        }
        for (m in 0L..2L) {
            val day = today.dayOfMonth.toLong() + 30 * m - 1
            if (day in 3..80) {
                add(TransactionType.INCOME, 92_000_00, sbi, day, 10, c("salary"), "Salary")
                add(TransactionType.EXPENSE, 18_000_00, hdfc, day - 1, 11, c("rent"), "Rent", PaymentChannel.NET_BANKING)
                add(TransactionType.TRANSFER, 25_000_00, sbi, day - 1, 12, note = "Move to savings", to = hdfc)
                add(TransactionType.EXPENSE, rnd.nextLong(1_100, 2_300) * 100, hdfc, day - 3, 15, c("bills"), "Electricity bill", PaymentChannel.UPI, PaymentProvider.PAYTM)
            }
        }
        add(TransactionType.TRANSFER, 12_000_00, hdfc, 3, 13, note = "Card bill", to = card)
        return "Sample data added (debug build only)."
    }
}
