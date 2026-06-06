package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import name.abuchen.portfolio.math.Covariance;
import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class MinVolatilityStrategyTest
{
    private static final double TOLERANCE = 0.1e-6;

    @Test
    public void testOptimizeHandlesEmptyAssets()
    {
        MinVolatilityStrategy strategy = new MinVolatilityStrategy();
        Covariance covariance = mock(Covariance.class);
        RebalancingConfig config = new RebalancingConfig();

        double[] weights = strategy.optimize(Collections.emptyList(), covariance, config);
        assertThat(weights.length, is(0));
    }

    @Test
    public void testOptimizeCalculatesInverseVolatility()
    {
        MinVolatilityStrategy strategy = new MinVolatilityStrategy();
        
        // Annualized Vol = DailyStdDev * sqrt(252)
        // We want Asset 0 to have 20% vol and Asset 1 to have 10% vol.
        double scale = Math.sqrt(name.abuchen.portfolio.math.FinancialConstants.US_TRADING_DAYS_PER_YEAR);
        double d0 = 0.20 / scale;
        double d1 = 0.10 / scale;

        PerformanceIndex a0 = mock(PerformanceIndex.class);
        when(a0.getDeltaPercentage()).thenReturn(new double[] { d0, 0, -d0 }); // StdDev = d0
        
        PerformanceIndex a1 = mock(PerformanceIndex.class);
        when(a1.getDeltaPercentage()).thenReturn(new double[] { d1, 0, -d1 }); // StdDev = d1

        List<PerformanceIndex> assets = Arrays.asList(a0, a1);
        Covariance covariance = mock(Covariance.class);
        RebalancingConfig config = new RebalancingConfig();

        double[] weights = strategy.optimize(assets, covariance, config);
        
        assertThat(weights.length, is(2));
        
        // Asset 1 should have twice the weight of Asset 0 because it has half the volatility.
        // Inverse vol: inv0 = 1 / 0.20 = 5. inv1 = 1 / 0.10 = 10.
        // Sum = 15.
        // w0 = 5 / 15 = 1/3 (0.333...)
        // w1 = 10 / 15 = 2/3 (0.666...)
        
        assertThat(weights[0], closeTo(1.0 / 3.0, TOLERANCE));
        assertThat(weights[1], closeTo(2.0 / 3.0, TOLERANCE));
    }

    @Test
    public void testOptimizeHandlesZeroVolatility()
    {
        MinVolatilityStrategy strategy = new MinVolatilityStrategy();
        
        List<PerformanceIndex> assets = Arrays.asList(
            mock(PerformanceIndex.class),
            mock(PerformanceIndex.class)
        );

        Covariance covariance = mock(Covariance.class);
        // Both have zero variance
        when(covariance.getCovarianceEntry(any(Integer.class), any(Integer.class))).thenReturn(0.0);
        
        RebalancingConfig config = new RebalancingConfig();

        double[] weights = strategy.optimize(assets, covariance, config);
        
        // Should fallback to equal distribution
        assertThat(weights[0], closeTo(0.5, TOLERANCE));
        assertThat(weights[1], closeTo(0.5, TOLERANCE));
    }
}
