package name.abuchen.portfolio.rebalance;

import java.util.List;

import name.abuchen.portfolio.math.AdvancedRiskMetrics;
import name.abuchen.portfolio.math.Covariance;
import name.abuchen.portfolio.math.PortfolioAnalytics;
import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * Layer 1: Risk-Based Weight Calculation.
 * <p>
 * The mathematical brain of the pipeline. Delegates to a {@link StrategyOptimizer}
 * (injected via constructor) to compute ideal target weights. This is the core
 * application of the <b>Strategy Pattern</b>: the optimization algorithm can be
 * swapped at runtime without modifying any pipeline logic.
 * <p>
 * This layer also computes and stores the correlation matrix in the context
 * for downstream use by the {@link RedundancyLayer}.
 *
 * @see StrategyOptimizer
 * @see MinVolatilityStrategy
 * @see MaxSharpeStrategy
 */
public class RiskLayer extends AbstractLayer
{
    private final StrategyOptimizer optimizer;

    /**
     * Constructs a RiskLayer with an injected optimization strategy.
     * <p>
     * This constructor is preserved for tests or explicit overrides.
     * When no optimizer is injected, the layer chooses the strategy based on
     * {@link RebalancingConfig#getStrategy()}.
     *
     * @param optimizer the strategy to use for weight calculation
     */
    public RiskLayer(StrategyOptimizer optimizer)
    {
        if (optimizer == null)
            throw new IllegalArgumentException("StrategyOptimizer must not be null");
        this.optimizer = optimizer;
    }

    /**
     * Constructs a RiskLayer that selects the optimizer based on the configured strategy.
     */
    public RiskLayer()
    {
        this.optimizer = null;
    }

    @Override
    public void process(RebalancingContext context)
    {
        List<PerformanceIndex> assets = context.getAssets();
        RebalancingConfig config = context.getConfig();
        StrategyOptimizer selectedOptimizer = this.optimizer != null ? this.optimizer : createOptimizer(config);

        context.getLogger().log("RiskLayer",
                        String.format("Computing target weights using %s strategy...",
                                        selectedOptimizer.getClass().getSimpleName()));

        // 1. Log individual asset volatilities and expected returns
        logIndividualAssetMetrics(context, assets);

        // 2. Build covariance and store correlation matrix for downstream layers
        Covariance covariance = calculateCovarianceAndSetCorrelation(context, assets);

        // 3. Optimize target weights using the selected strategy
        double[] targetWeights = selectedOptimizer.optimize(assets, covariance, config);

        if (targetWeights == null || targetWeights.length != assets.size())
        {
            context.getLogger().log("RiskLayer", "ABORT: Optimizer returned invalid weights.");
            context.setAborted(true);
            return;
        }

        // 5. Evaluate portfolio volatility constraint
        if (!checkVolatilityConstraint(context, covariance, assets, targetWeights, config))
        {
            return;
        }

        // 6. Save and log final computed target weights
        context.setTargetWeights(targetWeights);
        logTargetWeights(context, targetWeights);
    }

    /**
     * Computes and logs individual asset risk metrics (annualized volatility
     * and annualized expected return) for transparency in execution.
     *
     * @param context The shared rebalancing context.
     * @param assets The list of portfolio assets.
     */
    private void logIndividualAssetMetrics(RebalancingContext context, List<PerformanceIndex> assets)
    {
        for (int i = 0; i < assets.size(); i++)
        {
            PerformanceIndex asset = assets.get(i);
            String assetName = context.getAssetIdentifier(i);
            double[] delta = asset.getDeltaPercentage();
            double vol = 0.0;
            double expReturn = 0.0;
            if (delta != null && delta.length >= 2)
            {
                vol = AdvancedRiskMetrics.annualizedStandardDeviation(delta);
                expReturn = AdvancedRiskMetrics.annualizedExpectedReturn(asset);
            }
            context.getLogger().log("RiskLayer", String.format("Asset '%s': history length = %d, annualized volatility = %.4f%%, expected return = %.4f%%", 
                assetName, (delta != null ? delta.length : 0), vol * 100, expReturn * 100));
        }
    }

    /**
     * Computes the covariance engine from asset returns and stores the precomputed
     * correlation matrix in the dynamic context for downstream layers.
     *
     * @param context The shared rebalancing context.
     * @param assets The list of portfolio assets.
     * @return The populated Covariance calculation object.
     */
    private Covariance calculateCovarianceAndSetCorrelation(RebalancingContext context, List<PerformanceIndex> assets)
    {
        Covariance covariance = new Covariance(assets);
        covariance.calculate();
        context.setCorrelationMatrix(covariance.getCorrelationMatrix());
        return covariance;
    }

    /**
     * Compares the total projected portfolio volatility of the optimized weights with the
     * user-defined maximum volatility limit. If it is exceeded, aborts the pipeline with
     * an error message explaining that no valid rebalance layout could be found within that risk limit.
     *
     * @param context The shared rebalancing context.
     * @param covariance The portfolio covariance object.
     * @param assets The list of portfolio assets.
     * @param targetWeights The target weights proposed by the optimizer strategy.
     * @param config The current rebalancing configuration.
     * @return true if the volatility is within the allowed limit; false if it exceeds the limit (aborts).
     */
    private boolean checkVolatilityConstraint(RebalancingContext context, Covariance covariance,
                    List<PerformanceIndex> assets, double[] targetWeights, RebalancingConfig config)
    {
        PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);
        double portfolioVolatility = analytics.getPortfolioStandardDeviationAnnualized(targetWeights);
        double maxAllowedVolatility = config.getMaxPortfolioVolatility();

        if (portfolioVolatility > maxAllowedVolatility)
        {
            context.getLogger().log("RiskLayer",
                            String.format("ABORT: Target portfolio volatility (%.4f) exceeds the maximum allowed volatility limit (%.4f). " +
                                          "Could not find a valid rebalance layout matching this risk profile.",
                                          portfolioVolatility, maxAllowedVolatility));
            context.setAborted(true);
            return false;
        }
        return true;
    }

    /**
     * Logs the final array of computed theoretical target weights in the audit log.
     *
     * @param context The shared rebalancing context.
     * @param targetWeights The array of calculated target weights.
     */
    private void logTargetWeights(RebalancingContext context, double[] targetWeights)
    {
        StringBuilder sb = new StringBuilder("Target weights: [");
        for (int i = 0; i < targetWeights.length; i++)
        {
            if (i > 0)
                sb.append(", ");
            sb.append(String.format("%.4f", targetWeights[i]));
        }
        sb.append("]");
        context.getLogger().log("RiskLayer", sb.toString());
    }

    /**
     * Factory method that instantiates the optimizer strategy selected by the user
     * in the configuration (Maximize Sharpe or Minimize Volatility).
     *
     * @param config The rebalancing configuration.
     * @return The corresponding StrategyOptimizer instance.
     */
    private StrategyOptimizer createOptimizer(RebalancingConfig config)
    {
        if (config == null)
            return new MaxSharpeStrategy();

        switch (config.getStrategy())
        {
            case MIN_VOLATILITY:
                return new MinVolatilityStrategy();
            case MAX_SHARPE:
            default:
                return new MaxSharpeStrategy();
        }
    }

    @Override
    protected double[][] weightCandidates(RebalancingContext context)
    {
        return null; // RiskLayer does not provide fallback candidates and it's the source of target weights.
    }

    @Override
    protected String[] weightCandidateLabels()
    {
        return null;
    }
}
