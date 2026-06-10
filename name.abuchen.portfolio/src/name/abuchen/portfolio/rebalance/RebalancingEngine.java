package name.abuchen.portfolio.rebalance;

import java.util.ArrayList;
import java.util.List;

/**
 * The orchestrator of the Rebalancing Pipeline.
 * <p>
 * Executes the Chain of Responsibility passing the {@link RebalancingContext}
 * through all configured layers. Optionally integrates with the
 * {@link RebalancingTrigger} to check whether a rebalancing is needed before
 * running the full pipeline.
 */
public class RebalancingEngine
{
    
    private final List<ILayer> pipeline = new ArrayList<>();
    private final RebalancingTrigger trigger = new RebalancingTrigger();

    public RebalancingEngine()
    {
    }

    /**
     * Adds a layer to the end of the pipeline.
     * @param layer the ILayer to add
     */
    public void addLayer(ILayer layer)
    {
        if (layer != null)
        {
            pipeline.add(layer);
        }
    }

    /**
     * Evaluates trigger conditions and, if any fires, executes the full pipeline.
     * <p>
     * This is the recommended entry point for production use. It prevents the
     * engine from recalculating the covariance matrix and running all layers
     * when no significant change has occurred.
     *
     * @param context the context containing initial data
     * @return the processed context, or the unmodified context if no trigger fired
     */
    public RebalancingContext evaluateAndExecute(RebalancingContext context)
    {
        // 1. Check event-based triggers first (Cash-in)
        RebalancingTrigger.TriggerType eventTrigger = trigger.evaluateMoneyChanged(context);
        if (eventTrigger != RebalancingTrigger.TriggerType.NONE)
        {
            context.getLogger().log("Engine",
                            String.format("Trigger fired: %s — starting pipeline.", eventTrigger));
            trigger.updateLastEvaluationTime();
            return execute(context);
        }

        // 2. Check config-change trigger (Config-changed)
        RebalancingTrigger.TriggerType configTrigger = trigger.evaluateConfigChanged(context.getConfig());
        if (configTrigger != RebalancingTrigger.TriggerType.NONE)
        {
            context.getLogger().log("Engine", String.format("Trigger fired: %s — starting pipeline.", configTrigger));
            trigger.updateLastEvaluationTime();
            return execute(context);
        }

        // 3. Check time-based trigger (Time-based)
        RebalancingTrigger.TriggerType timeTrigger = trigger.evaluateTimeBased(context.getConfig());
        if (timeTrigger != RebalancingTrigger.TriggerType.NONE)
        {
            context.getLogger().log("Engine",
                            String.format("Trigger fired: %s — starting pipeline.", timeTrigger));
            trigger.updateLastEvaluationTime();
            return execute(context);
        }

        // 4. Block execution if neither triggers nor manual override are present
        context.getLogger().log("Engine", "No trigger condition met. Pipeline not executed.");
        return context;
    }

    /**
     * Executes the pipeline directly over the given context, bypassing triggers.
     * @param context the context containing initial data
     * @return the processed context containing final weights and orders
     */
    private RebalancingContext execute(RebalancingContext context)
    {
        context.getLogger().log("Engine", "Starting rebalancing pipeline with " + pipeline.size() + " layers.");
        
        for (ILayer layer : pipeline)
        {
            long startTime = System.currentTimeMillis();
            
            layer.process(context);
            
            long duration = System.currentTimeMillis() - startTime;
            context.getLogger().log("Engine", String.format("Layer %s completed in %d ms.", layer.getClass().getSimpleName(), duration));
            
            if (context.isAborted())
            {
                context.getLogger().log("Engine", "Pipeline ABORTED by layer: " + layer.getClass().getSimpleName());
                break;
            }
        }
        
        context.getLogger().log("Engine", "Rebalancing pipeline finished.");
        return context;
    }
}

