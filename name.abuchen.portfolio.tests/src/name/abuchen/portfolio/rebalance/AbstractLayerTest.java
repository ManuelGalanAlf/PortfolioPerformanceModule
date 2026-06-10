package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class AbstractLayerTest
{
    // StubLayer for testing
    private static class StubLayer extends AbstractLayer
    {
        private double[][] candidates;
        private String[] labels;

        public void setupCandidates(double[][] candidates, String[] labels)
        {
            this.candidates = candidates;
            this.labels = labels;
        }

        @Override
        protected double[][] weightCandidates(RebalancingContext context)
        {
            return candidates;
        }

        @Override
        protected String[] weightCandidateLabels()
        {
            return labels;
        }

        @Override
        public void process(RebalancingContext context)
        {

        }
    }

    private List<PerformanceIndex> dummyAssets(int n)
    {
        List<PerformanceIndex> list = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            list.add(mock(PerformanceIndex.class));
        }
        return list;
    }

    @Test
    public void testResolveWeightsReturnsFirstCandidateIfValid()
    {
        RebalancingContext context = new RebalancingContext(dummyAssets(3), null);
        double[] optimalWeights = { 0.40, 0.40, 0.20 };

        StubLayer layer = new StubLayer();
        layer.setupCandidates(new double[][] { optimalWeights }, new String[] { "optimalTarget" });

        double[] result = layer.resolveWeightsWithFallback(context);

        assertThat(result, is(notNullValue()));
        assertThat(result, is(optimalWeights));
        assertThat(context.isAborted(), is(false));
    }

    @Test
    public void testResolveWeightsActivatesFallbackWhenFirstIsInvalid()
    {
        RebalancingContext context = new RebalancingContext(dummyAssets(3), null);
        double[] invalidWeights = { 0.80, 0.50, 0.20 };
        double[] fallbackWeights = { 0.33, 0.33, 0.34 };

        StubLayer layer = new StubLayer();
        layer.setupCandidates(new double[][] { invalidWeights, fallbackWeights },
                        new String[] { "invalidTarget", "emergencyFallback" });

        double[] result = layer.resolveWeightsWithFallback(context);

        assertThat(result, is(notNullValue()));
        assertThat(result, is(fallbackWeights));
        assertThat(context.isAborted(), is(false));
    }

    @Test
    public void testResolveWeightsAbortsContextWhenNoCandidatesAreValid()
    {
        RebalancingContext context = new RebalancingContext(dummyAssets(3), null);
        double[] invalid1 = { 1.20, 0.00, 0.00 };
        double[] invalid2 = { 0.50, -0.10, 0.60 };

        StubLayer layer = new StubLayer();
        layer.setupCandidates(new double[][] { invalid1, invalid2 }, new String[] { "error1", "error2" });

        double[] result = layer.resolveWeightsWithFallback(context);

        assertThat(result, is(nullValue()));
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testResolveWeightsReturnsNullIfCandidatesArrayIsNull()
    {
        RebalancingContext context = new RebalancingContext(dummyAssets(3), null);
        StubLayer layer = new StubLayer();
        layer.setupCandidates(null, null);

        double[] result = layer.resolveWeightsWithFallback(context);

        assertThat(result, is(nullValue()));
        assertThat(context.isAborted(), is(false));
    }

    @Test
    public void testAreWeightsValidMathematicalEdgeCases()
    {
        StubLayer layer = new StubLayer();
        int expectedSize = 3;

        // Valid Cases
        assertThat(layer.areWeightsValid(new double[] { 0.3333, 0.3333, 0.3333 }, expectedSize), is(true));
        assertThat(layer.areWeightsValid(new double[] { 0.50, 0.51, 0.00 }, expectedSize), is(true));

        // Invalid Cases: Null structures and inconsistent size
        assertThat(layer.areWeightsValid(null, expectedSize), is(false));
        assertThat(layer.areWeightsValid(new double[] { 0.50, 0.50 }, expectedSize), is(false));

        // Invalid cases: Special values (NaN o Infinites)
        assertThat(layer.areWeightsValid(new double[] { 0.50, Double.NaN, 0.50 }, expectedSize), is(false));
        assertThat(layer.areWeightsValid(new double[] { 0.50, Double.POSITIVE_INFINITY, 0.00 }, expectedSize),
                        is(false));

     // Invalid Cases: Financial Restrictions (Negative values ​​or sums outside of tolerance)
     assertThat(layer.areWeightsValid(new double[] { 0.50, -0.10, 0.60 }, expectedSize), is(false));
     assertThat(layer.areWeightsValid(new double[] { 0.60, 0.50, 0.00 }, expectedSize), is(false));
     assertThat(layer.areWeightsValid(new double[] { 0.40, 0.40, 0.00 }, expectedSize), is(false));
    }
}