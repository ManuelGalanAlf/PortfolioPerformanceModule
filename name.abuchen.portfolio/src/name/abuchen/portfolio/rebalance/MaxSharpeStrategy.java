package name.abuchen.portfolio.rebalance;

import java.util.List;

import name.abuchen.portfolio.math.Covariance;
import name.abuchen.portfolio.math.PortfolioAnalytics;
import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * Maximum Sharpe Ratio optimization strategy.
 * <p>
 * Uses a Monte Carlo simulation approach: generates random weight
 * combinations and selects the one that maximizes the theoretical
 * Sharpe Ratio computed via {@link PortfolioAnalytics}.
 * <p>
 * This is the computationally intensive strategy that leverages the
 * full Markowitz framework (covariance matrix, expected returns).
 *
 * @see StrategyOptimizer
 * @see PortfolioAnalytics#getPortfolioSharpeRatio(double[], double)
 */
public class MaxSharpeStrategy implements StrategyOptimizer
{
    private static final int DEFAULT_SIMULATIONS = 50_000;
    private static final double DEFAULT_RISK_FREE_RATE = 0.02;

    private final int simulations;
    private final double riskFreeRate;

    public MaxSharpeStrategy()
    {
        this(DEFAULT_SIMULATIONS, DEFAULT_RISK_FREE_RATE);
    }

    public MaxSharpeStrategy(int simulations, double riskFreeRate)
    {
        this.simulations = simulations;
        this.riskFreeRate = riskFreeRate;
    }

    @Override
    public double[] optimize(List<PerformanceIndex> assets, Covariance covariance, RebalancingConfig config)
    {
        int n = assets.size();
        if (n == 0)
            return new double[0];

        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);

        double bestSharpe = Double.NEGATIVE_INFINITY;
        double[] bestWeights = equalWeights(n);

        // Fixed seed for reproducible (deterministic) results across multiple runs
        java.util.Random random = new java.util.Random(302);

        for (int sim = 0; sim < simulations; sim++)
        {
            double[] candidateWeights = generateRandomWeights(n, random);
            double sharpe = analytics.getPortfolioSharpeRatioAnnualized(candidateWeights, riskFreeRate);

            if (Double.isFinite(sharpe) && sharpe > bestSharpe)
            {
                bestSharpe = sharpe;
                bestWeights = candidateWeights;
            }
        }

        return bestWeights;
    }

    /**
     * Generates random weights that sum to 1.0 using the Dirichlet
     * distribution (breaking-stick method).
     */
    private double[] generateRandomWeights(int n, java.util.Random random)
    {
        double[] raw = new double[n];
        double sum = 0.0;

        for (int i = 0; i < n; i++)
        {
            // Exponential distribution: -log(U) where U is uniform(0,1)
            raw[i] = -Math.log(random.nextDouble());
            sum += raw[i];
        }

        for (int i = 0; i < n; i++)
        {
            raw[i] /= sum;
        }

        return raw;
    }

    private double[] equalWeights(int n)
    {
        double[] weights = new double[n];
        double w = 1.0 / n;
        for (int i = 0; i < n; i++)
            weights[i] = w;
        return weights;
    }
}
