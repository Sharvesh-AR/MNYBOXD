// SPDX-FileCopyrightText: 2026 ARS (Sharvesh-AR)
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.sharveshar.spendly.domain.model

/** What a transaction does to money. Stored by [name] in the database; never rename constants. */
enum class TransactionType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer"),
    REFUND("Refund"),
}

/**
 * Kinds of accounts. Every account except [DEBIT_CARD] holds its own balance.
 *
 * Balances are kept in "net worth" terms: for a [CREDIT_CARD] the stored balance is the
 * negative of what is owed, so a purchase (balance − amount) increases the outstanding and a
 * repayment transfer (balance + amount) reduces it. A debit card has no balance of its own:
 * payments made with it are booked against its linked bank account.
 */
enum class AccountType(val label: String) {
    SAVINGS("Savings account"),
    CURRENT("Current account"),
    CASH("Cash wallet"),
    DEBIT_CARD("Debit card"),
    CREDIT_CARD("Credit card"),
    WALLET("Other wallet"),
    ;

    val holdsBalance: Boolean get() = this != DEBIT_CARD
    val isLiability: Boolean get() = this == CREDIT_CARD
    val isBank: Boolean get() = this == SAVINGS || this == CURRENT
}

/** How the payment was made (the rail), independent of which account funded it. */
enum class PaymentChannel(val label: String) {
    UPI("UPI"),
    CASH("Cash"),
    DEBIT_CARD("Debit card"),
    CREDIT_CARD("Credit card"),
    NET_BANKING("Net banking"),
    BANK_TRANSFER("Bank transfer"),
    WALLET("Wallet"),
    OTHER("Other"),
}

/** The app used to make a UPI or wallet payment. Purely descriptive; never moves money. */
enum class PaymentProvider(val label: String) {
    GOOGLE_PAY("Google Pay"),
    PHONEPE("PhonePe"),
    PAYTM("Paytm"),
    BHIM("BHIM"),
    AMAZON_PAY("Amazon Pay"),
    CRED("CRED"),
    WHATSAPP_PAY("WhatsApp Pay"),
    MOBIKWIK("MobiKwik"),
    OTHER("Other app"),
}

enum class CategoryKind { EXPENSE, INCOME }
