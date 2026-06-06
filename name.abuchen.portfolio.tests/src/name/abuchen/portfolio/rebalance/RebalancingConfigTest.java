package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.Test;

public class RebalancingConfigTest
{
    private static final double TOLERANCE = 0.1e-10;

    @Test
    public void testDefaultValues()
    {
        RebalancingConfig config = new RebalancingConfig();

        assertThat(config.getStrategy(), is(RebalancingConfig.Strategy.MAX_SHARPE));
        assertThat(config.getMaxWeightPerAsset(), is(0.50));
        assertThat(config.getMinWeightPerAsset(), is(0.00));
        assertThat(config.getCorrelationThreshold(), is(0.85));
        assertThat(config.getMaxPortfolioVolatility(), is(1.0));
        assertThat(config.getInertiaTolerance(), is(0.02));
        assertThat(config.isAllowFractions(), is(false));
    }

    @Test
    public void testSettersAndGetters()
    {
        RebalancingConfig config = new RebalancingConfig();

        config.setStrategy(RebalancingConfig.Strategy.MIN_VOLATILITY);
        assertThat(config.getStrategy(), is(RebalancingConfig.Strategy.MIN_VOLATILITY));

        config.setCorrelationThreshold(0.75);
        assertThat(config.getCorrelationThreshold(), is(0.75));

        config.setMaxPortfolioVolatility(0.25);
        assertThat(config.getMaxPortfolioVolatility(), is(0.25));

        config.setInertiaTolerance(0.05);
        assertThat(config.getInertiaTolerance(), is(0.05));

        config.setAllowFractions(true);
        assertThat(config.isAllowFractions(), is(true));

        config.setNewCashAmount(1000.0);
        assertThat(config.getNewCashAmount(), is(1000.0));

        config.setCashBuffer(50.0);
        assertThat(config.getCashBuffer(), is(50.0));

        config.setCommissionFixed(5.0);
        assertThat(config.getCommissionFixed(), is(5.0));

        config.setCommissionVariable(0.01);
        assertThat(config.getCommissionVariable(), is(0.01));
    }

    @Test
    public void testSetWeightLimitsValid()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setWeightLimits(0.05, 0.30);
        assertThat(config.getMinWeightPerAsset(), is(0.05));
        assertThat(config.getMaxWeightPerAsset(), is(0.30));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetWeightLimitsInvalidReversed()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setWeightLimits(0.30, 0.05); // min > max
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetWeightLimitsInvalidNegative()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setWeightLimits(-0.1, 0.5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetWeightLimitsInvalidGreaterThanOne()
    {
        RebalancingConfig config = new RebalancingConfig();
        config.setWeightLimits(0.1, 1.5);
    }

    @Test
    public void testFrozenAssets()
    {
        RebalancingConfig config = new RebalancingConfig();
        String assetId = "AAPL";
        
        assertThat(config.isAssetFrozen(assetId), is(false));
        
        config.addFrozenAsset(assetId);
        assertThat(config.isAssetFrozen(assetId), is(true));
        
        config.removeFrozenAsset(assetId);
        assertThat(config.isAssetFrozen(assetId), is(false));
    }
}
