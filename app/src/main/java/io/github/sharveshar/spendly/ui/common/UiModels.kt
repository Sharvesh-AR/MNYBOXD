package io.github.sharveshar.spendly.ui.common

import androidx.compose.runtime.Immutable
import io.github.sharveshar.spendly.data.db.AccountWithBalance
import io.github.sharveshar.spendly.data.db.CategoryEntity
import io.github.sharveshar.spendly.domain.ledger.BalanceCalculator
import io.github.sharveshar.spendly.domain.model.AccountType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Immutable
data class AccountUi(
    val id: Long,
    val name: String,
    val institution: String,
    val type: AccountType,
    /** Signed net-worth balance; for debit cards this is the linked bank's balance. */
    val balancePaise: Long,
    val linkedAccountId: Long?,
    val linkedAccountName: String?,
    val creditLimitPaise: Long?,
    val colorIndex: Int,
    val archived: Boolean,
) {
    val outstandingPaise: Long get() = BalanceCalculator.outstanding(balancePaise)
    val subtitle: String
        get() = when (type) {
            AccountType.DEBIT_CARD -> "Debit card · ${linkedAccountName ?: "unlinked"}"
            else -> listOf(institution, type.label).filter { it.isNotBlank() }.joinToString(" · ")
        }

    companion object {
        fun from(rows: List<AccountWithBalance>): List<AccountUi> {
            val byId = rows.associateBy { it.account.id }
            return rows.map { r ->
                val a = r.account
                val linked = a.linkedAccountId?.let { byId[it] }
                AccountUi(
                    id = a.id,
                    name = a.name,
                    institution = a.institution,
                    type = a.type,
                    balancePaise = if (a.type == AccountType.DEBIT_CARD) linked?.balancePaise ?: 0L else r.balancePaise,
                    linkedAccountId = a.linkedAccountId,
                    linkedAccountName = linked?.account?.name,
                    creditLimitPaise = a.creditLimitPaise,
                    colorIndex = a.colorIndex,
                    archived = a.archived,
                )
            }
        }
    }
}

@Immutable
data class CategoryUi(val id: Long, val name: String, val iconKey: String, val colorIndex: Int, val archived: Boolean) {
    companion object {
        fun from(e: CategoryEntity) = CategoryUi(e.id, e.name, e.iconKey, e.colorIndex, e.archived)
    }
}

/** Observable "today" so screens refresh correctly when the app is resumed on a new day. */
object Today {
    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()
    fun refresh() { _date.value = LocalDate.now(ZoneId.systemDefault()) }
}

object Formats {
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    val month: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
    val shortMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
    val dayHeader: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    val dayHeaderYear: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a", Locale.ENGLISH)
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) date.format(dayHeader) else date.format(dayHeaderYear)
    }
}
