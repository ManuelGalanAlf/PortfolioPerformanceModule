package name.abuchen.portfolio.rebalance;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User-defined parameters and constraints for the RebalancingEngine.
 * <p>
 * This configuration class holds all user-editable parameters that control
 * the rebalancing behavior. Parameters are validated to prevent invalid states.
 * <p>
 * Key parameters include:
 * <ul>
 * <li><b>Risk Profile:</b> Strategy selection (Min Volatility vs Max Sharpe)</li>
 * <li><b>Concentration Limits:</b> Max/min weight per asset (use setWeightLimits() to change both safely)</li>
 * <li><b>Risk Controls:</b> Correlation threshold, portfolio volatility ceiling (validated by RiskLayer)</li>
 * <li><b>Trading Rules:</b> Commissions, fraction allowance, cash buffer</li>
 * <li><b>Asset Restrictions:</b> Frozen assets that won't be traded</li>
 * <li><b>Monitoring:</b> Evaluation frequency (used by RebalancingTrigger)</li>
 * <li><b>Strict Cash Mode:</b> Always enabled - buy orders scaled down to prevent negative cash</li>
 * </ul>
 */
public class RebalancingConfig
{
    
    public enum Strategy
    {
        MIN_VOLATILITY,
        MAX_SHARPE
    }

    private Strategy strategy = Strategy.MAX_SHARPE;
    
    private double maxWeightPerAsset = 0.50; // default 50% max weight per asset
    private double minWeightPerAsset = 0.00; // default 0% min weight
    
    private double correlationThreshold = 0.85; // highly correlated assets are penalized or removed
    private double inertiaTolerance = 0.02; // 2% drift tolerance before acting
    private double maxPortfolioVolatility = 1.0; // allowed portfolio volatility ceiling
    
    private int monitoringIntervalMinutes = 1440; // evaluation frequency (1 day = 1440 minutes)
    private double newCashAmount = 0.0; // user-provided capital injection
    
    private double commissionFixed = 0.0;
    private double commissionVariable = 0.0; // as a decimal, e.g., 0.01 for 1%
    
    private boolean allowFractions = false; // whether fractional units are
                                            // allowed in orders
    private double cashBuffer = 0.0; // absolute cash amount to leave out of the market

    private final Set<String> frozenAssets = new HashSet<>();

    public Strategy getStrategy()
    {
        return strategy;
    }

    public void setStrategy(Strategy strategy)
    {
        if (strategy == null)
            throw new IllegalArgumentException("Strategy must not be null");
        this.strategy = strategy;
    }

    public double getMaxWeightPerAsset()
    {
        return maxWeightPerAsset;
    }

    public void setMaxWeightPerAsset(double maxWeightPerAsset)
    {
        if (Double.isNaN(maxWeightPerAsset))
            throw new IllegalArgumentException("maxWeightPerAsset must be a valid number");
        if (maxWeightPerAsset < 0.0 || maxWeightPerAsset > 1.0)
            throw new IllegalArgumentException("maxWeightPerAsset must be between 0.0 and 1.0");
        if (maxWeightPerAsset < this.minWeightPerAsset)
            throw new IllegalArgumentException("maxWeightPerAsset must be greater than or equal to minWeightPerAsset");
        this.maxWeightPerAsset = maxWeightPerAsset;
    }

    public double getMinWeightPerAsset()
    {
        return minWeightPerAsset;
    }

    public void setMinWeightPerAsset(double minWeightPerAsset)
    {
        if (Double.isNaN(minWeightPerAsset))
            throw new IllegalArgumentException("minWeightPerAsset must be a valid number");
        if (minWeightPerAsset < 0.0 || minWeightPerAsset > 1.0)
            throw new IllegalArgumentException("minWeightPerAsset must be between 0.0 and 1.0");
        if (minWeightPerAsset > this.maxWeightPerAsset)
            throw new IllegalArgumentException("minWeightPerAsset must be less than or equal to maxWeightPerAsset");
        this.minWeightPerAsset = minWeightPerAsset;
    }

    /**
     * Updates both weight limits at once to avoid validation conflicts between 
     * individual setters.
     */
    public void setWeightLimits(double min, double max)
    {
        if (Double.isNaN(min) || Double.isNaN(max))
            throw new IllegalArgumentException("Weights must be valid numbers");
        if (min < 0.0 || min > 1.0 || max < 0.0 || max > 1.0)
        {
            throw new IllegalArgumentException("Weights must be between 0.0 and 1.0");
        }
        if (min > max)
        {
            throw new IllegalArgumentException("Min weight cannot be greater than max weight");
        }
        
        this.minWeightPerAsset = min;
        this.maxWeightPerAsset = max;
    }

