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

public class MaxSharpeStrategyTest
{
    private static final double TOLERANCE = 0.1e-6;

    @Test
    public void testOptimizeHandlesEmptyAssets()
    {
        MaxSharpeStrategy strategy = new MaxSharpeStrategy();
        Covariance covariance = mock(Covariance.class);
        RebalancingConfig config = new RebalancingConfig();

        double[] weights = strategy.optimize(Collections.emptyList(), covariance, config);
        assertThat(weights.length, is(0));
    }

    @Test
    public void testOptimizeReturnsValidWeights()
    {
        MaxSharpeStrategy strategy = new MaxSharpeStrategy();
        
        PerformanceIndex p1 = mock(PerformanceIndex.class);
        when(p1.getDeltaPercentage()).thenReturn(new double[] { 0.01, 0.02, 0.03 });
        
        PerformanceIndex p2 = mock(PerformanceIndex.class);
        when(p2.getDeltaPercentage()).thenReturn(new double[] { 0.03, 0.01, -0.01 });
        
        List<PerformanceIndex> assets = Arrays.asList(p1, p2);

        Covariance covariance = mock(Covariance.class);
        when(covariance.getPortfolioVariance(any(double[].class))).thenReturn(0.01); // Fixed mock variance
        
        RebalancingConfig config = new RebalancingConfig();

        double[] weights = strategy.optimize(assets, covariance, config);
        
        assertThat(weights.length, is(2));
        
        // Ensure the returned weights sum to 1.0
        double sum = weights[0] + weights[1];
        assertThat(sum, closeTo(1.0, TOLERANCE));
        
        // Ensure weights are positive
        assertThat(weights[0] >= 0.0, is(true));
        assertThat(weights[1] >= 0.0, is(true));
    }
}
