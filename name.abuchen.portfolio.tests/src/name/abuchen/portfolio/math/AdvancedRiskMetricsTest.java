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
    public void testDownsideRisk()
    {
        double[] returns = { 0.05, -0.03, 0.02, -0.04 };
        assertThat(AdvancedRiskMetrics.downsideRisk(returns, 0.0), closeTo(0.025, TOLERANCE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDownsideRiskWithEmptyArray()
    {
        AdvancedRiskMetrics.downsideRisk(new double[] {}, 0.0);
    }

    @Test
    public void testSharpeRatio()
    {
        double[] delta = { 0.01, -0.005, 0.02, -0.01 };
        when(index.getDeltaPercentage()).thenReturn(delta);
        when(index.getPerformanceIRR()).thenReturn(0.10);

        double expected = (0.10 - 0.02)
                        / (sampleStandardDeviation(delta) * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR));
        double sharpe = AdvancedRiskMetrics.sharpeRatio(index, 0.02);

        assertThat(sharpe, closeTo(expected, TOLERANCE));
    }

    @Test
    public void testSharpeWithZeroVolatility()
    {
        when(index.getDeltaPercentage()).thenReturn(new double[] { 0.01, 0.01 });

        assertThat(Double.isNaN(AdvancedRiskMetrics.sharpeRatio(index, 0.02)), is(true));
    }

    @Test
    public void testSortinoRatio()
    {
        double[] delta = { 0.05, -0.02, 0.03, -0.04 };
        when(index.getPerformanceIRR()).thenReturn(0.12);
        when(index.getDeltaPercentage()).thenReturn(delta);

        double dailyRf = Math.pow(1.0 + 0.02, 1.0 / FinancialConstants.US_TRADING_DAYS_PER_YEAR) - 1.0;
        double downsideRisk = AdvancedRiskMetrics.downsideRisk(delta, dailyRf);
        double expected = (0.12 - 0.02) / (downsideRisk * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR));

        double sortino = AdvancedRiskMetrics.sortinoRatio(index, 0.02);
        assertThat(sortino, closeTo(expected, TOLERANCE));
    }

    @Test
    public void testCalmarRatio()
    {
        double[] accumulated = { 0.0, 0.10, -0.01 };
        when(index.getAccumulatedPercentage()).thenReturn(accumulated);
        when(index.getDates()).thenReturn(new java.time.LocalDate[] {
                        java.time.LocalDate.of(2026, 1, 1),
                        java.time.LocalDate.of(2026, 1, 2),
                        java.time.LocalDate.of(2026, 1, 3) });
        when(index.getPerformanceIRR()).thenReturn(0.05);

        double calmar = AdvancedRiskMetrics.calmarRatio(index);
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

        assertThat(Double.isNaN(AdvancedRiskMetrics.calmarRatio(index)), is(true));
    }

    @Test
    public void testValueAtRisk()
    {
        double[] delta = { 0.04, -0.01, 0.02, -0.03, 0, -0.05, 0.01, -0.02, 0.03, -0.04 };
        when(index.getDeltaPercentage()).thenReturn(delta);

        assertThat(AdvancedRiskMetrics.valueAtRisk(index, 0.90), closeTo(0.04, TOLERANCE));
    }

    @Test
    public void testValueAtRiskHighConfidence()
    {
        double[] delta = new double[20];
        for (int i = 0; i < 20; i++)
            delta[i] = i * 0.01 - 0.10;
        when(index.getDeltaPercentage()).thenReturn(delta);

        double var95 = AdvancedRiskMetrics.valueAtRisk(index, 0.95);
        assertThat(var95, closeTo(0.09, TOLERANCE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValueAtRiskWithInvalidConfidence()
    {
        // Confidence > 1.0 should throw exception
        AdvancedRiskMetrics.valueAtRisk(index, 1.05);
    }

    @Test
    public void testExpectedReturn()
    {
        double[] delta = { 0.01, 0.02, -0.03 };
        when(index.getDeltaPercentage()).thenReturn(delta);

        double er = AdvancedRiskMetrics.expectedReturn(index);
        assertThat(er, closeTo(0.0, TOLERANCE));
    }

    @Test
    public void testExpectedReturnWithNull()
    {
        assertThat(Double.isNaN(AdvancedRiskMetrics.expectedReturn(null)), is(true));
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
        double expected = sampleStandardDeviation(diff);
        double te = AdvancedRiskMetrics.trackingError(portfolio, benchmark);
        assertThat(te, closeTo(expected, TOLERANCE));
    }

    @Test
    public void testTrackingErrorWithMismatchedData()
    {
        PerformanceIndex p = mock(PerformanceIndex.class);
        PerformanceIndex b = mock(PerformanceIndex.class);

        when(p.getDeltaPercentage()).thenReturn(new double[] { 0.01, 0.02 });
        when(b.getDeltaPercentage()).thenReturn(new double[] { 0.01 });

        double te = AdvancedRiskMetrics.trackingError(p, b);
        assertThat(Double.isNaN(te), is(true));
    }

    @Test
    public void testTrackingErrorAgainstSameAsset()
    {
        double[] delta = { 0.01, -0.01, 0.02, -0.02 };
        when(index.getDeltaPercentage()).thenReturn(delta);

        // TE against self must be 0
        double te = AdvancedRiskMetrics.trackingError(index, index);
        assertThat(te, closeTo(0.0, TOLERANCE));
    }

    @Test
    public void testNullInputsTrackingError()
    {
        assertThat(Double.isNaN(AdvancedRiskMetrics.trackingError(null, null)), is(true));
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

        double ir = AdvancedRiskMetrics.informationRatio(portfolio, benchmark);
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

        double ir = AdvancedRiskMetrics.informationRatio(portfolio, benchmark);
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

        double ir = AdvancedRiskMetrics.informationRatio(p, b);
        assertThat(Double.isNaN(ir), is(true));
    }

    @Test
    public void testInsufficientData()
    {
        when(index.getDeltaPercentage()).thenReturn(new double[] { 0.01 });
        assertThat(Double.isNaN(AdvancedRiskMetrics.valueAtRisk(index, 0.95)), is(true));

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
