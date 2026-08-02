package ru.abrikosov.cleanrate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import ru.abrikosov.cleanrate.data.CurrencyCatalog
import ru.abrikosov.cleanrate.data.CurrencyMetadata
import ru.abrikosov.cleanrate.data.CbrRepository
import ru.abrikosov.cleanrate.data.CurrencyRepository
import ru.abrikosov.cleanrate.data.RateOrigin
import ru.abrikosov.cleanrate.data.RateSource
import ru.abrikosov.cleanrate.data.UiLanguage
import ru.abrikosov.cleanrate.domain.ConversionEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    state: ConverterUiState,
    viewModel: ConverterViewModel,
    onOpenChart: () -> Unit,
) {
    var showCurrencyManager by rememberSaveable { mutableStateOf(false) }
    var showSourcePicker by rememberSaveable { mutableStateOf(false) }
    var showLanguagePicker by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
    var isCalculatorVisible by rememberSaveable { mutableStateOf(true) }
    var editRateCode by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val text = remember(state.uiLanguage) { UiText(state.uiLanguage) }
    val keyFeedback = rememberKeyFeedbackController()

    LaunchedEffect(state.message, state.uiLanguage) {
        state.message?.let {
            snackbarHostState.showSnackbar(text.message(it))
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text.appName, fontWeight = FontWeight.SemiBold)
                        Text(
                            text.tagline,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showLanguagePicker = true },
                        modifier = Modifier.semantics { contentDescription = text.changeLanguage },
                    ) {
                        Text(state.uiLanguage.shortLabel, fontWeight = FontWeight.Bold)
                    }
                    if (state.isRefreshing) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { viewModel.refreshRates() }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = text.refreshRates)
                        }
                    }
                    IconButton(onClick = { showCurrencyManager = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = text.settings)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .padding(bottom = 10.dp),
        ) {
            RateStatus(state, text, onSelectSource = { showSourcePicker = true })
            Spacer(Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(state.favorites, key = { it }) { code ->
                        val converted = remember(
                            state.amount,
                            state.activeCode,
                            state.effectiveRates,
                            code,
                        ) {
                            ConversionEngine.convert(
                                amount = state.amount,
                                fromCode = state.activeCode,
                                toCode = code,
                                rates = state.effectiveRates,
                            )
                        }
                        CurrencyRow(
                            metadata = CurrencyCatalog.metadata(code, state.uiLanguage),
                            value = if (code == state.activeCode) {
                                ValueFormatter.expression(state.expression, state.uiLanguage)
                            } else {
                                ValueFormatter.amount(converted, state.uiLanguage)
                            },
                            isActive = code == state.activeCode,
                            rateOrigin = state.originFor(code),
                            showOrigin = state.rateSource != RateSource.MARKET,
                            text = text,
                            onClick = {
                                viewModel.selectCurrency(code)
                                isCalculatorVisible = true
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onOpenChart,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ShowChart, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text.charts)
                }
                FilledTonalButton(
                    onClick = { isCalculatorVisible = !isCalculatorVisible },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = if (isCalculatorVisible) {
                                text.hideKeyboard
                            } else {
                                text.showKeyboard
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Icon(
                        imageVector = if (isCalculatorVisible) {
                            Icons.Outlined.KeyboardArrowDown
                        } else {
                            Icons.Outlined.KeyboardArrowUp
                        },
                        contentDescription = null,
                    )
                }
            }

            if (isCalculatorVisible) {
                CalculatorPad(
                    language = state.uiLanguage,
                    text = text,
                    soundEnabled = state.keySoundEnabled,
                    vibrationEnabled = state.keyVibrationEnabled,
                    keyFeedback = keyFeedback,
                    onKey = viewModel::pressKey,
                )
            }
        }
    }

    if (showCurrencyManager) {
        CurrencyManagerSheet(
            state = state,
            text = text,
            onDismiss = { showCurrencyManager = false },
            onToggleFavorite = viewModel::toggleFavorite,
            onMoveFavorite = viewModel::moveFavorite,
            onEditRate = { editRateCode = it },
            soundEnabled = state.keySoundEnabled,
            vibrationEnabled = state.keyVibrationEnabled,
            onSoundEnabledChange = { enabled ->
                viewModel.setKeySoundEnabled(enabled)
                if (enabled) keyFeedback.perform(soundEnabled = true, vibrationEnabled = false)
            },
            onVibrationEnabledChange = { enabled ->
                viewModel.setKeyVibrationEnabled(enabled)
                if (enabled) keyFeedback.perform(soundEnabled = false, vibrationEnabled = true)
            },
            onOpenPrivacy = {
                showCurrencyManager = false
                showPrivacyPolicy = true
            },
        )
    }

    if (showSourcePicker) {
        RateSourcePickerSheet(
            selectedSource = state.rateSource,
            manualRateCount = state.manualRates.size,
            text = text,
            onSelect = {
                viewModel.selectRateSource(it)
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false },
        )
    }

    if (showLanguagePicker) {
        LanguagePickerSheet(
            selectedLanguage = state.uiLanguage,
            text = text,
            onSelect = {
                viewModel.selectUiLanguage(it)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
    }

    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(
            text = text,
            onDismiss = { showPrivacyPolicy = false },
        )
    }

    editRateCode?.let { code ->
        ManualRateDialog(
            code = code,
            automaticRate = if (state.rateSource == RateSource.CBR) {
                state.cbrSnapshot.rates[code] ?: state.marketSnapshot.rates[code]
            } else {
                state.marketSnapshot.rates[code]
            },
            manualRate = state.manualRates[code],
            language = state.uiLanguage,
            text = text,
            onDismiss = { editRateCode = null },
            onSave = {
                viewModel.setManualRate(code, it)
                editRateCode = null
            },
            onReset = {
                viewModel.setManualRate(code, null)
                editRateCode = null
            },
        )
    }
}

@Composable
private fun RateStatus(
    state: ConverterUiState,
    text: UiText,
    onSelectSource: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val health = RateStatusPolicy.evaluate(state)
    val loadedFromSeed = health.loadedFromSeed
    val isStale = health.isStale
    val statusText = when (state.rateSource) {
        RateSource.MARKET -> if (isStale) {
            text.outdatedRate
        } else if (state.marketSnapshot.loadedFromSeed) {
            text.savedMarketRate
        } else {
            text.rateFor(ValueFormatter.date(state.marketSnapshot.rateDate, state.uiLanguage))
        }
        RateSource.CBR -> if (isStale) {
            text.outdatedRate
        } else {
            text.cbrRateFor(ValueFormatter.date(state.cbrSnapshot.officialDate, state.uiLanguage))
        }
        RateSource.CUSTOM -> if (state.manualRates.isEmpty()) {
            text.bankRatesNotSet
        } else {
            text.ownRateCount(state.manualRates.size)
        }
    }
    val lastCheckedEpochSeconds = health.lastCheckedEpochSeconds
    val detailText = if (isStale) {
        text.refreshRequired
    } else lastCheckedEpochSeconds?.let {
        text.checked(ValueFormatter.date(it, state.uiLanguage))
    } ?: when (state.rateSource) {
        RateSource.MARKET -> text.marketReference
        RateSource.CBR -> text.unpublishedUseMarket
        RateSource.CUSTOM -> text.remainingUseMarket
    }
    val sourceLabel = text.sourceName(state.rateSource, compact = true)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = if (isStale) {
                    MaterialTheme.colorScheme.error
                } else if (loadedFromSeed) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            ) {}
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    detailText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    modifier = Modifier.clickable(onClick = onSelectSource),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 9.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(sourceLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = text.chooseRateSource,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.rateSource == RateSource.CBR) {
                        Text(
                            "cbr.ru",
                            modifier = Modifier.clickable { uriHandler.openUri(CbrRepository.OFFICIAL_URL) },
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(" · ", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        CurrencyRepository.ATTRIBUTION_LABEL,
                        modifier = Modifier.clickable { uriHandler.openUri(CurrencyRepository.ATTRIBUTION_URL) },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyRow(
    metadata: CurrencyMetadata,
    value: String,
    isActive: Boolean,
    rateOrigin: RateOrigin,
    showOrigin: Boolean,
    text: UiText,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clickable(onClick = onClick),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurrencyBadge(metadata)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        metadata.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (showOrigin) {
                        Spacer(Modifier.width(6.dp))
                        RateOriginBadge(rateOrigin, text)
                    }
                }
                Text(
                    metadata.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                value,
                modifier = Modifier.widthIn(min = 126.dp, max = 205.dp),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (isActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun RateOriginBadge(origin: RateOrigin, text: UiText) {
    val label = text.originName(origin)
    val color = when (origin) {
        RateOrigin.MARKET -> MaterialTheme.colorScheme.surfaceVariant
        RateOrigin.CBR -> MaterialTheme.colorScheme.secondaryContainer
        RateOrigin.CUSTOM -> MaterialTheme.colorScheme.primaryContainer
    }
    Surface(shape = RoundedCornerShape(7.dp), color = color) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun CurrencyBadge(metadata: CurrencyMetadata) {
    val badgeColors = listOf(
        Color(0xFFD8EDEA),
        Color(0xFFFFE3C7),
        Color(0xFFDDE5FF),
        Color(0xFFF3DDED),
        Color(0xFFE8E1D5),
    )
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = badgeColors[ValueFormatter.colorIndex(metadata.code, badgeColors.size)],
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(metadata.flag, fontSize = 23.sp, color = Color(0xFF15201E))
        }
    }
}

@Composable
private fun CalculatorPad(
    language: UiLanguage,
    text: UiText,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    keyFeedback: KeyFeedbackController,
    onKey: (String) -> Unit,
) {
    val decimalKey = if (language == UiLanguage.ENGLISH) "." else ","
    val rows = listOf(
        listOf("C", "±", "÷", "⌫"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("0", "00", decimalKey, "="),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { keys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                keys.forEach { key ->
                    val containerColor = when (key) {
                        "C" -> MaterialTheme.colorScheme.errorContainer
                        "=" -> MaterialTheme.colorScheme.primary
                        "±", "÷", "×", "−", "+", "⌫" -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val contentColor = when (key) {
                        "C" -> MaterialTheme.colorScheme.error
                        "=" -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    FilledTonalButton(
                        onClick = {
                            keyFeedback.perform(soundEnabled, vibrationEnabled)
                            onKey(if (key == ".") "," else key)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .semantics {
                                contentDescription = when (key) {
                                    "⌫" -> text.deleteCharacter
                                    "±" -> text.changeSign
                                    "=" -> text.equals
                                    else -> key
                                }
                            },
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = containerColor,
                            contentColor = contentColor,
                        ),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RateSourcePickerSheet(
    selectedSource: RateSource,
    manualRateCount: Int,
    text: UiText,
    onSelect: (RateSource) -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text.sourcePickerTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text.sourcePickerIntro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            RateSourceOption(
                source = RateSource.MARKET,
                selected = selectedSource == RateSource.MARKET,
                description = text.marketSourceDescription,
                text = text,
                onClick = { onSelect(RateSource.MARKET) },
            )
            RateSourceOption(
                source = RateSource.CBR,
                selected = selectedSource == RateSource.CBR,
                description = text.cbrSourceDescription,
                text = text,
                onClick = { onSelect(RateSource.CBR) },
            )
            RateSourceOption(
                source = RateSource.CUSTOM,
                selected = selectedSource == RateSource.CUSTOM,
                description = if (manualRateCount == 0) {
                    text.addRatesWithPencil
                } else {
                    text.customSourceDescription(manualRateCount)
                },
                text = text,
                onClick = { onSelect(RateSource.CUSTOM) },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text.cbrDisclaimer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { uriHandler.openUri(CbrRepository.OFFICIAL_URL) }) {
                    Text(text.cbrData)
                }
                TextButton(onClick = { uriHandler.openUri(CurrencyRepository.ATTRIBUTION_URL) }) {
                    Text(text.marketData)
                }
            }
        }
    }
}

@Composable
private fun RateSourceOption(
    source: RateSource,
    selected: Boolean,
    description: String,
    text: UiText,
    onClick: () -> Unit,
) {
    val title = text.sourceName(source)
    val icon = when (source) {
        RateSource.MARKET -> Icons.Outlined.Public
        RateSource.CBR -> Icons.Outlined.AccountBalance
        RateSource.CUSTOM -> Icons.Outlined.CreditCard
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = text.selected,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(
    selectedLanguage: UiLanguage,
    text: UiText,
    onSelect: (UiLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text.languagePickerTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text.languagePickerIntro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            UiLanguage.entries.forEach { language ->
                val selected = language == selectedLanguage
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(language) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(language.shortLabel, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text.languageName(language),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (selected) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = text.selected,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyManagerSheet(
    state: ConverterUiState,
    text: UiText,
    onDismiss: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onMoveFavorite: (String, Int) -> Unit,
    onEditRate: (String) -> Unit,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    onVibrationEnabledChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val filteredCodes = remember(state.allCodes, normalizedQuery, state.uiLanguage) {
        state.allCodes.filter { code ->
            val metadata = CurrencyCatalog.metadata(code, state.uiLanguage)
            normalizedQuery.isBlank() ||
                code.lowercase().contains(normalizedQuery) ||
                metadata.name.lowercase().contains(normalizedQuery)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.94f)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text.settings,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text.settingsIntro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPrivacy) {
                Icon(Icons.Outlined.PrivacyTip, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(text.privacyPolicy)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text.keypadFeedback,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            FeedbackToggleRow(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = text.keySound,
                description = text.keySoundDescription,
                checked = soundEnabled,
                onCheckedChange = onSoundEnabledChange,
            )
            FeedbackToggleRow(
                icon = Icons.Outlined.Vibration,
                title = text.keyVibration,
                description = text.keyVibrationDescription,
                checked = vibrationEnabled,
                onCheckedChange = onVibrationEnabledChange,
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text(
                text.currenciesOnScreen,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text.currencyManagerIntro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                label = { Text(text.currencySearch) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                if (normalizedQuery.isBlank()) {
                    item {
                        SectionTitle(text.onMainScreen)
                    }
                    items(state.favorites, key = { "favorite-$it" }) { code ->
                        val index = state.favorites.indexOf(code)
                        CurrencyManagerItem(
                            metadata = CurrencyCatalog.metadata(code, state.uiLanguage),
                            text = text,
                            isFavorite = true,
                            hasManualRate = code in state.manualRates,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.favorites.lastIndex,
                            onToggleFavorite = { onToggleFavorite(code) },
                            onMoveUp = { onMoveFavorite(code, -1) },
                            onMoveDown = { onMoveFavorite(code, 1) },
                            onEditRate = { onEditRate(code) },
                        )
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SectionTitle(text.allCurrencies)
                    }
                }

                val codes = if (normalizedQuery.isBlank()) {
                    filteredCodes.filterNot { it in state.favorites }
                } else {
                    filteredCodes
                }
                items(codes, key = { "all-$it" }) { code ->
                    CurrencyManagerItem(
                        metadata = CurrencyCatalog.metadata(code, state.uiLanguage),
                        text = text,
                        isFavorite = code in state.favorites,
                        hasManualRate = code in state.manualRates,
                        canMoveUp = false,
                        canMoveDown = false,
                        onToggleFavorite = { onToggleFavorite(code) },
                        onMoveUp = {},
                        onMoveDown = {},
                        onEditRate = { onEditRate(code) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyPolicyDialog(
    text: UiText,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
        title = { Text(text.privacyPolicy) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text.privacySummary)
                Text(text.privacyNetwork)
                Text(text.privacyStorage)
                Text(text.privacyPermissions)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text.close)
            }
        },
    )
}

@Composable
private fun FeedbackToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 10.dp, bottom = 5.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CurrencyManagerItem(
    metadata: CurrencyMetadata,
    text: UiText,
    isFavorite: Boolean,
    hasManualRate: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleFavorite: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEditRate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleFavorite)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CurrencyBadge(metadata)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${metadata.code} · ${metadata.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hasManualRate) {
                Text(
                    text.manualRateInUse,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
        if (isFavorite && (canMoveUp || canMoveDown)) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(34.dp).alpha(if (canMoveUp) 1f else 0.25f),
            ) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = text.moveUp(metadata.code))
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(34.dp).alpha(if (canMoveDown) 1f else 0.25f),
            ) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = text.moveDown(metadata.code))
            }
        }
        IconButton(
            onClick = onEditRate,
            enabled = metadata.code != "USD",
            modifier = Modifier.size(38.dp),
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = text.editRate(metadata.code))
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(40.dp)) {
            Icon(
                if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) {
                    text.removeCurrency(metadata.code)
                } else {
                    text.addCurrency(metadata.code)
                },
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManualRateDialog(
    code: String,
    automaticRate: BigDecimal?,
    manualRate: BigDecimal?,
    language: UiLanguage,
    text: UiText,
    onDismiss: () -> Unit,
    onSave: (BigDecimal) -> Unit,
    onReset: () -> Unit,
) {
    var value by remember(code, manualRate, automaticRate, language) {
        mutableStateOf(ValueFormatter.rate(manualRate ?: automaticRate, language))
    }
    var hasError by rememberSaveable(code) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text.manualRateTitle(code)) },
        text = {
            Column {
                Text(text.manualRatePrompt(code))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it.filter { char -> char.isDigit() || char == ',' || char == '.' }
                        hasError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("1 USD = … $code") },
                    supportingText = {
                        if (hasError) {
                            Text(text.enterPositiveNumber)
                        } else {
                            Text(text.referenceRate(ValueFormatter.rate(automaticRate, language)))
                        }
                    },
                    isError = hasError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = value.replace(',', '.').toBigDecimalOrNull()
                if (parsed == null || parsed <= BigDecimal.ZERO) {
                    hasError = true
                } else {
                    onSave(parsed)
                }
            }) {
                Text(text.save)
            }
        },
        dismissButton = {
            Row {
                if (manualRate != null) {
                    TextButton(onClick = onReset) { Text(text.reset) }
                }
                TextButton(onClick = onDismiss) { Text(text.cancel) }
            }
        },
    )
}