    public double getCorrelationThreshold()
    {
        return correlationThreshold;
    }

    public void setCorrelationThreshold(double correlationThreshold)
    {
        if (Double.isNaN(correlationThreshold))
            throw new IllegalArgumentException("correlationThreshold must be a valid number");
        if (correlationThreshold < 0.0 || correlationThreshold > 1.0)
            throw new IllegalArgumentException("correlationThreshold must be between 0.0 and 1.0");
        this.correlationThreshold = correlationThreshold;
    }

    public double getMaxPortfolioVolatility()
    {
        return maxPortfolioVolatility;
    }

    public void setMaxPortfolioVolatility(double maxPortfolioVolatility)
    {
        if (Double.isNaN(maxPortfolioVolatility))
            throw new IllegalArgumentException("maxPortfolioVolatility must be a valid number");
        if (maxPortfolioVolatility < 0.0 || maxPortfolioVolatility > 1.0)
            throw new IllegalArgumentException("maxPortfolioVolatility must be between 0.0 and 1.0");
        this.maxPortfolioVolatility = maxPortfolioVolatility;
    }

    public int getMonitoringIntervalMinutes()
    {
        return monitoringIntervalMinutes;
    }

    public void setMonitoringIntervalMinutes(int monitoringIntervalMinutes)
    {
        if (monitoringIntervalMinutes <= 0)
            throw new IllegalArgumentException("monitoringIntervalMinutes must be greater than zero");
        this.monitoringIntervalMinutes = monitoringIntervalMinutes;
    }

    public double getNewCashAmount()
    {
        return newCashAmount;
    }

    public void setNewCashAmount(double newCashAmount)
    {
        if (Double.isNaN(newCashAmount))
            throw new IllegalArgumentException("newCashAmount must be a valid number");
        if (newCashAmount < 0.0)
            throw new IllegalArgumentException("newCashAmount must not be negative");
        this.newCashAmount = newCashAmount;
    }

    public double getInertiaTolerance()
    {
        return inertiaTolerance;
    }

    public void setInertiaTolerance(double inertiaTolerance)
    {
        if (Double.isNaN(inertiaTolerance))
            throw new IllegalArgumentException("inertiaTolerance must be a valid number");
        if (inertiaTolerance < 0.0 || inertiaTolerance > 1.0)
            throw new IllegalArgumentException("inertiaTolerance must be between 0.0 and 1.0");
        this.inertiaTolerance = inertiaTolerance;
    }

    public double getCommissionFixed()
    {
        return commissionFixed;
    }

    public void setCommissionFixed(double commissionFixed)
    {
        if (Double.isNaN(commissionFixed))
            throw new IllegalArgumentException("commissionFixed must be a valid number");
        if (commissionFixed < 0.0)
            throw new IllegalArgumentException("commissionFixed must not be negative");
        this.commissionFixed = commissionFixed;
    }

    public double getCommissionVariable()
    {
        return commissionVariable;
    }

    public void setCommissionVariable(double commissionVariable)
    {
        if (Double.isNaN(commissionVariable))
            throw new IllegalArgumentException("commissionVariable must be a valid number");
        if (commissionVariable < 0.0 || commissionVariable > 1.0)
            throw new IllegalArgumentException("commissionVariable must be between 0.0 and 1.0");
        this.commissionVariable = commissionVariable;
    }

    public boolean isAllowFractions()
    {
        return allowFractions;
    }

    public void setAllowFractions(boolean allowFractions)
    {
        this.allowFractions = allowFractions;
    }

    public Set<String> getFrozenAssets()
    {
        return Collections.unmodifiableSet(frozenAssets);
    }

    public void addFrozenAsset(String assetId)
    {
        if (assetId == null || assetId.isBlank())
            throw new IllegalArgumentException("assetId must not be null or blank");
        frozenAssets.add(assetId.trim());
    }

    public void removeFrozenAsset(String assetId)
    {
        if (assetId != null)
            frozenAssets.remove(assetId.trim());
    }

    public boolean isAssetFrozen(String assetId) 
    {
        return assetId != null && frozenAssets.contains(assetId.trim());
    }

    public double getCashBuffer()
    {
        return cashBuffer;
    }

    public void setCashBuffer(double cashBuffer)
    {
        if (Double.isNaN(cashBuffer))
            throw new IllegalArgumentException("cashBuffer must be a valid number");
        if (cashBuffer < 0.0)
            throw new IllegalArgumentException("cashBuffer must not be negative");
        this.cashBuffer = cashBuffer;
    }
}

