package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.junit.Test;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class RedundancyLayerTest
{
    private static final double TOLERANCE = 0.1e-6;

    private PerformanceIndex createMockAsset(double[] deltas)
    {
        PerformanceIndex asset = mock(PerformanceIndex.class);
        when(asset.getDeltaPercentage()).thenReturn(deltas);
        return asset;
    }

    @Test
    public void testAbortWhenMissingCorrelationMatrix()
    {
        RebalancingContext context = new RebalancingContext(null, null);
        context.setConstrainedWeights(new double[] { 0.5, 0.5 });
        // Correlation matrix is null

        RedundancyLayer layer = new RedundancyLayer();
        layer.process(context);

        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testAbortWhenMissingWeights()
    {
        RebalancingContext context = new RebalancingContext(null, null);
        context.setCorrelationMatrix(new double[][] { { 1.0 } });
        // Constrained weights are null

        RedundancyLayer layer = new RedundancyLayer();
        layer.process(context);

        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testNoRedundancyEliminationWhenBelowThreshold()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setCorrelationThreshold(0.85);

        RebalancingContext context = new RebalancingContext(null, config);
        context.setConstrainedWeights(new double[] { 0.50, 0.50 });
        
        // Correlation is 0.50, which is < 0.85
        double[][] correlation = new double[][] {
            { 1.0, 0.5 },
            { 0.5, 1.0 }
        };
        context.setCorrelationMatrix(correlation);

        RedundancyLayer layer = new RedundancyLayer();
        layer.process(context);

        double[] filtered = context.getRedundancyFilteredWeights();
        assertThat(filtered[0], closeTo(0.50, TOLERANCE));
        assertThat(filtered[1], closeTo(0.50, TOLERANCE));
    }

    @Test
    public void testRedundantAssetIsEliminatedBasedOnVolatility()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setCorrelationThreshold(0.85);
        config.setWeightLimits(0.0, 1.0);

        // Asset 0 has high volatility (deltas oscillate wildly)
        PerformanceIndex asset0 = createMockAsset(new double[] { 0.1, -0.1, 0.2, -0.2 });
        // Asset 1 has low volatility (deltas are stable)
        PerformanceIndex asset1 = createMockAsset(new double[] { 0.01, 0.01, 0.02, 0.01 });

        RebalancingContext context = new RebalancingContext(Arrays.asList(asset0, asset1), config);
        context.setConstrainedWeights(new double[] { 0.40, 0.60 });
        
        // Correlation is 0.90, which is > 0.85. They are redundant.
        double[][] correlation = new double[][] {
            { 1.0, 0.9 },
            { 0.9, 1.0 }
        };
        context.setCorrelationMatrix(correlation);

        RedundancyLayer layer = new RedundancyLayer();
        layer.process(context);

        double[] filtered = context.getRedundancyFilteredWeights();
        
        // Asset 0 should be eliminated because it's more volatile.
        // Its weight (0.40) should be transferred to Asset 1 (0.60 + 0.40 = 1.00).
        assertThat(filtered[0], closeTo(0.00, TOLERANCE));
        assertThat(filtered[1], closeTo(1.00, TOLERANCE));
    }

    @Test
    public void testRedundantSurvivorIsCappedAndExcessGoesToCash()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setCorrelationThreshold(0.85);
        config.setWeightLimits(0.0, 0.50);

        PerformanceIndex asset0 = createMockAsset(new double[] { 0.1, -0.1, 0.2, -0.2 });
        PerformanceIndex asset1 = createMockAsset(new double[] { 0.01, 0.01, 0.02, 0.01 });

        RebalancingContext context = new RebalancingContext(Arrays.asList(asset0, asset1), config);

        context.setConstrainedWeights(new double[] { 0.40, 0.60 });

        double[][] correlation = new double[][] { { 1.0, 0.9 }, { 0.9, 1.0 } };
        context.setCorrelationMatrix(correlation);

        RedundancyLayer layer = new RedundancyLayer();
        layer.process(context);

        double[] filtered = context.getRedundancyFilteredWeights();
        assertThat(filtered[0], closeTo(0.00, 1.0E-8));
        assertThat(filtered[1], closeTo(0.50, 1.0E-8));

        double totalAssetWeight = filtered[0] + filtered[1];
        assertThat(totalAssetWeight, closeTo(0.50, 1.0E-8));
    }
}
