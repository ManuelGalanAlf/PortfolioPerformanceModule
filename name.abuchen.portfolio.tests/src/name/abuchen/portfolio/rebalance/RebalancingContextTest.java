package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class RebalancingContextTest
{
    private static final double TOLERANCE = 0.1e-10;

    @Test
    public void testInitialization()
    {
        List<PerformanceIndex> assets = Collections.singletonList(mock(PerformanceIndex.class));
        RebalancingConfig config = new RebalancingConfig();

        RebalancingContext context = new RebalancingContext(assets, config);

        assertThat(context.getAssets(), is(assets));
        assertThat(context.getConfig(), is(config));
        assertThat(context.getLogger(), is(notNullValue()));
        assertThat(context.isAborted(), is(false));
        assertThat(context.getProposedOrders(), is(notNullValue()));
        assertThat(context.getProposedOrders().size(), is(0));
    }

    @Test
    public void testInitializationWithIdentifiers()
    {
        List<PerformanceIndex> assets = Arrays.asList(mock(PerformanceIndex.class));
        List<String> identifiers = Arrays.asList("AAPL");
        RebalancingConfig config = new RebalancingConfig();

        RebalancingContext context = new RebalancingContext(assets, identifiers, config);

        assertThat(context.getAssetIdentifier(0), is("AAPL"));
        assertThat(context.getAssetIdentifier(1), is(nullValue())); // out of bounds
    }

    @Test
    public void testStateAttributesSetAndGet()
    {
        RebalancingContext context = new RebalancingContext(Collections.emptyList(), null);

        double[] weights = new double[] { 0.5, 0.5 };
        
        context.setCurrentWeights(weights);
        assertThat(context.getCurrentWeights(), is(weights));

        context.setTargetWeights(weights);
        assertThat(context.getTargetWeights(), is(weights));

        context.setConstrainedWeights(weights);
        assertThat(context.getConstrainedWeights(), is(weights));

        context.setRedundancyFilteredWeights(weights);
        assertThat(context.getRedundancyFilteredWeights(), is(weights));

        context.setFinalWeights(weights);
        assertThat(context.getFinalWeights(), is(weights));

        double[][] matrix = new double[][] { { 1.0 } };
        context.setCorrelationMatrix(matrix);
        assertThat(context.getCorrelationMatrix(), is(matrix));

        context.setTotalPortfolioValue(1000.0);
        assertThat(context.getTotalPortfolioValue(), is(1000.0));
    }

    @Test
    public void testOrderManagement()
    {
        RebalancingContext context = new RebalancingContext(Collections.emptyList(), null);
        Order order1 = new Order(null, Order.OrderSide.BUY, 10, 10);
        Order order2 = new Order(null, Order.OrderSide.SELL, 5, 20);

        context.addOrder(order1);
        context.addOrder(order2);

        assertThat(context.getProposedOrders().size(), is(2));
        assertThat(context.getProposedOrders().get(0), is(order1));
        assertThat(context.getProposedOrders().get(1), is(order2));
    }

    @Test
    public void testAbortedState()
    {
        RebalancingContext context = new RebalancingContext(Collections.emptyList(), null);
        assertThat(context.isAborted(), is(false));

        context.setAborted(true);
        assertThat(context.isAborted(), is(true));
    }
}
