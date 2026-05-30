package name.abuchen.portfolio.math;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class AdvancedRiskMetricsTest
{
    private static final double TOLERANCE = 1e-8;

    private PerformanceIndex index;

    @Before
    public void setUp()
    {
        index = mock(PerformanceIndex.class);
    }

    @Test
    public void testSharpeRatioAnnualized()
    {
        double[] delta = { 0.01, -0.005, 0.02, -0.01 };
        when(index.getDeltaPercentage()).thenReturn(delta);
        when(index.getPerformanceIRR()).thenReturn(0.10);

        double expected = (0.10 - 0.02)
                        / (sampleStandardDeviation(delta) * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR));
        double sharpe = AdvancedRiskMetrics.annualizedSharpeRatio(index, 0.02);

        assertThat(sharpe, closeTo(expected, TOLERANCE));
    }

    @Test
    public void testSharpeWithZeroVolatility()
    {
        when(index.getDeltaPercentage()).thenReturn(new double[] { 0.01, 0.01 });

        assertThat(Double.isNaN(AdvancedRiskMetrics.annualizedSharpeRatio(index, 0.02)), is(true));
    }

    @Test
    public void testSortinoRatioAnnualized()
    {
        double[] delta = { 0.05, -0.02, 0.03, -0.04 };
        when(index.getPerformanceIRR()).thenReturn(0.12);
        when(index.getDeltaPercentage()).thenReturn(delta);

        double dailyRf = Math.pow(1.0 + 0.02, 1.0 / FinancialConstants.US_TRADING_DAYS_PER_YEAR) - 1.0;

        double target = dailyRf;
        double sum = 0.0;
        int count = 0;
        for (double r : delta)
        {
            if (r < target)
            {
                double diff = r - target;
                sum += diff * diff;
            }
            count++;
        }
        double sampleDownsideRiskDaily = Math.sqrt(sum / count);

        double expected = (0.12 - 0.02)
                        / (sampleDownsideRiskDaily * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR));

        double sortino = AdvancedRiskMetrics.annualizedSortinoRatio(index, 0.02);
        assertThat(sortino, closeTo(expected, TOLERANCE));
    }

    @Test
    public void testCalmarRatioAnnualized()
    {
        double[] accumulated = { 0.0, 0.10, -0.01 };
        when(index.getAccumulatedPercentage()).thenReturn(accumulated);
        when(index.getDates()).thenReturn(new java.time.LocalDate[] {
                        java.time.LocalDate.of(2026, 1, 1),
                        java.time.LocalDate.of(2026, 1, 2),
                        java.time.LocalDate.of(2026, 1, 3) });
        when(index.getPerformanceIRR()).thenReturn(0.05);

        double calmar = AdvancedRiskMetrics.annualizedCalmarRatio(index);
        assertThat(calmar, closeTo(0.5, TOLERANCE));
    }

    @Test
    public void testCalmarWithZeroDrawdown()
    {
        double[] accumulated = { 0.0, 0.10, 0.20 };
        when(index.getAccumulatedPercentage()).thenReturn(accumulated);
        when(index.getDates()).thenReturn(new java.time.LocalDate[] {
                        java.time.LocalDate.of(2026, 1, 1),
                        java.time.LocalDate.of(2026, 1, 2),
                        java.time.LocalDate.of(2026, 1, 3) });

        assertThat(Double.isNaN(AdvancedRiskMetrics.annualizedCalmarRatio(index)), is(true));
    }

    @Test
    public void testAnnualizedValueAtRisk()
    {
        double[] delta = { 0.04, -0.01, 0.02, -0.03, 0, -0.05, 0.01, -0.02, 0.03, -0.04 };
        when(index.getDeltaPercentage()).thenReturn(delta);

        double expected = 0.04 * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR);
        assertThat(AdvancedRiskMetrics.annualizedValueAtRisk(index, 0.90), closeTo(expected, TOLERANCE));
    }

    @Test
    public void testAnnualizedValueAtRiskHighConfidence()
    {
        double[] delta = new double[20];
        for (int i = 0; i < 20; i++)
            delta[i] = i * 0.01 - 0.10;
        when(index.getDeltaPercentage()).thenReturn(delta);

        double var95 = AdvancedRiskMetrics.annualizedValueAtRisk(index, 0.95);
        double expectedDailyVaR = 0.09;
        assertThat(var95,
                        closeTo(expectedDailyVaR * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR), TOLERANCE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAnnualizedValueAtRiskWithInvalidConfidence()
    {
        AdvancedRiskMetrics.annualizedValueAtRisk(index, 1.05);
    }

    @Test
    public void testAnnualizedExpectedReturn()
    {
        double[] delta = { 0.01, 0.02, -0.03 };
        when(index.getDeltaPercentage()).thenReturn(delta);

        double er = AdvancedRiskMetrics.annualizedExpectedReturn(index);
        assertThat(er, closeTo(0.0, TOLERANCE));
    }

    @Test
    public void testAnnualizedExpectedReturnWithNull()
    {
        assertThat(Double.isNaN(AdvancedRiskMetrics.annualizedExpectedReturn(null)), is(true));
    }

    @Test
    public void testSkewness()
    {
        double[] delta = { 0.00, 0.01, 0.02 };
        when(index.getDeltaPercentage()).thenReturn(delta);

        assertThat(AdvancedRiskMetrics.skewness(index), closeTo(0.0, TOLERANCE));
    }

    @Test
    public void testPositiveSkewness()
    {
        double[] delta = { -0.01, -0.01, -0.01, 0.05 };
        when(index.getDeltaPercentage()).thenReturn(delta);

        double skew = AdvancedRiskMetrics.skewness(index);
        assertThat(skew > 0, is(true));
    }

    @Test
    public void testExcessKurtosis()
    {
        double[] delta = { -0.02, -0.01, 0.01, 0.02 };
        when(index.getDeltaPercentage()).thenReturn(delta);

        double kurt = AdvancedRiskMetrics.excessKurtosis(index);
        assertThat(kurt < 0, is(true));
    }

    @Test
    public void testExcessKurtosisWithFatTails()
    {
        double[] nearNormal = { -0.02, -0.01, 0.01, 0.02, 0.00, 0.01 };
        when(index.getDeltaPercentage()).thenReturn(nearNormal);
        double baselineKurt = AdvancedRiskMetrics.excessKurtosis(index);

        double[] fatTail = { 0.01, 0.01, 0.01, 0.01, 0.01, 0.80 };
        when(index.getDeltaPercentage()).thenReturn(fatTail);

        double kurt = AdvancedRiskMetrics.excessKurtosis(index);
        assertThat(kurt > baselineKurt, is(true));
    }

    @Test
    public void testTrackingError()
    {
        PerformanceIndex portfolio = mock(PerformanceIndex.class);
        PerformanceIndex benchmark = mock(PerformanceIndex.class);

        double[] pDelta = { 0.01, 0.02, -0.01 };
        double[] bDelta = { 0.005, 0.015, 0.0 };

        when(portfolio.getDeltaPercentage()).thenReturn(pDelta);
        when(benchmark.getDeltaPercentage()).thenReturn(bDelta);

        double[] diff = { 0.005, 0.005, -0.01 };
        double expected = sampleStandardDeviation(diff) * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR);
        double te = AdvancedRiskMetrics.annualizedTrackingError(portfolio, benchmark);
        assertThat(te, closeTo(expected, TOLERANCE));
    }

    @Test
    public void testTrackingErrorWithMismatchedData()
    {
        PerformanceIndex p = mock(PerformanceIndex.class);
        PerformanceIndex b = mock(PerformanceIndex.class);

        when(p.getDeltaPercentage()).thenReturn(new double[] { 0.01, 0.02 });
        when(b.getDeltaPercentage()).thenReturn(new double[] { 0.01 });

        double te = AdvancedRiskMetrics.annualizedTrackingError(p, b);
        assertThat(Double.isNaN(te), is(true));
    }

    @Test
    public void testTrackingErrorAgainstSameAsset()
    {
        double[] delta = { 0.01, -0.01, 0.02, -0.02 };
        when(index.getDeltaPercentage()).thenReturn(delta);

        double te = AdvancedRiskMetrics.annualizedTrackingError(index, index);
        assertThat(te, closeTo(0.0, TOLERANCE));
    }

    @Test
    public void testNullInputsTrackingError()
    {
        assertThat(Double.isNaN(AdvancedRiskMetrics.annualizedTrackingError(null, null)), is(true));
    }

    @Test
    public void testInformationRatio()
    {
        PerformanceIndex portfolio = mock(PerformanceIndex.class);
        PerformanceIndex benchmark = mock(PerformanceIndex.class);

        when(portfolio.getPerformanceIRR()).thenReturn(0.10);
        when(benchmark.getPerformanceIRR()).thenReturn(0.06);

        when(portfolio.getDeltaPercentage()).thenReturn(new double[] { 0.02, 0.01 });
        when(benchmark.getDeltaPercentage()).thenReturn(new double[] { 0.01, 0.02 });

        double ir = AdvancedRiskMetrics.annualizedInformationRatio(portfolio, benchmark);
        assertThat(ir, closeTo(0.17817416127494956, TOLERANCE));
    }

    @Test
    public void testInformationRatioWithNegativeAlpha()
    {
        PerformanceIndex portfolio = mock(PerformanceIndex.class);
        PerformanceIndex benchmark = mock(PerformanceIndex.class);

        when(portfolio.getPerformanceIRR()).thenReturn(0.04);
        when(benchmark.getPerformanceIRR()).thenReturn(0.10);

        when(portfolio.getDeltaPercentage()).thenReturn(new double[] { 0.02, 0.01 });
        when(benchmark.getDeltaPercentage()).thenReturn(new double[] { 0.01, 0.02 });

        double ir = AdvancedRiskMetrics.annualizedInformationRatio(portfolio, benchmark);
        assertThat(ir, closeTo(-0.26726124191242434, TOLERANCE));
    }

    @Test
    public void testInformationRatioWithZeroTrackingError()
    {
        PerformanceIndex p = mock(PerformanceIndex.class);
        PerformanceIndex b = mock(PerformanceIndex.class);

        double[] identical = { 0.01, 0.01 };
        when(p.getDeltaPercentage()).thenReturn(identical);
        when(b.getDeltaPercentage()).thenReturn(identical);

        double ir = AdvancedRiskMetrics.annualizedInformationRatio(p, b);
        assertThat(Double.isNaN(ir), is(true));
    }

    @Test
    public void testInsufficientData()
    {
        when(index.getDeltaPercentage()).thenReturn(new double[] { 0.01 });
        assertThat(Double.isNaN(AdvancedRiskMetrics.annualizedValueAtRisk(index, 0.95)), is(true));
        assertThat(Double.isNaN(AdvancedRiskMetrics.skewness(index)), is(true));
    }

    private static double sampleStandardDeviation(double[] values)
    {
        double mean = 0.0;
        for (double value : values)
            mean += value;
        mean /= values.length;

        double squared = 0.0;
        for (double value : values)
        {
            double deviation = value - mean;
            squared += deviation * deviation;
        }

        return Math.sqrt(squared / (values.length - 1));
    }
}