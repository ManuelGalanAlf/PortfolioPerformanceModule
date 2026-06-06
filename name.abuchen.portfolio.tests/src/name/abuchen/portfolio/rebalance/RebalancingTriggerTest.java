package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.Collections;

import org.junit.Test;

public class RebalancingTriggerTest
{
    @Test
    public void testTriggerTypeEnum()
    {
        assertThat(RebalancingTrigger.TriggerType.NONE.name(), is("NONE"));
        assertThat(RebalancingTrigger.TriggerType.CASH_IN.name(), is("CASH_IN"));
        assertThat(RebalancingTrigger.TriggerType.TIME_BASED.name(), is("TIME_BASED"));
        assertThat(RebalancingTrigger.TriggerType.CONFIG_CHANGED.name(), is("CONFIG_CHANGED"));
    }

    @Test
    public void testEvaluateWithContext()
    {
        RebalancingTrigger trigger = new RebalancingTrigger();
        RebalancingConfig config = new RebalancingConfig();
        RebalancingContext context = new RebalancingContext(Collections.emptyList(), config);

        assertThat(trigger.evaluateMoneyChanged(context), is(RebalancingTrigger.TriggerType.NONE));

        config.setNewCashAmount(500.0);
        assertThat(trigger.evaluateMoneyChanged(context), is(RebalancingTrigger.TriggerType.CASH_IN));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEvaluateWithNullContextThrowsException()
    {
        RebalancingTrigger trigger = new RebalancingTrigger();
        trigger.evaluateMoneyChanged((RebalancingContext) null);
    }

    @Test
    public void testEvaluateWithContextNullConfig()
    {
        RebalancingTrigger trigger = new RebalancingTrigger();
        RebalancingContext context = new RebalancingContext(Collections.emptyList(), null);
        assertThat(trigger.evaluateMoneyChanged(context), is(RebalancingTrigger.TriggerType.NONE));
    }

    @Test
    public void testEvaluateTimeBased()
    {
        RebalancingTrigger trigger = new RebalancingTrigger();
        RebalancingConfig config = new RebalancingConfig();
        
        config.setMonitoringIntervalMinutes(1440);
        assertThat(trigger.evaluateTimeBased(config), is(RebalancingTrigger.TriggerType.TIME_BASED));

        trigger.updateLastEvaluationTime();
        
        assertThat(trigger.evaluateTimeBased(config), is(RebalancingTrigger.TriggerType.NONE));
    }

    @Test
    public void testEvaluateTimeBasedWithNullConfig()
    {
        RebalancingTrigger trigger = new RebalancingTrigger();
        assertThat(trigger.evaluateTimeBased(null), is(RebalancingTrigger.TriggerType.NONE));
    }

    @Test
    public void testEvaluateWithCashVariation()
    {
        RebalancingTrigger trigger = new RebalancingTrigger();
        RebalancingConfig config = new RebalancingConfig();
        RebalancingContext context = new RebalancingContext(Collections.emptyList(), config);

        context.setCashAccountBalance(1000.0);
        assertThat(trigger.evaluateMoneyChanged(context), is(RebalancingTrigger.TriggerType.NONE));

        assertThat(trigger.evaluateMoneyChanged(context), is(RebalancingTrigger.TriggerType.NONE));

        context.setCashAccountBalance(1500.0);
        assertThat(trigger.evaluateMoneyChanged(context), is(RebalancingTrigger.TriggerType.CASH_IN));

        assertThat(trigger.evaluateMoneyChanged(context), is(RebalancingTrigger.TriggerType.NONE));

        context.setCashAccountBalance(1200.0);
        assertThat(trigger.evaluateMoneyChanged(context), is(RebalancingTrigger.TriggerType.CASH_IN));
    }

    @Test
    public void testEvaluateConfigChanged()
    {
        RebalancingTrigger trigger = new RebalancingTrigger();
        RebalancingConfig config = new RebalancingConfig();

        assertThat(trigger.evaluateConfigChanged(config), is(RebalancingTrigger.TriggerType.CONFIG_CHANGED));

        assertThat(trigger.evaluateConfigChanged(config), is(RebalancingTrigger.TriggerType.NONE));

        config.setMaxPortfolioVolatility(0.15);

        assertThat(trigger.evaluateConfigChanged(config), is(RebalancingTrigger.TriggerType.CONFIG_CHANGED));
        assertThat(trigger.evaluateConfigChanged(config), is(RebalancingTrigger.TriggerType.NONE));
    }

    @Test
    public void testEvaluateConfigChangedWithNullConfig()
    {
        RebalancingTrigger trigger = new RebalancingTrigger();
        assertThat(trigger.evaluateConfigChanged(null), is(RebalancingTrigger.TriggerType.NONE));
    }
}