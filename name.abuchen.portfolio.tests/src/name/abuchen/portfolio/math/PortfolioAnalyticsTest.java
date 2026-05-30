package name.abuchen.portfolio.math;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import name.abuchen.portfolio.math.Risk.Volatility;
import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class PortfolioAnalyticsTest
{
    private static final double TOLERANCE = 0.1e-10;

    private PerformanceIndex index(double[] deltas)
    {
        Volatility volatility = new Volatility(deltas, i -> true);
        return index(deltas, volatility.getStandardDeviation());
    }

    private PerformanceIndex index(double[] deltas, double standardDeviation)
    {
        PerformanceIndex index = mock(PerformanceIndex.class);
        Volatility volatility = mock(Volatility.class);

        when(index.getDeltaPercentage()).thenReturn(deltas);
        when(index.getVolatility()).thenReturn(volatility);
        when(volatility.getStandardDeviation()).thenReturn(standardDeviation);

        return index;
    }

    @Test
    public void testPortfolioStandardDeviationUsesSquareRootOfVariance()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03, 0.04 }),
                        index(new double[] { 0.03, 0.02, 0.05, 0.04 }));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);
        double[] weights = new double[] { 0.5, 0.5 };

        double variance = covariance.getPortfolioVariance(weights);
        assertThat(analytics.getPortfolioStandardDeviationAnnualized(weights), closeTo(Math.sqrt(variance), TOLERANCE));
    }

    @Test
    public void testPortfolioStandardDeviationReturnsNaNForInvalidWeights()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03 }),
                        index(new double[] { 0.03, 0.02, 0.01 }));

        PortfolioAnalytics analytics = new PortfolioAnalytics(new Covariance(assets), assets);

        assertThat(Double.isNaN(analytics.getPortfolioStandardDeviationAnnualized(new double[] { 1.0 })), is(true));
    }

    @Test
    public void testDiversificationRatioMatchesFormula()
    {
        double[] deltasFirst = new double[] { 0.01, -0.01, 0.03, -0.02 };
        double[] deltasSecond = new double[] { 0.02, -0.005, 0.015, -0.01 };

        PerformanceIndex first = index(deltasFirst);
        PerformanceIndex second = index(deltasSecond);
        List<PerformanceIndex> assets = Arrays.asList(first, second);

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);
        double[] weights = new double[] { 0.5, 0.5 };

        double sigma = analytics.getPortfolioStandardDeviationAnnualized(weights);
        double weightedSum = (weights[0] * sampleStandardDeviation(deltasFirst)
                        * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR))
                        + (weights[1] * sampleStandardDeviation(deltasSecond)
                                        * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR));

        assertThat(analytics.getDiversificationRatio(weights), closeTo(weightedSum / sigma, TOLERANCE));
    }

    @Test
    public void testDiversificationRatioReturnsNaNWhenPortfolioVolatilityIsZero()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.01, 0.01 }, 0.0),
                        index(new double[] { 0.01, 0.01, 0.01 }, 0.0));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);

        assertThat(Double.isNaN(analytics.getDiversificationRatio(new double[] { 0.5, 0.5 })), is(true));
    }

    @Test
    public void testDiversificationRatioReturnsNaNForInvalidWeightsLength()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03 }),
                        index(new double[] { 0.03, 0.02, 0.01 }));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);

        assertThat(Double.isNaN(analytics.getDiversificationRatio(new double[] { 1.0 })), is(true));
    }

    @Test
    public void testDiversificationRatioReturnsNaNForNullWeights()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03 }),
                        index(new double[] { 0.03, 0.02, 0.01 }));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);

        assertThat(Double.isNaN(analytics.getDiversificationRatio(null)), is(true));
    }

    @Test
    public void testRiskContributionsSumToPortfolioVolatility()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03, 0.04 }),
                        index(new double[] { 0.03, 0.02, 0.05, 0.04 }));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);
        double[] weights = new double[] { 0.6, 0.4 };

        double[] contributions = analytics.getRiskContributions(weights);
        double sum = 0.0;
        for (double contribution : contributions)
        {
            sum += contribution;
        }

        assertThat(contributions.length, is(2));
        assertThat(sum, closeTo(analytics.getPortfolioStandardDeviationAnnualized(weights), TOLERANCE));
    }

    @Test
    public void testRiskContributionsMatchFormula()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03, 0.04 }),
                        index(new double[] { 0.03, 0.02, 0.05, 0.04 }));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);
        double[] weights = new double[] { 0.5, 0.5 };

        double sigmaP = analytics.getPortfolioStandardDeviationAnnualized(weights);
        double sigma00 = covariance.getCovarianceEntry(0, 0);
        double sigma01 = covariance.getCovarianceEntry(0, 1);
        double sigma10 = covariance.getCovarianceEntry(1, 0);
        double sigma11 = covariance.getCovarianceEntry(1, 1);

        double expectedFirst = (weights[0] * ((sigma00 * weights[0]) + (sigma01 * weights[1]))) / sigmaP;
        double expectedSecond = (weights[1] * ((sigma10 * weights[0]) + (sigma11 * weights[1]))) / sigmaP;

        double[] contributions = analytics.getRiskContributions(weights);

        assertThat(contributions[0], closeTo(expectedFirst, TOLERANCE));
        assertThat(contributions[1], closeTo(expectedSecond, TOLERANCE));
    }

    @Test
    public void testRiskContributionsReturnEmptyArrayForInvalidWeights()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03 }),
                        index(new double[] { 0.03, 0.02, 0.01 }));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);

        assertThat(analytics.getRiskContributions(new double[] { 1.0 }).length, is(0));
    }

    @Test
    public void testRiskContributionsReturnZeroesWhenPortfolioVolatilityIsZero()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.01, 0.01 }, 0.0),
                        index(new double[] { 0.01, 0.01, 0.01 }, 0.0));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);
        double[] contributions = analytics.getRiskContributions(new double[] { 0.5, 0.5 });

        assertThat(contributions.length, is(2));
        assertThat(contributions[0], is(0.0));
        assertThat(contributions[1], is(0.0));
    }

    @Test
    public void testRiskContributionsPercentageReturnZeroesWhenPortfolioVolatilityIsZero()
    {
        List<PerformanceIndex> assets = Arrays.asList(index(new double[] { 0.01, 0.01, 0.01 }, 0.0),
                        index(new double[] { 0.01, 0.01, 0.01 }, 0.0));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);
        double[] percentages = analytics.getRiskContributionsPercentage(new double[] { 0.5, 0.5 });

        assertThat(percentages.length, is(2));
        assertThat(percentages[0], is(0.0));
        assertThat(percentages[1], is(0.0));
    }

    @Test
    public void testConcentrationIndexMatchesHHIFormula()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03 }),
                        index(new double[] { 0.03, 0.02, 0.01 }));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);
        double[] weights = new double[] { 0.6, 0.4 };

        double expected = (0.6 * 0.6) + (0.4 * 0.4);
        assertThat(analytics.getConcentrationIndex(weights), closeTo(expected, TOLERANCE));
    }

    @Test
    public void testConcentrationIndexForEqualWeightsIsOneOverN()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03 }),
                        index(new double[] { 0.03, 0.02, 0.01 }));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);

        assertThat(analytics.getConcentrationIndex(new double[] { 0.5, 0.5 }), closeTo(0.5, TOLERANCE));
    }

    @Test
    public void testConcentrationIndexReturnsNaNForInvalidWeightsLength()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03 }),
                        index(new double[] { 0.03, 0.02, 0.01 }));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);

        assertThat(Double.isNaN(analytics.getConcentrationIndex(new double[] { 1.0 })), is(true));
    }

    @Test
    public void testConcentrationIndexReturnsNaNForNullWeights()
    {
        List<PerformanceIndex> assets = Arrays.asList(
                        index(new double[] { 0.01, 0.02, 0.03 }),
                        index(new double[] { 0.03, 0.02, 0.01 }));

        Covariance covariance = new Covariance(assets);
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);

        assertThat(Double.isNaN(analytics.getConcentrationIndex(null)), is(true));
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