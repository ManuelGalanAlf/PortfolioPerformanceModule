package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class ViabilityLayerTest
{
    private static final double TOLERANCE = 0.1e-6;

    private PerformanceIndex createMockAsset(double[] accumulatedPercentages)
    {
        PerformanceIndex asset = mock(PerformanceIndex.class);
        when(asset.getAccumulatedPercentage()).thenReturn(accumulatedPercentages);
        return asset;
    }

    @Test
    public void testAbortWhenNoWeightsAvailable()
    {
        RebalancingContext context = new RebalancingContext(null, null);
        ViabilityLayer layer = new ViabilityLayer();
        
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testAbortWhenInvestableValueIsZero()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setCashBuffer(100.0); // Buffer is larger than total value
        
        RebalancingContext context = new RebalancingContext(null, config);
        context.setConstrainedWeights(new double[] { 1.0 });
        context.setTotalPortfolioValue(50.0);
        
        ViabilityLayer layer = new ViabilityLayer();
        layer.process(context);
        
        assertThat(context.isAborted(), is(true));
    }

    @Test
    public void testOrdersAreGeneratedCorrectly()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setCashBuffer(0.0);
        config.setAllowFractions(true); // Allow fractions to simplify math
        config.setInertiaTolerance(0.001);
        config.setCommissionFixed(0.0);
        config.setCommissionVariable(0.0);
        
        // Two assets. Price = 100 for both.
        PerformanceIndex a1 = createMockAsset(new double[] { 100.0 });
        PerformanceIndex a2 = createMockAsset(new double[] { 100.0 });
        List<PerformanceIndex> assets = Arrays.asList(a1, a2);
        
        RebalancingContext context = new RebalancingContext(assets, config);
        context.setTotalPortfolioValue(1000.0);
        
        // We currently have 50/50 (500$ each = 5 shares each)
        context.setCurrentWeights(new double[] { 0.50, 0.50 });
        
        // We want 80/20. 
        // A1 needs to go from 0.50 to 0.80 (Buy 30% = 300$ = 3 shares)
        // A2 needs to go from 0.50 to 0.20 (Sell 30% = 300$ = 3 shares)
        context.setConstrainedWeights(new double[] { 0.80, 0.20 });
        
        ViabilityLayer layer = new ViabilityLayer();
        layer.process(context);
        
        assertThat(context.isAborted(), is(false));
        List<Order> orders = context.getProposedOrders();
        
        assertThat(orders.size(), is(2));
        
        // Because of the SELL FIRST logic, order 0 should be the SELL order
        Order sellOrder = orders.get(0);
        assertThat(sellOrder.getSide(), is(Order.OrderSide.SELL));
        assertThat(sellOrder.getAsset(), is(a2));
        assertThat(sellOrder.getQuantity(), closeTo(3.0, TOLERANCE));
        
        Order buyOrder = orders.get(1);
        assertThat(buyOrder.getSide(), is(Order.OrderSide.BUY));
        assertThat(buyOrder.getAsset(), is(a1));
        assertThat(buyOrder.getQuantity(), closeTo(3.0, TOLERANCE));
        
        // Final weights should match the target since there were no blocks
        double[] finalWeights = context.getFinalWeights();
        assertThat(finalWeights[0], closeTo(0.80, TOLERANCE));
        assertThat(finalWeights[1], closeTo(0.20, TOLERANCE));
    }

    @Test
    public void testInertiaFilterBlocksSmallMoves()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setCashBuffer(0.0);
        config.setAllowFractions(true);
        config.setInertiaTolerance(0.05); // 5% tolerance
        
        PerformanceIndex a1 = createMockAsset(new double[] { 100.0 });
        RebalancingContext context = new RebalancingContext(Arrays.asList(a1), config);
        context.setTotalPortfolioValue(1000.0);
        
        context.setCurrentWeights(new double[] { 0.50 });
        // Target is 0.52. The drift is 0.02, which is < 0.05 tolerance.
        context.setConstrainedWeights(new double[] { 0.52 });
        
        ViabilityLayer layer = new ViabilityLayer();
        layer.process(context);
        
        assertThat(context.getProposedOrders().size(), is(0));
        
        // Final weight reverts to current
        assertThat(context.getFinalWeights()[0], closeTo(0.50, TOLERANCE));
    }

    @Test
    public void testCommissionFilterBlocksExpensiveTrades()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setCashBuffer(0.0);
        config.setAllowFractions(true);
        config.setInertiaTolerance(0.001);
        config.setCommissionFixed(10.0); // 10$ fixed fee
        config.setCommissionVariable(0.0);
        
        PerformanceIndex a1 = createMockAsset(new double[] { 100.0 });
        RebalancingContext context = new RebalancingContext(Arrays.asList(a1), config);
        context.setTotalPortfolioValue(1000.0);
        
        context.setCurrentWeights(new double[] { 0.50 });
        // Target is 0.60. Drift = 0.10. Trade value = 100$.
        // Commission is 10$. 10/100 = 10% commission rate.
        // Threshold is 2%, so this should be blocked.
        context.setConstrainedWeights(new double[] { 0.60 });
        
        ViabilityLayer layer = new ViabilityLayer();
        layer.process(context);
        
        assertThat(context.getProposedOrders().size(), is(0));
        assertThat(context.getFinalWeights()[0], closeTo(0.50, TOLERANCE)); // Reverts
    }

    @Test
    public void testCashBufferIsNeverUnderfundedDueToRoundingOrCommissions()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setCashBuffer(300.0);
        config.setAllowFractions(false); // Enable integer rounding to test cash buffer protection
        config.setInertiaTolerance(0.01);
        config.setCommissionFixed(5.0); // 5$ fixed commission per trade
        config.setCommissionVariable(0.005); // 0.5% variable commission
        
        PerformanceIndex a1 = createMockAsset(new double[] { 100.0 });
        PerformanceIndex a2 = createMockAsset(new double[] { 100.0 });
        List<PerformanceIndex> assets = Arrays.asList(a1, a2);
        
        RebalancingContext context = new RebalancingContext(assets, config);
        context.setTotalPortfolioValue(1000.0);
        
        // We currently have:
        // A1: 60% = 600$, A2: 30% = 300$, Cash = 100$.
        context.setCurrentWeights(new double[] { 0.60, 0.30 });
        
        // Target:
        // A1: 40% = 400$
        // A2: 30% = 300$
        // Cash Buffer = 300$. Investable value = 1000 - 300 = 700$.
        // Target weights: A1 = 400/700 = 0.571428, A2 = 300/700 = 0.428571.
        context.setConstrainedWeights(new double[] { 0.571428, 0.428571 });
        
        ViabilityLayer layer = new ViabilityLayer();
        layer.process(context);
        
        assertThat(context.isAborted(), is(false));
        
        // Calculate remaining cash using the same formula:
        double totalValue = context.getTotalPortfolioValue();
        double stockValue = 0.0;
        for (int i = 0; i < assets.size(); i++)
        {
            stockValue += context.getCurrentWeights()[i] * totalValue;
        }
        double existingCash = totalValue - stockValue;
        
        double totalCommissions = 0.0;
        double netInvested = 0.0;
        for (Order order : context.getProposedOrders())
        {
            double actualTradeValue = order.getQuantity() * order.getEstimatedPrice();
            double comm = config.getCommissionFixed() + (actualTradeValue * config.getCommissionVariable());
            totalCommissions += comm;
            if (order.getSide() == Order.OrderSide.BUY)
            {
                netInvested += actualTradeValue;
            }
            else
            {
                netInvested -= actualTradeValue;
            }
        }
        double remainingCash = existingCash - netInvested - totalCommissions;
        
        // Remaining cash must be >= cash buffer (300.0)
        org.hamcrest.MatcherAssert.assertThat(remainingCash >= 300.0, is(true));
    }
}
