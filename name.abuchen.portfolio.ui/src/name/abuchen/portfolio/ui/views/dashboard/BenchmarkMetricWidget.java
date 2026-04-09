package name.abuchen.portfolio.ui.views.dashboard;

import java.time.LocalDate;
import java.util.function.Supplier;

import name.abuchen.portfolio.math.AdvancedRiskMetrics;
import name.abuchen.portfolio.model.Dashboard.Widget;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.util.Interval;

public class BenchmarkMetricWidget extends AbstractIndicatorWidget<Double>
{
    public enum MetricType
    {
        TRACKING_ERROR_DAILY,
        TRACKING_ERROR_ANNUALIZED,
        INFORMATION_RATIO
    }

    private final MetricType metricType;

    public BenchmarkMetricWidget(Widget widget, DashboardData dashboardData, MetricType metricType)
    {
        super(widget, dashboardData, false, null);

        this.metricType = metricType;
        addConfigAfter(DataSeriesConfig.class, new BenchmarkDataSeriesConfig(this));
    }

    @Override
    public Supplier<Double> getUpdateTask()
    {
        return () -> {
            Interval interval = get(ReportingPeriodConfig.class).getReportingPeriod().toInterval(LocalDate.now());

            PerformanceIndex portfolio = getDashboardData().calculate(get(DataSeriesConfig.class).getDataSeries(),
                            interval);

            var benchmarkSeries = get(BenchmarkDataSeriesConfig.class).getDataSeries();
            if (benchmarkSeries == null)
                return Double.NaN;

            PerformanceIndex benchmark = getDashboardData().calculate(benchmarkSeries, interval);

            return switch (metricType)
            {
                case TRACKING_ERROR_DAILY -> AdvancedRiskMetrics.trackingError(portfolio, benchmark);
                case TRACKING_ERROR_ANNUALIZED -> AdvancedRiskMetrics.annualizedTrackingError(portfolio, benchmark);
                case INFORMATION_RATIO -> AdvancedRiskMetrics.informationRatio(portfolio, benchmark);
            };
        };
    }

    @Override
    public void update(Double value)
    {
        super.update(value);

        if (value == null || Double.isNaN(value))
        {
            indicator.setText(Messages.LabelNotAvailable);
            return;
        }

        if (metricType == MetricType.INFORMATION_RATIO)
            indicator.setText(Values.PercentPlain.format(value));
        else
            indicator.setText(Values.Percent2.format(value));
    }
}
