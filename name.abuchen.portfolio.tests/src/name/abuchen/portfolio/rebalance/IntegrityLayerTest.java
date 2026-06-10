package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class IntegrityLayerTest
{
    private PerformanceIndex createMockAsset(double[] deltas)
    {
        PerformanceIndex asset = mock(PerformanceIndex.class);
        when(asset.getDeltaPercentage()).thenReturn(deltas);
        when(asset.getTotals()).thenReturn(new long[] { 100000L });
        return asset;
    }

    @Test
    public void testAbortWhenAssetsAreNull()
    {
        RebalancingContext context = new RebalancingContext(null, null);
        IntegrityLayer layer = new IntegrityLayer();
        
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testAbortWhenAssetListIsEmpty()
    {
        RebalancingContext context = new RebalancingContext(Collections.emptyList(), null);
        IntegrityLayer layer = new IntegrityLayer();
        
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testAbortWhenAssetContainsNull()
    {
        RebalancingContext context = new RebalancingContext(Arrays.asList(createMockAsset(new double[]{0.1}), null), null);
        IntegrityLayer layer = new IntegrityLayer();
        
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testAbortWhenAssetHasNoHistory()
    {
        PerformanceIndex asset = createMockAsset(null);
        RebalancingContext context = new RebalancingContext(Collections.singletonList(asset), null);
        IntegrityLayer layer = new IntegrityLayer();
        
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testAbortWhenAssetHasInsufficientHistory()
    {
        // Less than 30 days
        PerformanceIndex asset = createMockAsset(new double[20]); 
        RebalancingContext context = new RebalancingContext(Collections.singletonList(asset), null);
        IntegrityLayer layer = new IntegrityLayer();
        
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testAbortWhenAssetHasNaNValues()
    {
        double[] deltas = new double[40];
        deltas[15] = Double.NaN;
        PerformanceIndex asset = createMockAsset(deltas);
        
        RebalancingContext context = new RebalancingContext(Collections.singletonList(asset), null);
        IntegrityLayer layer = new IntegrityLayer();
        
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testAbortWhenAssetHasInfiniteValues()
    {
        double[] deltas = new double[40];
        deltas[20] = Double.POSITIVE_INFINITY;
        PerformanceIndex asset = createMockAsset(deltas);
        
        RebalancingContext context = new RebalancingContext(Collections.singletonList(asset), null);
        IntegrityLayer layer = new IntegrityLayer();
        
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testPassWhenDataIsValid()
    {
        double[] deltas = new double[40];
        Arrays.fill(deltas, 0.01);
        PerformanceIndex asset = createMockAsset(deltas);
        
        RebalancingContext context = new RebalancingContext(Collections.singletonList(asset), null);
        IntegrityLayer layer = new IntegrityLayer();
        
        layer.process(context);
        
        assertThat(context.isAborted(), is(false));
        assertThat(context.getCurrentWeights(), is(notNullValue()));
        assertThat(context.getCurrentWeights().length, is(1));
        assertThat(context.getCurrentWeights()[0], closeTo(1.0, 1e-9));
    }

    @Test
    public void testAbortWhenTotalPortfolioValueIsZero()
    {
        double[] deltas = new double[40];
        Arrays.fill(deltas, 0.01);
        PerformanceIndex asset = mock(PerformanceIndex.class);
        when(asset.getDeltaPercentage()).thenReturn(deltas);
        when(asset.getTotals()).thenReturn(new long[] { 0L }); // zero value

        RebalancingContext context = new RebalancingContext(Collections.singletonList(asset), null);
        IntegrityLayer layer = new IntegrityLayer();

        layer.process(context);

        assertThat(context.isAborted(), is(true));
    }
}
