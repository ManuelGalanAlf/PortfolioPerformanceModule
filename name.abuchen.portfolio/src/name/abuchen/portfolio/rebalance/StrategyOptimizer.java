package name.abuchen.portfolio.rebalance;

import java.util.List;

import name.abuchen.portfolio.math.Covariance;
import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * Strategy Pattern interface for portfolio optimization algorithms.
 * <p>
 * Each implementation encapsulates a different optimization objective
 * (e.g., minimize volatility, maximize Sharpe ratio). The {@link RiskLayer}
 * delegates the weight calculation to the active strategy, allowing the
 * algorithm to be swapped at runtime without modifying the pipeline logic.
 *
 * @see MinVolatilityStrategy
 * @see MaxSharpeStrategy
 */
public interface StrategyOptimizer
{
    /**
     * Calculates the optimal target weights for the given assets.
     *
     * @param assets
     *                       list of asset performance indices
     * @param covariance
     *                       the precomputed covariance engine
     * @param config
     *                       user-defined constraints and parameters
     * @return array of target weights (must sum to 1.0), one per asset
     */
    double[] optimize(List<PerformanceIndex> assets, Covariance covariance, RebalancingConfig config);
}
