package ru.abrikosov.cleanrate.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.max
import ru.abrikosov.cleanrate.data.ChartPeriod
import ru.abrikosov.cleanrate.data.CurrencyCatalog
import ru.abrikosov.cleanrate.data.CurrencyMetadata
import ru.abrikosov.cleanrate.data.HistoricalRatePoint
import ru.abrikosov.cleanrate.data.HistoricalRatesRepository
import ru.abrikosov.cleanrate.data.UiLanguage

private enum class CurrencyPickerSide { BASE, QUOTE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateChartScreen(
    state: HistoryUiState,
    language: UiLanguage,
    allCodes: List<String>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectBase: (String) -> Unit,
    onSelectQuote: (String) -> Unit,
    onSwapCurrencies: () -> Unit,
    onSelectPeriod: (ChartPeriod) -> Unit,
    onAmountChange: (String) -> Unit,
) {
    var pickerSide by rememberSaveable { mutableStateOf<CurrencyPickerSide?>(null) }
    var selectedDate by rememberSaveable(
        state.baseCode,
        state.quoteCode,
        state.period.name,
    ) { mutableStateOf<String?>(null) }
    val text = remember(language) { UiText(language) }
    val view = LocalView.current
    val selectedIndex = state.points
        .indexOfFirst { it.date.toString() == selectedDate }
        .takeIf { it >= 0 }
    val selectedPoint = selectedIndex?.let(state.points::getOrNull)

    LaunchedEffect(state.points, selectedDate) {
        if (selectedDate != null && selectedPoint == null && state.points.isNotEmpty()) {
            selectedDate = null
        }
    }

    val selectPoint: (Int, Boolean) -> Unit = { index, toggle ->
        state.points.getOrNull(index)?.let { point ->
            val date = point.date.toString()
            val nextDate = if (toggle && selectedDate == date) null else date
            if (nextDate != selectedDate) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                selectedDate = nextDate
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text.chartTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = text.back)
                    }
                },
                actions = {
                    if (state.isLoading) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = text.refreshRates)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                CurrencyPairCard(
                    base = CurrencyCatalog.metadata(state.baseCode, language),
                    quote = CurrencyCatalog.metadata(state.quoteCode, language),
                    text = text,
                    onSelectBase = { pickerSide = CurrencyPickerSide.BASE },
                    onSelectQuote = { pickerSide = CurrencyPickerSide.QUOTE },
                    onSwap = onSwapCurrencies,
                )
            }
            item {
                CurrentConversionCard(
                    state = state,
                    text = text,
                    language = language,
                    selectedPoint = selectedPoint,
                    onAmountChange = onAmountChange,
                )
            }
            item {
                PeriodSelector(
                    selected = state.period,
                    text = text,
                    onSelect = onSelectPeriod,
                )
            }
            item {
                HistoryChartCard(
                    state = state,
                    text = text,
                    language = language,
                    selectedPoint = selectedPoint,
                    selectedIndex = selectedIndex,
                    onPointTapped = { selectPoint(it, true) },
                    onPointDragged = { selectPoint(it, false) },
                    onClearSelection = { selectedDate = null },
                    onRetry = onRefresh,
                )
            }
        }
    }

    pickerSide?.let { side ->
        CurrencyPickerSheet(
            codes = allCodes,
            selectedCode = if (side == CurrencyPickerSide.BASE) state.baseCode else state.quoteCode,
            language = language,
            text = text,
            onSelect = { code ->
                if (side == CurrencyPickerSide.BASE) onSelectBase(code) else onSelectQuote(code)
                pickerSide = null
            },
            onDismiss = { pickerSide = null },
        )
    }
}

