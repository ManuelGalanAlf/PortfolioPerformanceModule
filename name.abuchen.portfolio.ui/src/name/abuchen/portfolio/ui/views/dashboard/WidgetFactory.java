package name.abuchen.portfolio.ui.views.dashboard;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import name.abuchen.portfolio.math.AdvancedRiskMetrics;
import name.abuchen.portfolio.math.AllTimeHigh;
import name.abuchen.portfolio.math.Covariance;
import name.abuchen.portfolio.math.PortfolioAnalytics;
import name.abuchen.portfolio.math.Risk.Drawdown;
import name.abuchen.portfolio.math.Risk.Volatility;
import name.abuchen.portfolio.model.ClientProperties;
import name.abuchen.portfolio.model.Dashboard;
import name.abuchen.portfolio.model.Dashboard.Widget;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.ClientPerformanceSnapshot.CategoryType;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.views.dashboard.charts.ClientDataSeriesChartWidget;
import name.abuchen.portfolio.ui.views.dashboard.charts.DrawdownChartWidget;
import name.abuchen.portfolio.ui.views.dashboard.charts.HoldingsChartWidget;
import name.abuchen.portfolio.ui.views.dashboard.charts.PortfolioRiskContributionChartWidget;
import name.abuchen.portfolio.ui.views.dashboard.charts.RebalancingChartWidget;
import name.abuchen.portfolio.ui.views.dashboard.charts.RebalancingTargetChartWidget;
import name.abuchen.portfolio.ui.views.dashboard.charts.TaxonomyChartWidget;
import name.abuchen.portfolio.ui.views.dashboard.earnings.EarningsByTaxonomyChartWidget;
import name.abuchen.portfolio.ui.views.dashboard.earnings.EarningsChartWidget;
import name.abuchen.portfolio.ui.views.dashboard.earnings.EarningsHeatmapWidget;
import name.abuchen.portfolio.ui.views.dashboard.earnings.EarningsListWidget;
import name.abuchen.portfolio.ui.views.dashboard.earnings.EarningsListWidget.ExpansionSetting;
import name.abuchen.portfolio.ui.views.dashboard.heatmap.CostHeatmapWidget;
import name.abuchen.portfolio.ui.views.dashboard.heatmap.InvestmentHeatmapWidget;
import name.abuchen.portfolio.ui.views.dashboard.heatmap.PerformanceHeatmapWidget;
import name.abuchen.portfolio.ui.views.dashboard.heatmap.YearlyPerformanceHeatmapWidget;
import name.abuchen.portfolio.ui.views.dashboard.lists.EventListWidget;
import name.abuchen.portfolio.ui.views.dashboard.lists.FollowUpWidget;
import name.abuchen.portfolio.ui.views.dashboard.lists.LimitExceededWidget;
import name.abuchen.portfolio.ui.views.dataseries.DataSeries;
import name.abuchen.portfolio.ui.views.payments.PaymentsViewModel;

public enum WidgetFactory
{
    HEADING(Messages.LabelHeading, Messages.LabelCommon, HeadingWidget::new),

    DESCRIPTION(Messages.LabelDescription, Messages.LabelCommon, DescriptionWidget::new),

