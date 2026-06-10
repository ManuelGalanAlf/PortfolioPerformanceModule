package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.Test;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class ConstraintLayerTest
{
    private static final double TOLERANCE = 0.1e-6;

    private List<PerformanceIndex> dummyAssets(int n)
    {
        List<PerformanceIndex> list = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++)
            list.add(mock(PerformanceIndex.class));
        return list;
    }

    @Test
    public void testAbortWhenNoTargetWeights()
    {
        RebalancingContext context = new RebalancingContext(dummyAssets(2), null);
        ConstraintLayer layer = new ConstraintLayer();
        
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testWeightsWithinLimitsRemainUnchanged()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setWeightLimits(0.10, 0.40); // min 10%, max 40%
        
        RebalancingContext context = new RebalancingContext(dummyAssets(4), config);
        context.setTargetWeights(new double[] { 0.20, 0.30, 0.25, 0.25 });
        
        ConstraintLayer layer = new ConstraintLayer();
        layer.process(context);
        
        double[] constrained = context.getConstrainedWeights();
        assertThat(constrained.length, is(4));
        assertThat(constrained[0], closeTo(0.20, TOLERANCE));
        assertThat(constrained[1], closeTo(0.30, TOLERANCE));
        assertThat(constrained[2], closeTo(0.25, TOLERANCE));
        assertThat(constrained[3], closeTo(0.25, TOLERANCE));
    }

    @Test
    public void testMaxWeightConstraintIsApplied()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setWeightLimits(0.01, 0.40);
        
        RebalancingContext context = new RebalancingContext(dummyAssets(3), config);
        context.setTargetWeights(new double[] { 0.80, 0.10, 0.10 });
        
        ConstraintLayer layer = new ConstraintLayer();
        layer.process(context);
        
        double[] constrained = context.getConstrainedWeights();
        assertThat(constrained[0], closeTo(0.40, TOLERANCE));
        assertThat(constrained[1], closeTo(0.30, TOLERANCE));
        assertThat(constrained[2], closeTo(0.30, TOLERANCE));
        
        double sum = constrained[0] + constrained[1] + constrained[2];
        assertThat(sum, closeTo(1.0, TOLERANCE));
    }

    @Test
    public void testMinWeightConstraintIsApplied()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setWeightLimits(0.15, 0.60);
        
        RebalancingContext context = new RebalancingContext(dummyAssets(3), config);
        context.setTargetWeights(new double[] { 0.05, 0.45, 0.50 });
        
        ConstraintLayer layer = new ConstraintLayer();
        layer.process(context);
        
        double[] constrained = context.getConstrainedWeights();
        
        // With the iterative Waterfilling algorithm, the minimum is strictly enforced
        assertThat(constrained[0], closeTo(0.15, TOLERANCE));
        
        // The 0.10 added to Asset 0 was taken proportionally from Asset 1 and Asset 2
        // Asset 1: 0.45 - (0.10 * 0.45/0.95) = 0.40263157...
        // Asset 2: 0.50 - (0.10 * 0.50/0.95) = 0.44736842...
        assertThat(constrained[1], closeTo(0.402631578947, TOLERANCE));
        assertThat(constrained[2], closeTo(0.447368421052, TOLERANCE));
        
        double sum = constrained[0] + constrained[1] + constrained[2];
        assertThat(sum, closeTo(1.0, TOLERANCE));
    }

    @Test
    public void testAbortWhenMinWeightInfeasible()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setWeightLimits(0.35, 0.80); // 3 assets * min 0.35 = 1.05 > 1.0
        
        RebalancingContext context = new RebalancingContext(dummyAssets(3), config);
        context.setTargetWeights(new double[] { 0.40, 0.40, 0.20 });
        
        ConstraintLayer layer = new ConstraintLayer();
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testAbortWhenMaxWeightInfeasible()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setWeightLimits(0.00, 0.30); // 3 assets * max 0.30 = 0.90 < 1.0
        
        RebalancingContext context = new RebalancingContext(dummyAssets(3), config);
        context.setTargetWeights(new double[] { 0.40, 0.40, 0.20 });
        
        ConstraintLayer layer = new ConstraintLayer();
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }
}
