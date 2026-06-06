package name.abuchen.portfolio.rebalance;

import java.util.List;

import name.abuchen.portfolio.math.AdvancedRiskMetrics;
import name.abuchen.portfolio.math.Covariance;
import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * Minimum Volatility optimization strategy.
 * <p>
 * Applies Risk Parity as the initial heuristic: each asset's weight is
 * inversely proportional to its individual annualized volatility.
 * <p>
 * Formula: w_i = (1 / σ_i) / Σ(1 / σ_j)
 * <p>
 * This gives higher weight to less volatile assets, producing the lowest
 * theoretical portfolio variance without requiring a full quadratic solver.
 *
 * @see StrategyOptimizer
 */
public class MinVolatilityStrategy implements StrategyOptimizer
{
    @Override
    public double[] optimize(List<PerformanceIndex> assets, Covariance covariance, RebalancingConfig config)
    {
        int n = assets.size();
        double[] weights = new double[n];

        if (n == 0)
            return weights;

        // Step 1: Calculate the inverse volatility for each asset
        double sumInverseVol = 0.0;
        double[] inverseVol = new double[n];

        for (int i = 0; i < n; i++)
        {
            PerformanceIndex asset = assets.get(i);
            if (asset == null)
                return equalWeights(n);

            double[] delta = asset.getDeltaPercentage();
            if (delta == null || delta.length < 2)
                return equalWeights(n);

            double annualizedVol = AdvancedRiskMetrics.annualizedStandardDeviation(delta);
            if (Double.isNaN(annualizedVol) || annualizedVol == 0.0)
                return equalWeights(n);

            inverseVol[i] = 1.0 / annualizedVol;
            sumInverseVol += inverseVol[i];
        }

        // Step 2: Normalize to ensure weights sum to 1.0
        if (sumInverseVol == 0.0)
            return equalWeights(n);

        for (int i = 0; i < n; i++)
        {
            weights[i] = inverseVol[i] / sumInverseVol;
        }

        return weights;
    }

    /**
     * Fallback: returns equal weights when volatility data is unavailable.
     */
    private double[] equalWeights(int n)
    {
        double[] weights = new double[n];
        double w = 1.0 / n;
        for (int i = 0; i < n; i++)
            weights[i] = w;
        return weights;
    }
}
