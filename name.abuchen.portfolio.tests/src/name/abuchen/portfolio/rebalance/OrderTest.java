package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

import org.junit.Test;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class OrderTest
{
    private static final double TOLERANCE = 0.1e-10;

    @Test
    public void testOrderCreationAndGetters()
    {
        PerformanceIndex mockAsset = mock(PerformanceIndex.class);
        Order.OrderSide side = Order.OrderSide.BUY;
        double quantity = 10.5;
        double estimatedPrice = 100.0;

        Order order = new Order(mockAsset, side, quantity, estimatedPrice);

        assertThat(order.getAsset(), is(mockAsset));
        assertThat(order.getSide(), is(side));
        assertThat(order.getQuantity(), is(quantity));
        assertThat(order.getEstimatedPrice(), is(estimatedPrice));
    }

    @Test
    public void testGetTotalValue()
    {
        PerformanceIndex mockAsset = mock(PerformanceIndex.class);
        Order order = new Order(mockAsset, Order.OrderSide.SELL, 5.0, 20.0);

        assertThat(order.getTotalValue(), is(100.0));
    }
}
