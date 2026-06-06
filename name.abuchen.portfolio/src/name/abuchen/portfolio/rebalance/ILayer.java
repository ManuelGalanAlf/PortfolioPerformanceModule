package name.abuchen.portfolio.rebalance;

/**
 * Interface for a processing layer in the Rebalancing Pipeline (Chain of Responsibility).
 */
public interface ILayer
{
    
    /**
     * Processes the rebalancing context, applying specific rules and transformations
     * according to the layer's responsibility.
     * 
     * @param context The shared state of the rebalancing process.
     */
    void process(RebalancingContext context);
}
