package name.abuchen.portfolio.rebalance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Test;

public class RebalancingEngineTest
{
    private RebalancingContext createTriggeredContext()
    {
        RebalancingConfig config = mock(RebalancingConfig.class);
        RebalancingContext context = new RebalancingContext(null, null);
        return context;
    }

    @Test
    public void testExecutePipeline()
    {
        RebalancingEngine engine = new RebalancingEngine();

        ILayer layer1 = mock(ILayer.class);
        ILayer layer2 = mock(ILayer.class);

        engine.addLayer(layer1);
        engine.addLayer(layer2);

        RebalancingContext context = createTriggeredContext();
        RebalancingContext result = engine.evaluateAndExecute(context);

        assertThat(result, is(context));
        verify(layer1, times(1)).process(context);
        verify(layer2, times(1)).process(context);
    }

    @Test
    public void testExecutePipelineAbortsEarly()
    {
        RebalancingEngine engine = new RebalancingEngine();

        ILayer layer1 = mock(ILayer.class);
        ILayer layer2 = mock(ILayer.class);

        doAnswer(invocation -> {
            RebalancingContext ctx = invocation.getArgument(0);
            ctx.setAborted(true);
            return null;
        }).when(layer1).process(org.mockito.ArgumentMatchers.any());

        engine.addLayer(layer1);
        engine.addLayer(layer2);

        RebalancingContext context = createTriggeredContext();
        engine.evaluateAndExecute(context);

        verify(layer1, times(1)).process(context);
        verify(layer2, times(0)).process(context);
    }
}