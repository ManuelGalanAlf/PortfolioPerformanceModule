package name.abuchen.portfolio.rebalance;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class RebalancingMetricsCalculatorTest
{
    private RebalancingContext context;
    private List<PerformanceIndex> mockAssets;
    private double[] currentWeights;
    private double[] finalWeights;
    private double riskFreeRate;

    @Before
    public void setUp()
    {
        riskFreeRate = 0.02;
        mockAssets = new ArrayList<>();

        // Create mock assets using Mockito to avoid undefined constructor
        // errors
        PerformanceIndex asset1 = mock(PerformanceIndex.class);
        when(asset1.getDeltaPercentage()).thenReturn(new double[] { 0.01, -0.005, 0.02 });

        PerformanceIndex asset2 = mock(PerformanceIndex.class);
        when(asset2.getDeltaPercentage()).thenReturn(new double[] { -0.01, 0.015, -0.005 });

        mockAssets.add(asset1);
        mockAssets.add(asset2);

        currentWeights = new double[] { 0.60, 0.40 };
        finalWeights = new double[] { 0.50, 0.50 };

        // Initialize a standard testing context stub
        context = new RebalancingContext(null, null)
        {
            @Override
            public List<PerformanceIndex> getAssets()
            {
                return mockAssets;
            }

            @Override
            public double[] getCurrentWeights()
            {
                return currentWeights;
            }

            @Override
            public double[] getFinalWeights()
            {
                return finalWeights;
            }
        };
    }

    @Test
    public void testCalculateWithNullPortfolioIndexAndValidAssets()
    {
        // Act
        RebalancingMetrics metrics = RebalancingMetricsCalculator.calculate(context, null, riskFreeRate);

        // Assert - Historic metrics must be NaN because portfolioIndex is null
        assertTrue(Double.isNaN(metrics.getHistoricalSharpe()));
        assertTrue(Double.isNaN(metrics.getHistoricalVolatility()));
        assertTrue(Double.isNaN(metrics.getHistoricalVaR()));

        // Assert - Unchanged and Changed (Rebalanced) metrics should evaluate
        // to numbers
        assertTrue(!Double.isNaN(metrics.getUnchangedSharpe()));
        assertTrue(!Double.isNaN(metrics.getUnchangedVolatility()));
        assertTrue(!Double.isNaN(metrics.getUnchangedVaR()));

        assertTrue(!Double.isNaN(metrics.getRebalancedSharpe()));
        assertTrue(!Double.isNaN(metrics.getRebalancedVolatility()));
        assertTrue(!Double.isNaN(metrics.getRebalancedVaR()));
    }

    @Test
    public void testCalculateWithEmptyAssets()
    {
        // Arrange - Context returns empty asset list
        RebalancingContext emptyContext = new RebalancingContext(null, null)
        {
            @Override
            public List<PerformanceIndex> getAssets()
            {
                return new ArrayList<>();
            }

            @Override
            public double[] getCurrentWeights()
            {
                return new double[0];
            }

            @Override
            public double[] getFinalWeights()
            {
                return new double[0];
            }
        };

        // Create mock portfolio index via Mockito
        PerformanceIndex dummyPortfolio = mock(PerformanceIndex.class);
        when(dummyPortfolio.getDeltaPercentage()).thenReturn(new double[] { 0.01, 0.02, -0.01 });

        // Act
        RebalancingMetrics metrics = RebalancingMetricsCalculator.calculate(emptyContext, dummyPortfolio, riskFreeRate);

        // Assert - Historic metrics should be computed from dummyPortfolio
        assertTrue(!Double.isNaN(metrics.getHistoricalSharpe()));
        assertTrue(!Double.isNaN(metrics.getHistoricalVolatility()));
        assertTrue(!Double.isNaN(metrics.getHistoricalVaR()));

        // Assert - Asset analytics metrics must be NaN due to empty asset list
        assertTrue(Double.isNaN(metrics.getUnchangedVolatility()));
        assertTrue(Double.isNaN(metrics.getUnchangedVaR()));
        assertTrue(Double.isNaN(metrics.getRebalancedSharpe()));
        assertTrue(Double.isNaN(metrics.getRebalancedVolatility()));
        assertTrue(Double.isNaN(metrics.getRebalancedVaR()));
    }

    @Test
    public void testCalculateFallbackWhenWeightsAreNull()
    {
        // Arrange - Context yields null for weights to trigger safe fallbacks
        RebalancingContext nullWeightsContext = new RebalancingContext(null, null)
        {
            @Override
            public List<PerformanceIndex> getAssets()
            {
                return mockAssets;
            }

            @Override
            public double[] getCurrentWeights()
            {
                return null;
            }

            @Override
            public double[] getFinalWeights()
            {
                return null;
            }
        };

        // Act
        RebalancingMetrics metrics = RebalancingMetricsCalculator.calculate(nullWeightsContext, null, riskFreeRate);

        // Assert - Fallback initializes an empty array size of assets
        assertTrue(!Double.isNaN(metrics.getUnchangedVolatility()));
        assertTrue(!Double.isNaN(metrics.getRebalancedVolatility()));

        // Unchanged and Rebalanced metrics must be identical since finalWeights
        // falls back to currentWeights
        assertThat(metrics.getUnchangedVolatility(), is(metrics.getRebalancedVolatility()));
        assertThat(metrics.getUnchangedSharpe(), is(metrics.getRebalancedSharpe()));
        assertThat(metrics.getUnchangedVaR(), is(metrics.getRebalancedVaR()));
    }

    @Test
    public void testCalculatePortfolioIndexInsufficientDeltas()
    {
        // Arrange - PortfolioIndex has less than 2 historical return records
        // (insufficient for variance)
        PerformanceIndex invalidPortfolio = mock(PerformanceIndex.class);
        when(invalidPortfolio.getDeltaPercentage()).thenReturn(new double[] { 0.05 });

        // Act
        RebalancingMetrics metrics = RebalancingMetricsCalculator.calculate(context, invalidPortfolio, riskFreeRate);

        // Assert - Volatility needs >= 2 delta values, so it must fall back to
        // NaN
        assertTrue(Double.isNaN(metrics.getHistoricalVolatility()));

        // Assert - Sharpe Ratio also becomes NaN because standard deviation
        // (denominator) is NaN
        assertTrue(Double.isNaN(metrics.getHistoricalSharpe()));

        // Assert - Value at Risk (VaR) also becomes NaN because it requires a
        // valid standard deviation
        assertTrue(Double.isNaN(metrics.getHistoricalVaR()));
    }
}