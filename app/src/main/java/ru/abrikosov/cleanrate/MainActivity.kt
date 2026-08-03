package ru.abrikosov.cleanrate

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.abrikosov.cleanrate.ui.ConverterScreen
import ru.abrikosov.cleanrate.ui.ConverterViewModel
import ru.abrikosov.cleanrate.ui.HistoryViewModel
import ru.abrikosov.cleanrate.ui.RateChartScreen
import ru.abrikosov.cleanrate.ui.theme.CleanRateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        setContent {
            CleanRateTheme {
                val converterViewModel: ConverterViewModel = viewModel()
                val historyViewModel: HistoryViewModel = viewModel()
                val converterState by converterViewModel.uiState.collectAsStateWithLifecycle()
                val historyState by historyViewModel.uiState.collectAsStateWithLifecycle()

                BackHandler(enabled = converterState.isChartVisible) {
                    converterViewModel.closeChart()
                }
                LaunchedEffect(converterState.isChartVisible) {
                    if (converterState.isChartVisible) {
                        val quote = converterState.favorites
                            .firstOrNull { it != converterState.activeCode }
                            ?: if (converterState.activeCode == "USD") "EUR" else "USD"
                        historyViewModel.initialize(
                            defaultBase = converterState.activeCode,
                            defaultQuote = quote,
                            amount = converterState.amount,
                        )
                    }
                }

                if (converterState.isChartVisible) {
                    RateChartScreen(
                        state = historyState,
                        language = converterState.uiLanguage,
                        allCodes = converterState.allCodes,
                        onBack = converterViewModel::closeChart,
                        onRefresh = historyViewModel::refresh,
                        onSelectBase = historyViewModel::selectBase,
                        onSelectQuote = historyViewModel::selectQuote,
                        onSwapCurrencies = historyViewModel::swapCurrencies,
                        onSelectPeriod = historyViewModel::selectPeriod,
                        onAmountChange = historyViewModel::setAmount,
                    )
                } else {
                    ConverterScreen(
                        state = converterState,
                        viewModel = converterViewModel,
                        onOpenChart = converterViewModel::openChart,
                    )
                }
            }
        }
    }
}