    TOTAL_SUM(Messages.LabelTotalSum, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidget.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        int length = index.getTotals().length;
                                        return Money.of(index.getCurrency(), index.getTotals()[length - 1]);
                                    })//
                                    .withBenchmarkDataSeries(false)//
                                    .build()),

    TTWROR(Messages.LabelTTWROR, Messages.ClientEditorLabelPerformance, // cumulative
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return index.getFinalAccumulatedPercentage();
                                    }).build()),

    TTWROR_ANNUALIZED(Messages.LabelTTWROR_Annualized, Messages.ClientEditorLabelPerformance, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.AnnualizedPercent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return index.getFinalAccumulatedAnnualizedPercentage();
                                    }).build()),

    IRR(Messages.LabelIRR, Messages.ClientEditorLabelPerformance, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.AnnualizedPercent2) //
                                    .with((ds, period) -> data.calculate(ds, period).getPerformanceIRR())//
                                    .build()),

    EXPECTED_RETURN_ANNUALIZED(Messages.LabelExpectedReturnAnnualized, Messages.ClientEditorLabelPerformance, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.AnnualizedPercent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return AdvancedRiskMetrics.annualizedExpectedReturn(index);
                                    }) //
                                    .withColoredValues(false)//
                                    .build()),

    ABSOLUTE_CHANGE(Messages.LabelAbsoluteChange, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidget.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        int length = index.getTotals().length;
                                        return Money.of(index.getCurrency(),
                                                        index.getTotals()[length - 1] - index.getTotals()[0]);
                                    })//
                                    .withBenchmarkDataSeries(false)//
                                    .build()),

    DELTA(Messages.LabelDelta, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidget.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        long[] d = data.calculate(ds, period).calculateDelta();
                                        return Money.of(data.getTermCurrency(), d.length > 0 ? d[d.length - 1] : 0L);
                                    })//
                                    .withBenchmarkDataSeries(false)//
                                    .build()),

    ABSOLUTE_DELTA(Messages.LabelAbsoluteDelta, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidget.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        long[] d = data.calculate(ds, period).calculateAbsoluteDelta();
                                        return Money.of(data.getTermCurrency(), d.length > 0 ? d[d.length - 1] : 0L);
                                    })//
                                    .withBenchmarkDataSeries(false)//
                                    .build()),

    SAVINGS(Messages.LabelPNTransfers, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidget.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        long[] d = data.calculate(ds, period).getTransferals();// skip d[0] because it refers to the
                                        // day before start
                                        return Money.of(data.getTermCurrency(),
                                                        d.length > 1 ? LongStream.of(d).skip(1).sum() : 0L);
                                    })//
                                    .withBenchmarkDataSeries(false)//
                                    .build()),

    MONTHLY_PN_TRANSFERS(Messages.LabelMonthlyPNTransfers, Messages.LabelStatementOfAssets,
                    MonthlyPNTransfersWidget::new),

    INVESTED_CAPITAL(Messages.LabelInvestedCapital, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidget.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        long[] d = data.calculate(ds, period).calculateInvestedCapital();
                                        return Money.of(data.getTermCurrency(), d.length > 0 ? d[d.length - 1] : 0L);
                                    })//
                                    .withBenchmarkDataSeries(false)//
                                    .build()),

    ABSOLUTE_INVESTED_CAPITAL(Messages.LabelAbsoluteInvestedCapital, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidget.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        long[] d = data.calculate(ds, period).calculateAbsoluteInvestedCapital();
                                        return Money.of(data.getTermCurrency(), d.length > 0 ? d[d.length - 1] : 0L);
                                    })//
                                    .withBenchmarkDataSeries(false)//
                                    .build()),

    RATIO(Messages.LabelRatio, Messages.LabelStatementOfAssets, RatioWidget::new),

    MAXDRAWDOWN(Messages.LabelMaxDrawdown, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return index.getDrawdown().getMaxDrawdown();
                                    }) //
                                    .withTooltip((ds, period) -> {
                                        DateTimeFormatter formatter = DateTimeFormatter
                                                        .ofLocalizedDate(FormatStyle.LONG)
                                                        .withZone(ZoneId.systemDefault());
                                        PerformanceIndex index = data.calculate(ds, period);
                                        Drawdown drawdown = index.getDrawdown();
                                        return MessageFormat.format(Messages.TooltipMaxDrawdown,
                                                        formatter.format(
                                                                        drawdown.getIntervalOfMaxDrawdown().getStart()),
                                                        formatter.format(drawdown.getIntervalOfMaxDrawdown().getEnd()));
                                    })//
                                    .withColoredValues(false)//
                                    .build()),

    CURRENT_DRAWDOWN(Messages.LabelCurrentDrawdown, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double[] accumulated = index.getAccumulatedPercentage();
                                        if (accumulated.length == 0)
                                            return 0.0;

                                        // Find the high watermark in
                                        // accumulated performance
                                        double highWatermark = Double.NEGATIVE_INFINITY;
                                        for (int i = 0; i < accumulated.length; i++)
                                        {
                                            if (accumulated[i] > highWatermark)
                                                highWatermark = accumulated[i];
                                        }

                                        // If no positive performance, no
                                        // drawdown
                                        if (highWatermark <= 0)
                                            return 0.0;

                                        // Current accumulated performance
                                        double currentValue = accumulated[accumulated.length - 1];

                                        // Calculate drawdown as percentage from
                                        // high watermark
                                        // This matches how the Drawdown class
                                        // calculates drawdown in Risk.java
                                        // Only return a drawdown if current
                                        // value is less than high watermark
                                        if (currentValue < highWatermark)
                                        {
                                            // Convert accumulated percentages
                                            // to values relative to 1
                                            // e.g., 0.25 (25% gain) becomes
                                            // 1.25
                                            double peakValue = 1 + highWatermark;
                                            double currentVal = 1 + currentValue;

                                            // Calculate drawdown using the same
                                            // formula as in Risk.Drawdown
                                            return -(peakValue - currentVal) / peakValue;
                                        }
                                        else
                                        {
                                            return 0.0;
                                        }
                                    }) //
                                    .withTooltip((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double[] accumulated = index.getAccumulatedPercentage();
                                        if (accumulated.length == 0)
                                            return Messages.TooltipCurrentDrawdown;

                                        // Find the high watermark date
                                        double highWatermark = Double.NEGATIVE_INFINITY;
                                        int highWatermarkIndex = 0;
                                        for (int i = 0; i < accumulated.length; i++)
                                        {
                                            if (accumulated[i] > highWatermark)
                                            {
                                                highWatermark = accumulated[i];
                                                highWatermarkIndex = i;
                                            }
                                        }

                                        DateTimeFormatter formatter = DateTimeFormatter
                                                        .ofLocalizedDate(FormatStyle.LONG)
                                                        .withZone(ZoneId.systemDefault());

                                        LocalDate highWatermarkDate = period.getStart().plusDays(highWatermarkIndex);
                                        return MessageFormat.format(Messages.TooltipCurrentDrawdown,
                                                        formatter.format(highWatermarkDate));
                                    })//
                                    .withColoredValues(true)//
                                    .build()),

    MAXDRAWDOWNDURATION(Messages.LabelMaxDrawdownDuration, Messages.LabelRiskIndicators,
                    MaxDrawdownDurationWidget::new),

    DRAWDOWN_CHART(Messages.LabelMaxDrawdownChart, Messages.LabelRiskIndicators, Images.VIEW_LINECHART,
                    DrawdownChartWidget::new),

    VOLATILITY(Messages.LabelVolatility, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return index.getVolatility().getStandardDeviation();
                                    }) //
                                    .withTooltip((ds, period) -> Messages.TooltipVolatility)//
                                    .withColoredValues(false)//
                                    .build()),

    SHARPE_RATIO(Messages.LabelSharpeRatio, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.PercentPlain) //
                                    .withColoredValues(false) //
                                    .withConfig(delegate -> new RiskFreeRateOfReturnConfig(delegate)) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double r = index.getPerformanceIRR();
                                        double rf = new ClientProperties(data.getClient()).getRiskFreeRateOfReturn();
                                        double volatility = index.getVolatility().getStandardDeviation();

                                        // handle invalid rf value
                                        if (Double.isNaN(rf))
                                            return Double.NaN;

                                        double excessReturn = r - rf;
                                        return excessReturn / volatility;
                                    }) //
                                    .withTooltip((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double r = index.getPerformanceIRR();
                                        double rf = new ClientProperties(data.getClient()).getRiskFreeRateOfReturn();
                                        double volatility = index.getVolatility().getStandardDeviation();
                                        double sharpeRatio = (r - rf) / volatility;
                                        return MessageFormat.format(Messages.TooltipSharpeRatio,
                                                        Values.Percent5.format(r), Values.Percent2.format(rf),
                                                        volatility, sharpeRatio);
                                    })//
                                    .build()),

    SHARPE_RATIO_ANNUALIZED(Messages.LabelSharpeRatioAnnualized, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.PercentPlain) //
                                    .withColoredValues(true) //
                                    .withConfig(delegate -> new RiskFreeRateOfReturnConfig(delegate)) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double rf = new ClientProperties(data.getClient()).getRiskFreeRateOfReturn();
                                        return AdvancedRiskMetrics.annualizedSharpeRatio(index, rf);
                                    }) //
                                    .withTooltip((ds, period) -> Messages.TooltipSharpeRatio)//
                                    .build()),
    VOLATILITY_ANNUALIZED(Messages.LabelVolatilityAnnualized, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double[] delta = index.getDeltaPercentage();
                                        return AdvancedRiskMetrics.annualizedStandardDeviation(delta);
                                    }) //
                                    .withTooltip((ds, period) -> Messages.TooltipVolatilityAnnualized)//
                                    .withColoredValues(false)//
                                    .build()),

    DOWNSIDE_RISK_ANNUALIZED(Messages.LabelDownsideRiskAnnualized, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .withColoredValues(false) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double[] delta = index.getDeltaPercentage();
                                        return AdvancedRiskMetrics.annualizedDownsideRisk(delta, 0.0);
                                    }) //
                                    .build()),

    SORTINO_RATIO_ANNUALIZED(Messages.LabelSortinoRatioAnnualized, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.PercentPlain) //
                                    .withColoredValues(true) //
                                    .withConfig(delegate -> new RiskFreeRateOfReturnConfig(delegate)) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        double rf = new ClientProperties(data.getClient()).getRiskFreeRateOfReturn();
                                        return AdvancedRiskMetrics.annualizedSortinoRatio(index, rf);
                                    }) //
                                    .build()),

    CALMAR_RATIO_ANNUALIZED(Messages.LabelCalmarRatioAnnualized, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.PercentPlain) //
                                    .withColoredValues(true) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return AdvancedRiskMetrics.annualizedCalmarRatio(index);
                                    }) //
                                    .build()),

    VALUE_AT_RISK_ANNUALIZED(Messages.LabelValueAtRiskAnnualized, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .withColoredValues(false) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return AdvancedRiskMetrics.annualizedValueAtRisk(index, 0.95);
                                    }) //
                                    .build()),

    TRACKING_ERROR_ANNUALIZED(Messages.LabelTrackingErrorAnnualized, Messages.LabelRiskIndicators,
                    (widget, data) -> new BenchmarkMetricWidget(widget, data,
                                    BenchmarkMetricWidget.MetricType.TRACKING_ERROR_ANNUALIZED)),

    INFORMATION_RATIO_ANNUALIZED(Messages.LabelInformationRatio, Messages.LabelRiskIndicators,
                    (widget, data) -> new BenchmarkMetricWidget(widget, data,
                                    BenchmarkMetricWidget.MetricType.INFORMATION_RATIO_ANNUALIZED)),

    SKEWNESS(Messages.LabelSkewness, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.PercentPlain) //
                                    .withColoredValues(true) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return AdvancedRiskMetrics.skewness(index);
                                    }) //
                                    .build()),

    EXCESS_KURTOSIS(Messages.LabelExcessKurtosis, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.PercentPlain) //
                                    .withColoredValues(false) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return AdvancedRiskMetrics.excessKurtosis(index);
                                    }) //
                                    .build()),

    SEMIVOLATILITY(Messages.LabelSemiVolatility, Messages.LabelRiskIndicators, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        return index.getVolatility().getSemiDeviation();
                                    }) //
                                    .withTooltip((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        Volatility vola = index.getVolatility();
                                        return MessageFormat.format(Messages.TooltipSemiVolatility,
                                                        Values.Percent5.format(vola.getExpectedSemiDeviation()),
                                                        vola.getNormalizedSemiDeviationComparison(),
                                                        Values.Percent5.format(vola.getStandardDeviation()),
                                                        Values.Percent5.format(vola.getSemiDeviation()));
                                    })//
                                    .withColoredValues(false)//
                                    .build()),

    CALCULATION(Messages.LabelPerformanceCalculation, Messages.ClientEditorLabelPerformance,
                    PerformanceCalculationWidget::new),

    CHART(Messages.LabelPerformanceChart, Messages.ClientEditorLabelPerformance, Images.VIEW_LINECHART,
                    (widget, data) -> new ChartWidget(widget, data, DataSeries.UseCase.PERFORMANCE)),

    ASSET_CHART(Messages.LabelAssetChart, Messages.LabelStatementOfAssets, Images.VIEW_LINECHART,
                    (widget, data) -> new ChartWidget(widget, data, DataSeries.UseCase.STATEMENT_OF_ASSETS)),

    HOLDINGS_CHART(Messages.LabelStatementOfAssetsHoldings, Messages.LabelStatementOfAssets, Images.VIEW_PIECHART,
                    HoldingsChartWidget::new),

    CLIENT_DATA_SERIES_CHART(Messages.LabelStatementOfAssetsDerivedDataSeries, Messages.LabelStatementOfAssets,
                    Images.VIEW_LINECHART, ClientDataSeriesChartWidget::new),

    TAXONOMY_CHART(Messages.LabelTaxonomies, Messages.LabelStatementOfAssets, Images.VIEW_PIECHART,
                    TaxonomyChartWidget::new),

    HEATMAP(Messages.LabelHeatmap, Messages.ClientEditorLabelPerformance, PerformanceHeatmapWidget::new),

    HEATMAP_YEARLY(Messages.LabelYearlyHeatmap, Messages.ClientEditorLabelPerformance,
                    YearlyPerformanceHeatmapWidget::new),

    EARNINGS(Messages.LabelEarningsTransactionList, Messages.LabelEarnings, //
                    config -> config.put(Dashboard.Config.LAYOUT.name(), ExpansionSetting.EXPAND_CURRENT_MONTH.name()),
                    EarningsListWidget::new),

    HEATMAP_EARNINGS(Messages.LabelHeatmapEarnings, Messages.LabelEarnings, EarningsHeatmapWidget::new),

    DIVIDEND_EVENT_LIST(Messages.LabelEarningsDividendList, Messages.LabelEarnings, DividendListWidget::new),

    EARNINGS_PER_YEAR_CHART(Messages.LabelEarningsPerYear, Messages.LabelEarnings, Images.VIEW_BARCHART,
                    EarningsChartWidget::perYear),

    EARNINGS_PER_QUARTER_CHART(Messages.LabelEarningsPerQuarter, Messages.LabelEarnings, Images.VIEW_BARCHART,
                    EarningsChartWidget::perQuarter),

    EARNINGS_PER_MONTH_CHART(Messages.LabelEarningsPerMonth, Messages.LabelEarnings, Images.VIEW_BARCHART,
                    EarningsChartWidget::perMonth),

    EARNINGS_BY_TAXONOMY(Messages.LabelEarningsByTaxonomy, Messages.LabelEarnings, Images.VIEW_PIECHART,
                    EarningsByTaxonomyChartWidget::new),

    TRADES_BASIC_STATISTICS(Messages.LabelTradesBasicStatistics, Messages.LabelTrades, TradesWidget::new),

    TRADES_PROFIT_LOSS(Messages.LabelTradesProfitLoss, Messages.LabelTrades, TradesProfitLossWidget::new),

    TRADES_AVERAGE_HOLDING_PERIOD(Messages.LabelAverageHoldingPeriod, Messages.LabelTrades,
                    TradesAverageHoldingPeriodWidget::new),

    TRADES_TURNOVER_RATIO(Messages.LabelTradesTurnoverRate, Messages.LabelTrades, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        OptionalDouble average = LongStream.of(index.getTotals()).average();
                                        if (!average.isPresent() || average.getAsDouble() <= 0)
                                            return 0.0;
                                        long buy = LongStream.of(index.getBuys()).sum();
                                        long sell = LongStream.of(index.getSells()).sum();
                                        return Long.min(buy, sell) / average.getAsDouble();
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .withTooltip((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        String currency = data.getCurrencyConverter().getTermCurrency();
                                        OptionalDouble average = LongStream.of(index.getTotals()).average();
                                        long buy = LongStream.of(index.getBuys()).sum();
                                        long sell = LongStream.of(index.getSells()).sum();
                                        return MessageFormat.format(Messages.TooltipTurnoverRate,
                                                        Values.Money.format(Money.of(currency, buy)),
                                                        Values.Money.format(Money.of(currency, sell)),
                                                        Values.Money.format(
                                                                        Money.of(currency, (long) average.orElse(0))),
                                                        Values.Percent2.format(
                                                                        average.isPresent() && average.getAsDouble() > 0
                                                                                        ? Long.min(buy, sell) / average
                                                                                                        .getAsDouble()
                                                                                        : 0));
                                    })//
                                    .withColoredValues(false).build()),

    HEATMAP_INVESTMENTS(Messages.LabelHeatmapInvestments, Messages.LabelTrades, InvestmentHeatmapWidget::new),

    HEATMAP_TAXES(Messages.LabelHeatmapTaxes, Messages.LabelTrades,
                    (widget, data) -> new CostHeatmapWidget(widget, data, PaymentsViewModel.Mode.TAXES)),

    HEATMAP_FEES(Messages.LabelHeatmapFees, Messages.LabelTrades,
                    (widget, data) -> new CostHeatmapWidget(widget, data, PaymentsViewModel.Mode.FEES)),

    PORTFOLIO_TAX_RATE(Messages.LabelPortfolioTaxRate, Messages.ClientEditorLabelPerformance, //
                    (widget, data) -> new PortfolioTaxOrFeeRateWidget(widget, data, s -> {
                        double rate = s.getPortfolioTaxRate();
                        return Double.isNaN(rate) ? s.getValue(CategoryType.TAXES) : rate;
                    }, Messages.TooltipPortfolioTaxRate)),

    PORTFOLIO_FEE_RATE(Messages.LabelPortfolioFeeRate, Messages.ClientEditorLabelPerformance, //
                    (widget, data) -> new PortfolioTaxOrFeeRateWidget(widget, data, s -> {
                        double rate = s.getPortfolioFeeRate();
                        return Double.isNaN(rate) ? s.getValue(CategoryType.FEES) : rate;
                    }, Messages.TooltipPortfolioFeeRate)),

    CURRENT_DATE(Messages.LabelCurrentDate, Messages.LabelCommon, CurrentDateWidget::new),

    EXCHANGE_RATE(Messages.LabelExchangeRate, Messages.LabelCommon, ExchangeRateWidget::new),

    ACTIVITY_CHART(Messages.LabelTradingActivityChart, Messages.LabelCommon, Images.VIEW_BARCHART, ActivityWidget::new),

    LIMIT_EXCEEDED(Messages.SecurityListFilterLimitPriceExceeded, Messages.LabelCommon, LimitExceededWidget::new),

    FOLLOW_UP(Messages.SecurityListFilterDateReached, Messages.LabelCommon, FollowUpWidget::new),

    EVENT_LIST(Messages.EventListWidgetTitle, Messages.LabelCommon, EventListWidget::new),

    LATEST_SECURITY_PRICE(Messages.LabelSecurityLatestPrice, Messages.LabelCommon, //
                    (widget, data) -> IndicatorWidget.<Long>create(widget, data) //
                                    .with(Values.Quote) //
                                    .with((ds, period) -> {
                                        if (!(ds.getInstance() instanceof Security))
                                            return 0L;

                                        Security security = (Security) ds.getInstance();
                                        return security.getSecurityPrice(LocalDate.now()).getValue();
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .with(ds -> ds.getInstance() instanceof Security) //
                                    .withColoredValues(false) //
                                    .withTooltip((ds, period) -> {
                                        if (!(ds.getInstance() instanceof Security))
                                            return ""; //$NON-NLS-1$

                                        Security security = (Security) ds.getInstance();

                                        return MessageFormat.format(Messages.TooltipSecurityLatestPrice,
                                                        security.getName(), Values.Date.format(security
                                                                        .getSecurityPrice(LocalDate.now()).getDate()));
                                    })//
                                    .build()),

    DISTANCE_TO_ATH(Messages.SecurityListFilterDistanceFromAth, Messages.LabelCommon, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> {
                                        if (!(ds.getInstance() instanceof Security))
                                            return (double) 0;

                                        Security security = (Security) ds.getInstance();

                                        Double distance = new AllTimeHigh(security, period).getDistance();
                                        if (distance == null)
                                            return (double) 0;

                                        return distance;
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .with(ds -> ds.getInstance() instanceof Security) //
                                    .withColoredValues(false) //
                                    .withTooltip((ds, period) -> {
                                        if (!(ds.getInstance() instanceof Security))
                                            return null;

                                        Security security = (Security) ds.getInstance();
                                        AllTimeHigh ath = new AllTimeHigh(security, period);
                                        if (ath.getValue() == null)
                                            return null;

                                        return MessageFormat.format(Messages.TooltipAllTimeHigh, period.getDays(),
                                                        Values.Date.format(ath.getDate()),
                                                        ath.getValue() / Values.Quote.divider(),
                                                        security.getSecurityPrice(LocalDate.now()).getValue()
                                                                        / Values.Quote.divider(),
                                                        Values.Date.format(security.getSecurityPrice(LocalDate.now())
                                                                        .getDate()));
                                    })//
                                    .build()),

    WEBSITE(Messages.Website, Messages.LabelCommon, BrowserWidget::new),

    REBALANCING_TARGET_CHART(
                    MessageFormat.format(Messages.LabelColonSeparated, Messages.LabelTaxonomies,
                                    Messages.ColumnTargetValue),
                    Messages.LabelStatementOfAssets, Images.VIEW_PIECHART, RebalancingTargetChartWidget::new),

    REBALANCING_CHART(Messages.RebalancingChartActualVsTarget, Messages.LabelStatementOfAssets, Images.VIEW_BARCHART,
                    RebalancingChartWidget::new),

    // typo is API now!!
    VERTICAL_SPACEER(Messages.LabelVerticalSpacer, Messages.LabelCommon, VerticalSpacerWidget::new),

    ALL_TIME_HIGH(Messages.LabelAllTimeHigh, Messages.LabelStatementOfAssets, //
                    (widget, data) -> IndicatorWidget.<Money>create(widget, data) //
                                    .with(Values.Money) //
                                    .with((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        long[] totals = index.getTotals();
                                        long maxValue = 0;
                                        for (long value : totals)
                                        {
                                            maxValue = Math.max(maxValue, value);
                                        }
                                        return Money.of(index.getCurrency(), maxValue);
                                    }) //
                                    .withBenchmarkDataSeries(false) //
                                    .withTooltip((ds, period) -> {
                                        PerformanceIndex index = data.calculate(ds, period);
                                        long[] totals = index.getTotals();
                                        long maxValue = 0;
                                        int maxIndex = 0;

                                        for (int i = 0; i < totals.length; i++)
                                        {
                                            if (totals[i] > maxValue)
                                            {
                                                maxValue = totals[i];
                                                maxIndex = i;
                                            }
                                        }

                                        DateTimeFormatter formatter = DateTimeFormatter
                                                        .ofLocalizedDate(FormatStyle.LONG)
                                                        .withZone(ZoneId.systemDefault());

                                        LocalDate date = period.getStart().plusDays(maxIndex);
                                        return MessageFormat.format(Messages.TooltipAllTimeHighWidget,
                                                        formatter.format(date));
                                    })//
                                    .build()),

    // Portfolio Analytics widgets used by the dedicated optimization view.
    PORTFOLIO_ANALYTICS_EXPECTED_RETURN_ANNUALIZED(Messages.LabelExpectedReturnAnnualized, Messages.LabelOptimization, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.AnnualizedPercent2) //
                                    .with((ds, period) -> withPortfolioAnalytics(data, ds, period,
                                                    input -> input.analytics.getPortfolioExpectedReturnAnnualized(input.weights))) // ⬅️ Delegación pura y limpia
                                    .withColoredValues(true) //
                                    .withBenchmarkDataSeries(false) //
                                    .with(WidgetFactory::isPortfolioAnalyticsSupportedDataSeries) //
                                    .build()),

    PORTFOLIO_ANALYTICS_VOLATILITY_ANNUALIZED(Messages.LabelVolatilityAnnualized, Messages.LabelOptimization, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> withPortfolioAnalytics(data, ds, period,
                                                    input -> input.analytics.getPortfolioStandardDeviationAnnualized(
                                                                    input.weights)))//
                                    .withColoredValues(false) //
                                    .withBenchmarkDataSeries(false) //
                                    .with(WidgetFactory::isPortfolioAnalyticsSupportedDataSeries) //
                                    .build()),

    PORTFOLIO_ANALYTICS_SHARPE_ANNUALIZED(Messages.LabelSharpeRatioAnnualized, Messages.LabelOptimization, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.PercentPlain) //
                                    .with((ds, period) -> withPortfolioAnalytics(data, ds, period, input -> {
                                        double riskFreeRate = new ClientProperties(data.getClient())
                                                        .getRiskFreeRateOfReturn();
                                        return input.analytics.getPortfolioSharpeRatioAnnualized(input.weights, riskFreeRate);
                                    })) //
                                    .withColoredValues(true) //
                                    .withBenchmarkDataSeries(false) //
                                    .with(WidgetFactory::isPortfolioAnalyticsSupportedDataSeries) //
                                    .build()),

    PORTFOLIO_ANALYTICS_VALUE_AT_RISK_ANNUALIZED(Messages.LabelValueAtRiskAnnualized, Messages.LabelOptimization, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.Percent2) //
                                    .with((ds, period) -> withPortfolioAnalytics(data, ds, period,
                                                    input -> input.analytics.getParametricVaRAnnualized(input.weights, 1.645))) //
                                    .withColoredValues(false) //
                                    .withBenchmarkDataSeries(false) //
                                    .with(WidgetFactory::isPortfolioAnalyticsSupportedDataSeries) //
                                    .build()),

    PORTFOLIO_ANALYTICS_DIVERSIFICATION(Messages.LabelDiversificationRatio, Messages.LabelOptimization, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.PercentPlain) //
                                    .with((ds, period) -> withPortfolioAnalytics(data, ds, period,
                                                    input -> input.analytics.getDiversificationRatio(input.weights))) //
                                    .withColoredValues(false) //
                                    .withBenchmarkDataSeries(false) //
                                    .with(WidgetFactory::isPortfolioAnalyticsSupportedDataSeries) //
                                    .build()),

    PORTFOLIO_ANALYTICS_CONCENTRATION(Messages.LabelConcentrationIndex, Messages.LabelOptimization, //
                    (widget, data) -> IndicatorWidget.<Double>create(widget, data) //
                                    .with(Values.PercentPlain) //
                                    .with((ds, period) -> withPortfolioAnalytics(data, ds, period,
                                                    input -> input.analytics.getConcentrationIndex(input.weights))) //
                                    .withColoredValues(false) //
                                    .withBenchmarkDataSeries(false) //
                                    .with(WidgetFactory::isPortfolioAnalyticsSupportedDataSeries) //
                                    .build()),

    PORTFOLIO_ANALYTICS_RISK_CONTRIBUTION_CHART(Messages.LabelRiskContributionChart, Messages.LabelOptimization,
                    Images.VIEW_DONUT,
                    config -> config.put(Dashboard.Config.HEIGHT.name(), "560"), 
                    PortfolioRiskContributionChartWidget::new);

    private String label;
    private String group;
    private Images image;
    private Consumer<Map<String, String>> defaultConfigFunction;
    private BiFunction<Dashboard.Widget, DashboardData, WidgetDelegate<?>> createFunction;

    private WidgetFactory(String label, String group, Images image, Consumer<Map<String, String>> defaultConfigFunction,
                    BiFunction<Dashboard.Widget, DashboardData, WidgetDelegate<?>> createFunction)
    {
        this.label = label;
        this.group = group;
        this.image = image;
        this.defaultConfigFunction = defaultConfigFunction;
        this.createFunction = createFunction;
    }

    private WidgetFactory(String label, String group, Consumer<Map<String, String>> defaultConfigFunction,
                    BiFunction<Dashboard.Widget, DashboardData, WidgetDelegate<?>> createFunction)
    {
        this(label, group, null, defaultConfigFunction, createFunction);
    }

    private WidgetFactory(String label, String group, Images image,
                    BiFunction<Dashboard.Widget, DashboardData, WidgetDelegate<?>> createFunction)
    {
        this(label, group, image, null, createFunction);
    }

    private WidgetFactory(String label, String group,
                    BiFunction<Dashboard.Widget, DashboardData, WidgetDelegate<?>> createFunction)
    {
        this(label, group, null, null, createFunction);
    }

    public String getLabel()
    {
        return label;
    }

    public String getGroup()
    {
        return group;
    }

    public Images getImage()
    {
        return image;
    }

    public WidgetDelegate<?> constructDelegate(Dashboard.Widget widget, DashboardData data)
    {
        return this.createFunction.apply(widget, data);
    }

    public Widget constructWidget()
    {
        Dashboard.Widget widget = new Dashboard.Widget();
        widget.setLabel(label);
        widget.setType(name());

        if (defaultConfigFunction != null)
            defaultConfigFunction.accept(widget.getConfiguration());

        return widget;
    }

    public boolean isOptimizationWidget()
    {
        return switch (this)
        {
            case PORTFOLIO_ANALYTICS_EXPECTED_RETURN_ANNUALIZED,
                            PORTFOLIO_ANALYTICS_VOLATILITY_ANNUALIZED,
                            PORTFOLIO_ANALYTICS_SHARPE_ANNUALIZED,
                            PORTFOLIO_ANALYTICS_VALUE_AT_RISK_ANNUALIZED,
                            PORTFOLIO_ANALYTICS_DIVERSIFICATION,
                            PORTFOLIO_ANALYTICS_CONCENTRATION,
                            PORTFOLIO_ANALYTICS_RISK_CONTRIBUTION_CHART -> true;
            default -> false;
        };
    }

    private record PortfolioAnalyticsInput(PortfolioAnalytics analytics, double[] weights)
    {
    }

    private static boolean isPortfolioAnalyticsSupportedDataSeries(DataSeries series)
    {
        return switch (series.getType())
        {
            case CLIENT, CLIENT_PRETAX, SECURITY, PORTFOLIO, PORTFOLIO_PRETAX, PORTFOLIO_PLUS_ACCOUNT,
                            PORTFOLIO_PLUS_ACCOUNT_PRETAX -> true;
            default -> false;
        };
    }

    private static double withPortfolioAnalytics(DashboardData data, DataSeries dataSeries, name.abuchen.portfolio.util.Interval period,
                    java.util.function.Function<PortfolioAnalyticsInput, Double> metric)
    {
        PortfolioAnalyticsInput input = buildPortfolioAnalyticsInput(data, dataSeries, period);
        if (input == null)
            return Double.NaN;

        return metric.apply(input);
    }

    private static PortfolioAnalyticsInput buildPortfolioAnalyticsInput(DashboardData data, DataSeries dataSeries,
                    name.abuchen.portfolio.util.Interval period)
    {
        List<Security> candidates = collectCandidateSecurities(data, dataSeries);
        if (candidates.isEmpty())
            return null;

        List<PerformanceIndex> indices = new ArrayList<>();
        List<Long> marketValues = new ArrayList<>();

        for (Security security : candidates)
        {
            PerformanceIndex index = name.abuchen.portfolio.snapshot.PerformanceIndex.forInvestment(data.getClient(),
                            data.getCurrencyConverter(), security, period, new ArrayList<>());

            long[] totals = index.getTotals();
            double[] deltas = index.getDeltaPercentage();

            if (totals == null || totals.length == 0 || deltas == null || deltas.length < 2)
                continue;

            long marketValue = totals[totals.length - 1];
            if (marketValue <= 0)
                continue;

            indices.add(index);
            marketValues.add(marketValue);
        }

        if (indices.size() < 2)
            return null;

        double total = marketValues.stream().mapToDouble(Long::doubleValue).sum();
        if (total <= 0)
            return null;

        double[] weights = new double[marketValues.size()];
        for (int index = 0; index < marketValues.size(); index++)
            weights[index] = marketValues.get(index) / total;

        Covariance covariance = new Covariance(indices);
        covariance.calculate();

        return new PortfolioAnalyticsInput(new PortfolioAnalytics(covariance, indices), weights);
    }

    private static List<Security> collectCandidateSecurities(DashboardData data, DataSeries dataSeries)
    {
        Set<Security> securities = new HashSet<>();

        if (dataSeries.getType() == DataSeries.Type.SECURITY)
        {
            securities.add((Security) dataSeries.getInstance());
        }
        else if (dataSeries.getType() == DataSeries.Type.PORTFOLIO
                        || dataSeries.getType() == DataSeries.Type.PORTFOLIO_PRETAX
                        || dataSeries.getType() == DataSeries.Type.PORTFOLIO_PLUS_ACCOUNT
                        || dataSeries.getType() == DataSeries.Type.PORTFOLIO_PLUS_ACCOUNT_PRETAX)
        {
            Portfolio portfolio = (Portfolio) dataSeries.getInstance();
            portfolio.getTransactions().stream().map(t -> t.getSecurity()).filter(java.util.Objects::nonNull)
                            .forEach(securities::add);
        }
        else
        {
            securities.addAll(data.getClient().getSecurities());
        }

        return new ArrayList<>(securities);
    }
}
