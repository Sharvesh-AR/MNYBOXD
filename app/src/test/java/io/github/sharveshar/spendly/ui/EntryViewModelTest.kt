package io.github.sharveshar.spendly.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import io.github.sharveshar.spendly.HangWatchdog
import io.github.sharveshar.spendly.Repositories
import io.github.sharveshar.spendly.data.db.SpendlyDatabase
import io.github.sharveshar.spendly.data.prefs.PreferencesRepository
import io.github.sharveshar.spendly.data.repo.AccountInput
import io.github.sharveshar.spendly.data.repo.SaveResult
import io.github.sharveshar.spendly.data.repo.WriteResult
import io.github.sharveshar.spendly.data.testDatabase
import io.github.sharveshar.spendly.domain.ledger.TransactionDraft
import io.github.sharveshar.spendly.domain.model.AccountType
import io.github.sharveshar.spendly.domain.model.PaymentChannel
import io.github.sharveshar.spendly.domain.model.PaymentProvider
import io.github.sharveshar.spendly.domain.model.TransactionType
import io.github.sharveshar.spendly.ui.entry.EntryEvent
import io.github.sharveshar.spendly.ui.entry.EntryViewModel
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** Quick Add form logic: voice pre-fill, edit, refund and process-death restore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryViewModelTest {
    @get:Rule val watchdog = HangWatchdog(2.minutes)
    private lateinit var db: SpendlyDatabase
    private lateinit var repos: Repositories
    private lateinit var prefs: PreferencesRepository
    private var sbi = 0L
    private var cc = 0L
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = runBlocking {
        db = testDatabase()
        repos = Repositories(db)
        prefs = PreferencesRepository(context)
        sbi = (repos.accounts.create(AccountInput("SBI Savings", "State Bank of India", AccountType.SAVINGS, 10_000_00, null, null, 0)) as WriteResult.Ok).id
        cc = (repos.accounts.create(AccountInput("ICICI Coral", "ICICI Bank", AccountType.CREDIT_CARD, 0, null, null, 1)) as WriteResult.Ok).id
    }

    /** Keeps each ViewModel's state flow subscribed so its combine() pipeline runs. */
    private val collectors = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun tearDown() {
        collectors.cancel()
        db.close()
    }

    private fun handle(editId: Long = 0, refundOf: Long = 0, type: String = "EXPENSE") = SavedStateHandle(
        mapOf("editId" to editId, "refundOf" to refundOf, "startVoice" to false, "type" to type, "accountId" to 0L, "origin" to ""),
    )

    /** Waits for the ViewModel's initial load (Room + DataStore run on background threads). */
    private fun EntryViewModel.awaitReady(): EntryViewModel = apply {
        runBlocking {
            withTimeout(5_000) {
                while (!(state.value.form.initialized && state.value.accounts.isNotEmpty() && state.value.categories.isNotEmpty())) {
                    ShadowLooper.idleMainLooper()
                    kotlinx.coroutines.delay(20)
                }
            }
        }
    }

    private fun EntryViewModel.saveAndAwait(): EntryEvent.Saved = runBlocking {
        save()
        withTimeout(5_000) {
            var event: EntryEvent? = null
            val job = collectors.launch { event = events.first() }
            while (event == null) { ShadowLooper.idleMainLooper(); kotlinx.coroutines.delay(20) }
            job.cancel()
            event as EntryEvent.Saved
        }
    }

    private fun newVm(h: SavedStateHandle = handle()) = EntryViewModel(repos, prefs, h).also { vm ->
        collectors.launch { vm.state.collect { } }
    }

    @Test fun `voice phrase pre-fills the form and nothing is saved until confirmed`() {
        val vm = newVm().awaitReady()
        vm.applyVoice("Spent 500 on petrol using PhonePe from SBI")
        ShadowLooper.idleMainLooper()
        val f = vm.state.value.form
        assertEquals("500", f.amountText)
        assertEquals(sbi, f.accountId)
        assertEquals(PaymentChannel.UPI, f.channel)
        assertEquals(PaymentProvider.PHONEPE, f.provider)
        assertEquals("Petrol", f.note)
        assertEquals("Spent 500 on petrol using PhonePe from SBI", f.voiceTranscript)
        assertTrue("nothing saved before confirmation", runBlocking { db.transactionDao().getAll() }.isEmpty())

        vm.saveAndAwait()
        val t = runBlocking { db.transactionDao().getAll() }.single()
        assertEquals(50_000L, t.amountPaise)
        assertEquals(sbi, t.accountId)
        assertEquals(PaymentProvider.PHONEPE, t.provider)
        assertEquals("fuel", runBlocking { db.categoryDao().getById(t.categoryId!!) }!!.key)
    }

    @Test fun `form survives process death via SavedStateHandle`() {
        val h = handle()
        val vm = newVm(h).awaitReady()
        vm.digit('7'); vm.digit('5'); vm.decimal(); vm.digit('5')
        vm.setNote("Auto")
        val restored = newVm(h).awaitReady()
        assertEquals("75.5", restored.state.value.form.amountText)
        assertEquals("Auto", restored.state.value.form.note)
        assertEquals(vm.state.value.form.clientRef, restored.state.value.form.clientRef)
    }

    @Test fun `editing updates the existing record instead of inserting`() {
        val food = runBlocking { db.categoryDao().getAll() }.first { it.key == "food" }.id
        val id = runBlocking {
            (repos.transactions.save(TransactionDraft(TransactionType.EXPENSE, 100_00, sbi, null, food, null, null, null, "Lunch", System.currentTimeMillis() - 60_000), "orig") as SaveResult.Saved).id
        }
        val vm = newVm(handle(editId = id)).awaitReady()
        assertEquals("100", vm.state.value.form.amountText)
        assertTrue(vm.state.value.isEdit)
        vm.clearAmount(); vm.digit('2'); vm.digit('5'); vm.digit('0')
        val saved = vm.saveAndAwait()
        assertTrue(saved.wasEdit)
        val all = runBlocking { db.transactionDao().getAll() }
        assertEquals(1, all.size)
        assertEquals(250_00L, all.single().amountPaise)
        assertEquals(9_750_00L, runBlocking { db.accountDao().observeWithBalances().first() }.first { it.account.id == sbi }.balancePaise)
    }

    @Test fun `refund form is prefilled with the refundable amount and links the original`() {
        val shopping = runBlocking { db.categoryDao().getAll() }.first { it.key == "shopping" }.id
        val id = runBlocking {
            (repos.transactions.save(TransactionDraft(TransactionType.EXPENSE, 2_000_00, cc, null, shopping, PaymentChannel.CREDIT_CARD, null, null, "Shoes", System.currentTimeMillis() - 60_000), "orig") as SaveResult.Saved).id
        }
        val vm = newVm(handle(refundOf = id)).awaitReady()
        val f = vm.state.value.form
        assertEquals(TransactionType.REFUND, f.type)
        assertEquals("2000", f.amountText)
        assertEquals(cc, f.accountId)
        assertEquals(2_000_00L, vm.state.value.refundLimitPaise)
        vm.clearAmount(); vm.digit('5'); vm.digit('0'); vm.digit('0')
        vm.saveAndAwait()
        val refund = runBlocking { db.transactionDao().getAll() }.single { it.type == TransactionType.REFUND }
        assertEquals(id, refund.refundOfId)
        assertEquals(500_00L, refund.amountPaise)
        assertEquals(shopping, refund.categoryId)
        assertEquals(1_500_00L, runBlocking { repos.transactions.refundableAmount(id) })
    }

    @Test fun `changing to a cash account switches the method to cash and clears the app`() {
        val vm = newVm().awaitReady()
        val cash = vm.state.value.accounts.first { it.type == AccountType.CASH }.id
        vm.setAccount(sbi); vm.setChannel(PaymentChannel.UPI); vm.setProvider(PaymentProvider.GOOGLE_PAY)
        vm.setAccount(cash)
        ShadowLooper.idleMainLooper()
        assertEquals(PaymentChannel.CASH, vm.state.value.form.channel)
        assertNull(vm.state.value.form.provider)
    }

    @Test fun `Quick Add opened on Income (the income widgets) starts on Income with income categories`() {
        val vm = newVm(handle(type = TransactionType.INCOME.name)).awaitReady()
        assertEquals(TransactionType.INCOME, vm.state.value.form.type)
        val names = vm.state.value.categories.map { it.name }
        assertTrue("Salary" in names)
        assertTrue("Food & Dining" !in names)
    }
}
