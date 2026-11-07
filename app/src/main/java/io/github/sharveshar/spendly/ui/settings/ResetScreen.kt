package io.github.sharveshar.spendly.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.sharveshar.spendly.AppContainer
import io.github.sharveshar.spendly.ui.components.IconBadge
import io.github.sharveshar.spendly.ui.components.PrimaryButton
import io.github.sharveshar.spendly.ui.components.SubScreen
import io.github.sharveshar.spendly.ui.theme.Space
import io.github.sharveshar.spendly.ui.theme.Spendly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What Reset Spendly deletes, shown on both confirmation steps. */
val RESET_DELETES = listOf(
    "All transactions, transfers and refunds",
    "All accounts, cards and their balances",
    "All budgets",
    "Custom categories (the default categories are put back)",
    "Your settings, theme and shortcut choices",
)

sealed interface ResetState {
    data object Idle : ResetState
    data object Working : ResetState
    data object Done : ResetState
    data class Failed(val message: String) : ResetState
}

class ResetViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<ResetState>(ResetState.Idle)
    val state: StateFlow<ResetState> = _state.asStateFlow()

    /**
     * Runs the reset only for the exact confirmation phrase and only once: repeated taps while it
     * runs, or after it finished, do nothing.
     */
    fun reset(phrase: String) {
        if (!isConfirmed(phrase)) return
        if (_state.value == ResetState.Working || _state.value == ResetState.Done) return
        _state.value = ResetState.Working
        viewModelScope.launch {
            _state.value = try {
                container.resetEverything()
                ResetState.Done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ResetState.Failed("Nothing was deleted. Spendly couldn't reset (${e.javaClass.simpleName}). Please try again.")
            }
        }
    }

    companion object {
        const val PHRASE = "DELETE EVERYTHING"

        /** Exact match only: same letters, same case, same single space. */
        fun isConfirmed(phrase: String) = phrase == PHRASE
    }
}

/**
 * Second step of Settings → Data management → Reset Spendly. The final button is enabled only when
 * the user has typed DELETE EVERYTHING exactly. Leaving, rotating or cancelling never deletes
 * anything; the typed text survives rotation but never triggers the reset by itself.
 */
@Composable
fun ResetScreen(viewModel: ResetViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var typed by rememberSaveable { mutableStateOf("") }
    val confirmed = ResetViewModel.isConfirmed(typed)
    val working = state == ResetState.Working || state == ResetState.Done

    LaunchedEffect(state) {
        if (state == ResetState.Done) {
            Toast.makeText(context.applicationContext, "Spendly has been reset. All your data was deleted.", Toast.LENGTH_LONG).show()
        }
    }

    SubScreen("Reset Spendly", onBack = { if (!working) onBack() }) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Space.gutter),
        ) {
            Spacer(Modifier.height(Space.lg))
            IconBadge(Icons.Rounded.WarningAmber, Spendly.colors.negative, size = 64.dp)
            Spacer(Modifier.height(Space.xl))
            Text(
                "This will permanently delete all your Spendly data.",
                style = MaterialTheme.typography.headlineSmall,
                color = Spendly.colors.negative,
            )
            Spacer(Modifier.height(Space.md))
            Text("It can't be undone, and there is no copy anywhere else. Deleted:", style = MaterialTheme.typography.bodyMedium, color = Spendly.colors.muted)
            Spacer(Modifier.height(Space.sm))
            RESET_DELETES.forEach {
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("•", color = Spendly.colors.muted)
                    Spacer(Modifier.width(Space.sm))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(Space.xl))
            Text(
                buildConfirmLabel(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(Space.sm))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it.take(40) },
                singleLine = true,
                enabled = !working,
                placeholder = { Text(ResetViewModel.PHRASE, color = Spendly.colors.muted.copy(alpha = 0.5f)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            (state as? ResetState.Failed)?.let {
                Spacer(Modifier.height(Space.md))
                Text(it.message, color = Spendly.colors.negative, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Spacer(Modifier.height(Space.xxl))
            PrimaryButton(
                text = "Delete everything permanently",
                onClick = { viewModel.reset(typed) },
                enabled = confirmed && !working,
                loading = working,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.md))
            OutlinedButton(onClick = onBack, enabled = !working, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Cancel") }
            Spacer(Modifier.height(Space.xxxl))
        }
    }
}

@Composable
private fun buildConfirmLabel() = androidx.compose.ui.text.buildAnnotatedString {
    append("To confirm, type ")
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
    append(ResetViewModel.PHRASE)
    pop()
    append(" exactly:")
}
