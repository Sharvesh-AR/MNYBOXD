package io.github.sharveshar.spendly.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.sharveshar.spendly.domain.model.AccountType

/** Category icon keys are stored in the database; unknown keys fall back to "other". */
object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "restaurant" to Icons.Rounded.Restaurant,
        "cafe" to Icons.Rounded.LocalCafe,
        "grocery" to Icons.Rounded.LocalGroceryStore,
        "commute" to Icons.Rounded.DirectionsCar,
        "fuel" to Icons.Rounded.LocalGasStation,
        "shopping" to Icons.Rounded.ShoppingBag,
        "clothes" to Icons.Rounded.Checkroom,
        "bills" to Icons.AutoMirrored.Rounded.ReceiptLong,
        "phone" to Icons.Rounded.Smartphone,
        "home" to Icons.Rounded.Home,
        "health" to Icons.Rounded.MedicalServices,
        "fitness" to Icons.Rounded.FitnessCenter,
        "movie" to Icons.Rounded.Movie,
        "travel" to Icons.Rounded.Flight,
        "school" to Icons.Rounded.School,
        "kids" to Icons.Rounded.ChildCare,
        "pets" to Icons.Rounded.Pets,
        "spa" to Icons.Rounded.Spa,
        "gift" to Icons.Rounded.CardGiftcard,
        "work" to Icons.Rounded.Work,
        "store" to Icons.Rounded.Storefront,
        "savings" to Icons.Rounded.Savings,
        "other" to Icons.Rounded.MoreHoriz,
    )
    private val map = all.toMap()

    fun forKey(key: String?): ImageVector = map[key] ?: Icons.Rounded.MoreHoriz

    val transfer: ImageVector = Icons.Rounded.SwapHoriz
    val refund: ImageVector = Icons.AutoMirrored.Rounded.Undo
}

fun AccountType.icon(): ImageVector = when (this) {
    AccountType.SAVINGS, AccountType.CURRENT -> Icons.Rounded.AccountBalance
    AccountType.CASH -> Icons.Rounded.Payments
    AccountType.DEBIT_CARD, AccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
    AccountType.WALLET -> Icons.Rounded.AccountBalanceWallet
}
