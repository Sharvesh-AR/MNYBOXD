// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.ChildFriendly
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.Elderly
import androidx.compose.material.icons.rounded.EmojiFoodBeverage
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocalPizza
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Plumbing
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.PropaneTank
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RamenDining
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SmokingRooms
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.SportsBar
import androidx.compose.material.icons.rounded.SportsCricket
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Subway
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TempleHindu
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Toys
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.Vaccines
import androidx.compose.material.icons.rounded.VapingRooms
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WineBar
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.sharveshar.spendly.domain.model.AccountType

/** One choosable category icon: the key stored in the database, its picture and its spoken name. */
data class CategoryIcon(val key: String, val vector: ImageVector, val label: String)

/** Icons grouped the way people think about spending, so the picker is quick to scan. */
data class IconGroup(val title: String, val icons: List<CategoryIcon>)

/**
 * Category icon keys are stored in the database: never rename or remove a key (old ones keep
 * working); unknown keys fall back to "other".
 */
object CategoryIcons {
    private fun i(key: String, vector: ImageVector, label: String) = CategoryIcon(key, vector, label)

    val groups: List<IconGroup> = listOf(
        IconGroup("Food & drink", listOf(
            i("restaurant", Icons.Rounded.Restaurant, "Restaurant"),
            i("cafe", Icons.Rounded.LocalCafe, "Café"),
            i("coffee", Icons.Rounded.Coffee, "Coffee"),
            i("tea", Icons.Rounded.EmojiFoodBeverage, "Tea"),
            i("fastfood", Icons.Rounded.Fastfood, "Fast food"),
            i("pizza", Icons.Rounded.LocalPizza, "Pizza"),
            i("burger", Icons.Rounded.LunchDining, "Burger"),
            i("noodles", Icons.Rounded.RamenDining, "Noodles"),
            i("bakery", Icons.Rounded.BakeryDining, "Bakery"),
            i("snacks", Icons.Rounded.Cookie, "Snacks"),
            i("icecream", Icons.Rounded.Icecream, "Ice cream"),
            i("cake", Icons.Rounded.Cake, "Cake"),
            i("drinks", Icons.Rounded.LocalDrink, "Drinks"),
            i("grocery", Icons.Rounded.LocalGroceryStore, "Groceries"),
            i("kitchen", Icons.Rounded.Kitchen, "Kitchen"),
        )),
        IconGroup("Habits", listOf(
            i("cigarette", Icons.Rounded.SmokingRooms, "Cigarettes"),
            i("vape", Icons.Rounded.VapingRooms, "Vape"),
            i("alcohol", Icons.Rounded.LocalBar, "Alcohol"),
            i("beer", Icons.Rounded.SportsBar, "Beer"),
            i("wine", Icons.Rounded.WineBar, "Wine"),
            i("nightlife", Icons.Rounded.Nightlife, "Nightlife"),
            i("betting", Icons.Rounded.Casino, "Betting"),
        )),
        IconGroup("Getting around", listOf(
            i("commute", Icons.Rounded.DirectionsCar, "Car"),
            i("bike", Icons.Rounded.TwoWheeler, "Bike"),
            i("cycle", Icons.AutoMirrored.Rounded.DirectionsBike, "Cycle"),
            i("taxi", Icons.Rounded.LocalTaxi, "Taxi"),
            i("bus", Icons.Rounded.DirectionsBus, "Bus"),
            i("train", Icons.Rounded.Train, "Train"),
            i("metro", Icons.Rounded.Subway, "Metro"),
            i("fuel", Icons.Rounded.LocalGasStation, "Fuel"),
            i("ev", Icons.Rounded.EvStation, "EV charging"),
            i("parking", Icons.Rounded.LocalParking, "Parking"),
            i("delivery", Icons.Rounded.LocalShipping, "Delivery"),
        )),
        IconGroup("Home & bills", listOf(
            i("home", Icons.Rounded.Home, "Home"),
            i("rent", Icons.Rounded.Apartment, "Rent"),
            i("bills", Icons.AutoMirrored.Rounded.ReceiptLong, "Bills"),
            i("electricity", Icons.Rounded.ElectricBolt, "Electricity"),
            i("water", Icons.Rounded.WaterDrop, "Water"),
            i("gas", Icons.Rounded.PropaneTank, "Gas cylinder"),
            i("internet", Icons.Rounded.Wifi, "Internet"),
            i("router", Icons.Rounded.Router, "Broadband"),
            i("phone", Icons.Rounded.Smartphone, "Phone"),
            i("tv", Icons.Rounded.Tv, "TV"),
            i("subscriptions", Icons.Rounded.Subscriptions, "Subscriptions"),
            i("laundry", Icons.Rounded.LocalLaundryService, "Laundry"),
            i("cleaning", Icons.Rounded.CleaningServices, "Cleaning"),
            i("repairs", Icons.Rounded.Handyman, "Repairs"),
            i("plumbing", Icons.Rounded.Plumbing, "Plumbing"),
            i("furniture", Icons.Rounded.Chair, "Furniture"),
            i("bed", Icons.Rounded.Bed, "Bedding"),
            i("garden", Icons.Rounded.Yard, "Garden"),
        )),
        IconGroup("Shopping", listOf(
            i("shopping", Icons.Rounded.ShoppingBag, "Shopping"),
            i("clothes", Icons.Rounded.Checkroom, "Clothes"),
            i("gadgets", Icons.Rounded.Devices, "Gadgets"),
            i("laptop", Icons.Rounded.Laptop, "Laptop"),
            i("headphones", Icons.Rounded.Headphones, "Audio"),
            i("watch", Icons.Rounded.Watch, "Watch"),
            i("jewellery", Icons.Rounded.Diamond, "Jewellery"),
            i("beauty", Icons.Rounded.Brush, "Beauty"),
            i("salon", Icons.Rounded.ContentCut, "Salon"),
            i("flowers", Icons.Rounded.LocalFlorist, "Flowers"),
            i("deals", Icons.Rounded.Sell, "Deals"),
        )),
        IconGroup("Health", listOf(
            i("health", Icons.Rounded.MedicalServices, "Health"),
            i("hospital", Icons.Rounded.LocalHospital, "Hospital"),
            i("medicine", Icons.Rounded.Medication, "Medicine"),
            i("pharmacy", Icons.Rounded.LocalPharmacy, "Pharmacy"),
            i("vaccine", Icons.Rounded.Vaccines, "Vaccines"),
            i("therapy", Icons.Rounded.Psychology, "Therapy"),
            i("fitness", Icons.Rounded.FitnessCenter, "Gym"),
            i("yoga", Icons.Rounded.SelfImprovement, "Yoga"),
            i("spa", Icons.Rounded.Spa, "Spa"),
            i("insurance", Icons.Rounded.HealthAndSafety, "Insurance"),
        )),
        IconGroup("Fun", listOf(
            i("movie", Icons.Rounded.Movie, "Movies"),
            i("tickets", Icons.Rounded.ConfirmationNumber, "Tickets"),
            i("shows", Icons.Rounded.TheaterComedy, "Shows"),
            i("music", Icons.Rounded.MusicNote, "Music"),
            i("games", Icons.Rounded.SportsEsports, "Games"),
            i("cricket", Icons.Rounded.SportsCricket, "Cricket"),
            i("football", Icons.Rounded.SportsSoccer, "Football"),
            i("sports", Icons.Rounded.SportsBasketball, "Sports"),
            i("swimming", Icons.Rounded.Pool, "Swimming"),
            i("books", Icons.AutoMirrored.Rounded.MenuBook, "Books"),
            i("camera", Icons.Rounded.CameraAlt, "Photography"),
            i("party", Icons.Rounded.Celebration, "Party"),
        )),
        IconGroup("Travel", listOf(
            i("travel", Icons.Rounded.Flight, "Flights"),
            i("hotel", Icons.Rounded.Hotel, "Hotel"),
            i("luggage", Icons.Rounded.Luggage, "Luggage"),
            i("beach", Icons.Rounded.BeachAccess, "Holiday"),
            i("trek", Icons.Rounded.Hiking, "Trek"),
            i("boat", Icons.Rounded.DirectionsBoat, "Boat"),
        )),
        IconGroup("People & family", listOf(
            i("kids", Icons.Rounded.ChildCare, "Kids"),
            i("baby", Icons.Rounded.ChildFriendly, "Baby"),
            i("parents", Icons.Rounded.Elderly, "Parents"),
            i("pets", Icons.Rounded.Pets, "Pets"),
            i("toys", Icons.Rounded.Toys, "Toys"),
            i("school", Icons.Rounded.School, "School"),
            i("study", Icons.Rounded.Science, "Courses"),
            i("gift", Icons.Rounded.CardGiftcard, "Gifts"),
            i("donation", Icons.Rounded.VolunteerActivism, "Donation"),
            i("temple", Icons.Rounded.TempleHindu, "Temple"),
        )),
        IconGroup("Money & work", listOf(
            i("work", Icons.Rounded.Work, "Work"),
            i("business", Icons.Rounded.Business, "Business"),
            i("store", Icons.Rounded.Storefront, "Store"),
            i("deal", Icons.Rounded.Handshake, "Deal"),
            i("savings", Icons.Rounded.Savings, "Savings"),
            i("invest", Icons.AutoMirrored.Rounded.TrendingUp, "Investments"),
            i("rupee", Icons.Rounded.CurrencyRupee, "Cash"),
            i("emi", Icons.Rounded.CreditScore, "EMI & loans"),
            i("tax", Icons.Rounded.RequestQuote, "Tax"),
            i("receipt", Icons.Rounded.Receipt, "Receipt"),
            i("rewards", Icons.Rounded.Redeem, "Rewards"),
            i("legal", Icons.Rounded.Gavel, "Legal"),
            i("protection", Icons.Rounded.Shield, "Protection"),
            i("other", Icons.Rounded.MoreHoriz, "Other"),
        )),
    )

    val all: List<CategoryIcon> = groups.flatMap { it.icons }
    private val map = all.associateBy { it.key }

    fun forKey(key: String?): ImageVector = map[key]?.vector ?: Icons.Rounded.MoreHoriz

    fun labelFor(key: String?): String = map[key]?.label ?: "Other"

    val transfer: ImageVector = Icons.Rounded.SwapHoriz
    val refund: ImageVector = Icons.AutoMirrored.Rounded.Undo
}

fun AccountType.icon(): ImageVector = when (this) {
    AccountType.SAVINGS, AccountType.CURRENT -> Icons.Rounded.AccountBalance
    AccountType.CASH -> Icons.Rounded.Payments
    AccountType.DEBIT_CARD, AccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
    AccountType.WALLET -> Icons.Rounded.AccountBalanceWallet
}