@Composable
private fun CurrencyPairCard(
    base: CurrencyMetadata,
    quote: CurrencyMetadata,
    text: UiText,
    onSelectBase: () -> Unit,
    onSelectQuote: () -> Unit,
    onSwap: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            CurrencyChoice(
                metadata = base,
                contentDescription = text.selectBaseCurrency,
                onClick = onSelectBase,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSwap) {
                Icon(Icons.Outlined.SwapHoriz, contentDescription = text.swapCurrencies)
            }
            CurrencyChoice(
                metadata = quote,
                contentDescription = text.selectQuoteCurrency,
                onClick = onSelectQuote,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CurrencyChoice(
    metadata: CurrencyMetadata,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurrencyBadge(metadata)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(metadata.code, fontWeight = FontWeight.Bold)
                Text(
                    metadata.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CurrentConversionCard(
    state: HistoryUiState,
    text: UiText,
    language: UiLanguage,
    selectedPoint: HistoricalRatePoint?,
    onAmountChange: (String) -> Unit,
) {
    val displayedPoint = selectedPoint ?: state.points.lastOrNull()
    val conversionText = "${ValueFormatter.amount(
        state.convertedAmountAt(displayedPoint?.rate),
        language,
    )} ${state.quoteCode}"
    val conversionStyle = when {
        conversionText.length >= 17 -> MaterialTheme.typography.titleMedium
        conversionText.length >= 14 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.headlineSmall
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.amountText,
                onValueChange = onAmountChange,
                modifier = Modifier.weight(0.46f),
                label = { Text("${text.amountForChart} · ${state.baseCode}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = remember(language) {
                    AmountGroupingVisualTransformation(language)
                },
                singleLine = true,
                shape = RoundedCornerShape(15.dp),
            )
            Column(
                modifier = Modifier.weight(0.54f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    displayedPoint?.let { point ->
                        text.valueFor(ValueFormatter.shortDate(point.date, language))
                    } ?: text.latestValue,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    conversionText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    style = conversionStyle,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selected: ChartPeriod,
    text: UiText,
    onSelect: (ChartPeriod) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ChartPeriod.entries.forEach { period ->
            val isSelected = period == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(period) },
                shape = RoundedCornerShape(13.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
            ) {
                Text(
                    text.chartPeriod(period),
                    modifier = Modifier.padding(vertical = 11.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun HistoryChartCard(
    state: HistoryUiState,
    text: UiText,
    language: UiLanguage,
    selectedPoint: HistoricalRatePoint?,
    selectedIndex: Int?,
    onPointTapped: (Int) -> Unit,
    onPointDragged: (Int) -> Unit,
    onClearSelection: () -> Unit,
    onRetry: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 380.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(text.minimum, style = MaterialTheme.typography.labelMedium)
                    Text(
                        ValueFormatter.chartRate(state.minimumRate, language),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text.maximum, style = MaterialTheme.typography.labelMedium)
                    Text(
                        ValueFormatter.chartRate(state.maximumRate, language),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            when {
                state.points.isNotEmpty() -> {
                    Spacer(Modifier.height(8.dp))
                    if (selectedPoint == null) {
                        Text(
                            text.chartTouchHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SelectedPointSummary(
                            point = selectedPoint,
                            baseCode = state.baseCode,
                            quoteCode = state.quoteCode,
                            language = language,
                            text = text,
                            onClearSelection = onClearSelection,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    RateLineChart(
                        points = state.points,
                        language = language,
                        contentDescription = selectedPoint?.let { point ->
                            text.selectedChartPoint(
                                state.baseCode,
                                state.quoteCode,
                                ValueFormatter.date(point.date, language),
                                ValueFormatter.chartRate(point.rate, language),
                            )
                        } ?: text.chartSummary(
                            state.baseCode,
                            state.quoteCode,
                            state.points.size,
                        ),
                        selectedIndex = selectedIndex,
                        selectPointLabel = text.selectChartPoint,
                        previousPointLabel = text.previousChartPoint,
                        nextPointLabel = text.nextChartPoint,
                        onPointTapped = onPointTapped,
                        onPointDragged = onPointDragged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                    )
                    if (state.isLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (state.missingPointCount > 0) {
                        Text(
                            text.incompleteHistory(state.missingPointCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.isLoading || !state.initialized -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(286.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(text.loadingHistory)
                        }
                    }
                }
                state.hasError -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(286.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(text.historyUnavailable, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(onClick = onRetry) { Text(text.retry) }
                        }
                    }
                }
            }

            if (state.loadedFromCache) {
                Text(
                    text.savedHistory,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.isStale) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text.historySource,
                modifier = Modifier.clickable {
                    uriHandler.openUri(HistoricalRatesRepository.ATTRIBUTION_URL)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text.historyDisclaimer,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectedPointSummary(
    point: HistoricalRatePoint,
    baseCode: String,
    quoteCode: String,
    language: UiLanguage,
    text: UiText,
    onClearSelection: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ValueFormatter.date(point.date, language),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "1 $baseCode = ${ValueFormatter.chartRate(point.rate, language)} $quoteCode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClearSelection) {
                Text(text.showLatestValue)
            }
        }
    }
}

@Composable
private fun RateLineChart(
    points: List<HistoricalRatePoint>,
    language: UiLanguage,
    contentDescription: String,
    selectedIndex: Int?,
    selectPointLabel: String,
    previousPointLabel: String,
    nextPointLabel: String,
    onPointTapped: (Int) -> Unit,
    onPointDragged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val labelTextSize = with(density) { 11.sp.toPx() }
    val currentOnPointTapped by rememberUpdatedState(onPointTapped)
    val currentOnPointDragged by rememberUpdatedState(onPointDragged)

    val interactiveModifier = modifier
        .semantics {
            this.contentDescription = contentDescription
            onClick(label = selectPointLabel) {
                if (points.isEmpty()) {
                    false
                } else {
                    currentOnPointDragged(selectedIndex ?: points.lastIndex)
                    true
                }
            }
            customActions = buildList {
                val currentIndex = selectedIndex ?: points.lastIndex
                if (currentIndex > 0) {
                    add(
                        CustomAccessibilityAction(previousPointLabel) {
                            currentOnPointDragged(currentIndex - 1)
                            true
                        },
                    )
                }
                if (currentIndex in 0 until points.lastIndex) {
                    add(
                        CustomAccessibilityAction(nextPointLabel) {
                            currentOnPointDragged(currentIndex + 1)
                            true
                        },
                    )
                }
            }
        }
        .pointerInput(points) {
            detectTapGestures { offset ->
                val index = ChartPointSelection.nearestIndex(
                    positionX = offset.x,
                    points = points,
                    chartLeft = 52.dp.toPx(),
                    chartRight = size.width - 8.dp.toPx(),
                )
                if (index >= 0) currentOnPointTapped(index)
            }
        }
        .pointerInput(points) {
            fun selectAt(positionX: Float) {
                val index = ChartPointSelection.nearestIndex(
                    positionX = positionX,
                    points = points,
                    chartLeft = 52.dp.toPx(),
                    chartRight = size.width - 8.dp.toPx(),
                )
                if (index >= 0) currentOnPointDragged(index)
            }
            detectHorizontalDragGestures(
                onDragStart = { offset -> selectAt(offset.x) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    selectAt(change.position.x)
                },
            )
        }

    Canvas(modifier = interactiveModifier) {
        if (points.size < 2) return@Canvas
        val values = points.map { it.rate.toDouble() }
        val rawMinimum = values.minOrNull() ?: return@Canvas
        val rawMaximum = values.maxOrNull() ?: return@Canvas
        val rawRange = rawMaximum - rawMinimum
        val padding = if (rawRange == 0.0) {
            max(abs(rawMaximum) * 0.01, 0.01)
        } else {
            rawRange * 0.08
        }
        val minimum = rawMinimum - padding
        val maximum = rawMaximum + padding
        val range = maximum - minimum

        val left = 52.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 9.dp.toPx()
        val bottom = size.height - 28.dp.toPx()
        val chartWidth = right - left
        val chartHeight = bottom - top

        val valuePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = labelTextSize
            textAlign = AndroidPaint.Align.RIGHT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        repeat(5) { index ->
            val fraction = index / 4f
            val y = top + chartHeight * fraction
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
            val value = maximum - range * fraction
            drawContext.canvas.nativeCanvas.drawText(
                ValueFormatter.chartRate(BigDecimal.valueOf(value), language),
                left - 7.dp.toPx(),
                y + labelTextSize * 0.35f,
                valuePaint,
            )
        }

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = left + chartWidth * ChartPointSelection.fractionForIndex(points, index)
            val normalized = ((point.rate.toDouble() - minimum) / range).toFloat()
            val y = bottom - chartHeight * normalized
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        if (selectedIndex == null) {
            val lastPoint = points.last()
            val lastY = bottom - chartHeight * ((lastPoint.rate.toDouble() - minimum) / range).toFloat()
            drawCircle(primaryColor, radius = 4.dp.toPx(), center = Offset(right, lastY))
        } else {
            val safeIndex = selectedIndex.coerceIn(points.indices)
            val selectedPoint = points[safeIndex]
            val selectedX = left + chartWidth * ChartPointSelection.fractionForIndex(points, safeIndex)
            val selectedY = bottom -
                chartHeight * ((selectedPoint.rate.toDouble() - minimum) / range).toFloat()
            drawLine(
                color = primaryColor.copy(alpha = 0.35f),
                start = Offset(selectedX, top),
                end = Offset(selectedX, bottom),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawCircle(surfaceColor, radius = 7.dp.toPx(), center = Offset(selectedX, selectedY))
            drawCircle(primaryColor, radius = 4.5.dp.toPx(), center = Offset(selectedX, selectedY))
        }

        val datePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = labelTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val middleIndex = ChartPointSelection.middleIndex(points)
        listOf(0, middleIndex, points.lastIndex).distinct().forEach { index ->
            val x = left + chartWidth * ChartPointSelection.fractionForIndex(points, index)
            datePaint.textAlign = when (index) {
                0 -> AndroidPaint.Align.LEFT
                points.lastIndex -> AndroidPaint.Align.RIGHT
                else -> AndroidPaint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                ValueFormatter.shortDate(points[index].date, language),
                x,
                size.height - 5.dp.toPx(),
                datePaint,
            )
        }
    }
}

internal object ChartPointSelection {
    fun nearestIndex(
        positionX: Float,
        points: List<HistoricalRatePoint>,
        chartLeft: Float,
        chartRight: Float,
    ): Int {
        if (points.isEmpty()) return -1
        if (points.size == 1) return 0
        val chartWidth = max(chartRight - chartLeft, 1f)
        val fraction = ((positionX - chartLeft) / chartWidth).coerceIn(0f, 1f)
        val firstDay = points.first().date.toEpochDay().toDouble()
        val lastDay = points.last().date.toEpochDay().toDouble()
        if (lastDay <= firstDay) return 0
        val targetDay = firstDay + (lastDay - firstDay) * fraction
        return points.indices.minByOrNull { index ->
            abs(points[index].date.toEpochDay() - targetDay)
        } ?: -1
    }

    fun fractionForIndex(points: List<HistoricalRatePoint>, index: Int): Float {
        if (points.size <= 1) return 0f
        val firstDay = points.first().date.toEpochDay()
        val lastDay = points.last().date.toEpochDay()
        if (lastDay <= firstDay) return 0f
        val day = points[index.coerceIn(points.indices)].date.toEpochDay()
        return ((day - firstDay).toDouble() / (lastDay - firstDay).toDouble()).toFloat()
    }

    fun middleIndex(points: List<HistoricalRatePoint>): Int {
        if (points.isEmpty()) return -1
        val firstDay = points.first().date.toEpochDay().toDouble()
        val lastDay = points.last().date.toEpochDay().toDouble()
        val middleDay = firstDay + (lastDay - firstDay) / 2.0
        return points.indices.minByOrNull { index ->
            abs(points[index].date.toEpochDay() - middleDay)
        } ?: 0
    }
}

internal class AmountGroupingVisualTransformation(
    private val language: UiLanguage,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val separatorIndex = raw.indexOfFirst { it == ',' || it == '.' }
        val integerEnd = if (separatorIndex >= 0) separatorIndex else raw.length
        val groupingSeparator = if (language == UiLanguage.ENGLISH) ',' else '\u00A0'
        val decimalSeparator = if (language == UiLanguage.ENGLISH) '.' else ','
        val output = StringBuilder()
        val originalToTransformed = IntArray(raw.length + 1)
        val transformedToOriginal = mutableListOf(0)

        raw.forEachIndexed { index, character ->
            val shouldGroup = index > 0 &&
                index < integerEnd &&
                (integerEnd - index) % 3 == 0
            if (shouldGroup) {
                output.append(groupingSeparator)
                transformedToOriginal += index
            }
            originalToTransformed[index] = output.length
            output.append(if (character == ',' || character == '.') decimalSeparator else character)
            transformedToOriginal += index + 1
            originalToTransformed[index + 1] = output.length
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToTransformed[offset.coerceIn(0, raw.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
        }
        return TransformedText(AnnotatedString(output.toString()), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPickerSheet(
    codes: List<String>,
    selectedCode: String,
    language: UiLanguage,
    text: UiText,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase()
    val filteredCodes = remember(codes, normalizedQuery, language) {
        codes.filter { code ->
            val metadata = CurrencyCatalog.metadata(code, language)
            normalizedQuery.isBlank() ||
                code.lowercase().contains(normalizedQuery) ||
                metadata.name.lowercase().contains(normalizedQuery)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text.chooseCurrency,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
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
                items(filteredCodes, key = { it }) { code ->
                    val metadata = CurrencyCatalog.metadata(code, language)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(code) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CurrencyBadge(metadata)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(metadata.code, fontWeight = FontWeight.Bold)
                            Text(
                                metadata.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (code == selectedCode) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = text.selected,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
