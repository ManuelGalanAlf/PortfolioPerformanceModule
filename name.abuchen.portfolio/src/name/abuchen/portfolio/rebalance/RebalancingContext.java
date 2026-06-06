package name.abuchen.portfolio.rebalance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * The shared state object that travels through the Rebalancing Pipeline.
 * Holds all intermediate calculations, constraints, and final proposed orders.
 */
public class RebalancingContext
{
    private final List<PerformanceIndex> assets;
    private final RebalancingConfig config;
    private final DecisionLogger logger;
    private List<String> assetIdentifiers;
    
    // Intermediate and final weight stages
    private double[] currentWeights;
    private double[] targetWeights;
    private double[] constrainedWeights;
    private double[] redundancyFilteredWeights;
    private double[] finalWeights;
    
    // Additional data context
    private double[][] correlationMatrix;
    private double totalPortfolioValue;
    private double[] assetPrices;
    
    private List<Order> proposedOrders = new ArrayList<>();
    private boolean aborted = false;
    private double cashAccountBalance;

    public RebalancingContext(List<PerformanceIndex> assets, RebalancingConfig config)
    {
        this.assets = assets;
        this.config = config != null ? config : new RebalancingConfig();
        this.logger = new DecisionLogger();
    }

    public RebalancingContext(List<PerformanceIndex> assets, List<String> assetIdentifiers,
                    RebalancingConfig config)
    {
        this.assets = assets;
        this.assetIdentifiers = assetIdentifiers != null ? new ArrayList<>(assetIdentifiers) : null;
        this.config = config != null ? config : new RebalancingConfig();
        this.logger = new DecisionLogger();
    }

    /**
     * Adds an order to the proposed orders list. If an order for the same asset
     * and side already exists, merges it into a single order by summing
     * quantities.
     */
    public void mergeOrder(Order order)
    {
        for (int i = 0; i < proposedOrders.size(); i++)
        {
            Order existing = proposedOrders.get(i);
            if (existing.getSide() == order.getSide() && existing.getAsset().equals(order.getAsset()))
            {
                proposedOrders.set(i, existing.withQuantity(existing.getQuantity() + order.getQuantity()));
                return;
            }
        }
        proposedOrders.add(order);
    }

    public List<PerformanceIndex> getAssets()
    {
        return assets;
    }

    public RebalancingConfig getConfig()
    {
        return config;
    }

    public DecisionLogger getLogger()
    {
        return logger;
    }

    public double[] getCurrentWeights()
    {
        return currentWeights;
    }

    public void setCurrentWeights(double[] currentWeights)
    {
        this.currentWeights = currentWeights;
    }

    public double[] getTargetWeights()
    {
        return targetWeights;
    }

    public void setTargetWeights(double[] targetWeights)
    {
        this.targetWeights = targetWeights;
    }

    public double[] getConstrainedWeights()
    {
        return constrainedWeights;
    }

    public void setConstrainedWeights(double[] constrainedWeights)
    {
        this.constrainedWeights = constrainedWeights;
    }

    public double[] getRedundancyFilteredWeights()
    {
        return redundancyFilteredWeights;
    }

    public void setRedundancyFilteredWeights(double[] redundancyFilteredWeights)
    {
        this.redundancyFilteredWeights = redundancyFilteredWeights;
    }

    public double[] getFinalWeights()
    {
        return finalWeights;
    }

    public void setFinalWeights(double[] finalWeights)
    {
        this.finalWeights = finalWeights;
    }

    public List<String> getAssetIdentifiers()
    {
        return assetIdentifiers == null ? Collections.emptyList() : Collections.unmodifiableList(assetIdentifiers);
    }

    public String getAssetIdentifier(int index)
    {
        if (assetIdentifiers == null || index < 0 || index >= assetIdentifiers.size())
            return null;
        return assetIdentifiers.get(index);
    }

    public void setAssetIdentifiers(List<String> assetIdentifiers)
    {
        if (assetIdentifiers == null)
        {
            this.assetIdentifiers = null;
            return;
        }

        this.assetIdentifiers = new ArrayList<>(assetIdentifiers);
    }

    public double[][] getCorrelationMatrix()
    {
        return correlationMatrix;
    }

    public void setCorrelationMatrix(double[][] correlationMatrix)
    {
        this.correlationMatrix = correlationMatrix;
    }

    public double getTotalPortfolioValue()
    {
        return totalPortfolioValue;
    }

    public void setTotalPortfolioValue(double totalPortfolioValue)
    {
        this.totalPortfolioValue = totalPortfolioValue;
    }

    public double[] getAssetPrices()
    {
        return assetPrices;
    }

    public void setAssetPrices(double[] assetPrices)
    {
        this.assetPrices = assetPrices;
    }

    public double getCashAccountBalance()
    {
        return cashAccountBalance;
    }

    public void setCashAccountBalance(double cashAccountBalance)
    {
        this.cashAccountBalance = cashAccountBalance;
    }

    public List<Order> getProposedOrders()
    {
        return proposedOrders;
    }

    public void setProposedOrders(List<Order> proposedOrders)
    {
        this.proposedOrders = proposedOrders;
    }
    
    public boolean isAborted()
    {
        return aborted;
    }

    public void setAborted(boolean aborted)
    {
        this.aborted = aborted;
    }

    public void addOrder(Order order)
    {
        this.proposedOrders.add(order);
    }
}
