package name.abuchen.portfolio.rebalance;

import java.util.List;

import name.abuchen.portfolio.math.AdvancedRiskMetrics;
import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * Layer 3: Redundancy Detection and Elimination.
 * <p>
 * Scans the correlation matrix (precomputed by {@link RiskLayer}) to find
 * pairs of assets whose correlation exceeds the user-defined threshold.
 * For each redundant pair, the asset with <b>higher volatility</b> is
 * eliminated and its weight is transferred to the more stable survivor.
 * <p>
 * This layer consumes the correlation matrix stored in the
 * {@link RebalancingContext} by the RiskLayer, demonstrating the
 * <b>Context Object</b> pattern: layers communicate only through the
 * shared context without direct dependencies.
 */
public class RedundancyLayer extends AbstractLayer
{
    @Override
    public void process(RebalancingContext context)
    {
        // 1. Validate inputs from context and correlation matrix
        double[] weights = resolveWeightsWithFallback(context);
        if (weights == null)
            return;
        
        double[][] correlation = context.getCorrelationMatrix();
        if (correlation == null)
        {
            context.getLogger().log("RedundancyLayer", "ABORT: Missing correlation matrix.");
            context.setAborted(true);
            return;
        }

        List<PerformanceIndex> assets = context.getAssets();
        double threshold = context.getConfig().getCorrelationThreshold();
        int n = weights.length;
        double[] adjusted = weights.clone();
        boolean[] eliminated = new boolean[n];

        // 2. Precompute annualized volatility for each asset (used as tie-breaker)
        double[] volatilities = computeVolatilities(assets, n);

        context.getLogger().log("RedundancyLayer",
                        String.format("Scanning for pairs with correlation > %.2f...", threshold));

        // 3. Scan correlation matrix and eliminate redundant assets
        eliminateRedundancies(context, correlation, adjusted, volatilities, threshold, eliminated);

        // 4. Save results back to context
        context.setRedundancyFilteredWeights(adjusted);

        // 5. Log redundancy summary
        logRedundancySummary(context, eliminated);
    } 

    /**
     * Scans the upper triangle of the correlation matrix searching for pairs
     * of assets whose mutual linear correlation exceeds the user-configured
     * threshold to resolve their redundancy.
     *
     * @param context The shared rebalancing context.
     * @param correlation The asset correlation matrix.
     * @param adjusted The weights array being adjusted (by reference).
     * @param volatilities The annualized individual volatilities of the assets.
     * @param threshold The correlation threshold limit.
     * @param eliminated Boolean mask to mark eliminated assets.
     */
    private void eliminateRedundancies(RebalancingContext context, double[][] correlation, double[] adjusted,
                    double[] volatilities, double threshold, boolean[] eliminated)
    {
        int n = adjusted.length;

        // Scan the upper triangle of the correlation matrix
        for (int i = 0; i < n; i++)
        {
            if (eliminated[i])
                continue;

            boolean isFrozen = isFrozenAsset(context, i);

            for (int j = i + 1; j < n; j++)
            {
                if (eliminated[j])
                    continue;

                if (i < correlation.length && j < correlation.length
                                && Math.abs(correlation[i][j]) > threshold)
                {
                    if (isFrozen || isFrozenAsset(context, j))
                    {
                        context.getLogger().log("RedundancyLayer", String.format(
                                        "Skipping redundant pair '%s' <-> '%s': one or both assets are frozen.",
                                        getAssetName(context, i), getAssetName(context, j)));
                        continue;
                    }
                    processRedundantPair(context, adjusted, volatilities, eliminated, i, j, correlation[i][j]);
                }
            }
        }
    }

    /**
     * Helper to get a user-friendly asset name.
     */
    private String getAssetName(RebalancingContext context, int idx)
    {
        String name = context.getAssetIdentifier(idx);
        return name != null ? name : "Asset " + idx;
    }

    /**
     * Resolves redundancy between a specific pair of highly correlated assets.
     * Eliminates the asset with the higher annualized volatility, transferring its
     * weight to the more stable survivor. If the survivor would exceed maxWeightPerAsset,
     * the excess is capped and distributed back to cash to avoid constraint violations.
     *
     * @param context The shared rebalancing context.
     * @param adjusted The weights array being adjusted.
     * @param volatilities The computed volatilities array.
     * @param eliminated The eliminated assets mask.
     * @param i The index of the first asset.
     * @param j The index of the second asset.
     * @param correlationValue The actual correlation value between both assets.
     */
    private void processRedundantPair(RebalancingContext context, double[] adjusted, double[] volatilities,
                boolean[] eliminated, int i, int j, double correlationValue)
    {
        String nameI = getAssetName(context, i);
        String nameJ = getAssetName(context, j);
        double threshold = context.getConfig().getCorrelationThreshold();

        context.getLogger().log("RedundancyLayer",
                        String.format("Redundant pair detected: '%s' <-> '%s' (corr=%.4f exceeds threshold %.4f).",
                                        nameI, nameJ, correlationValue, threshold));

        int loser = volatilities[i] > volatilities[j] ? i : j;
        int survivor = loser == i ? j : i;

        String nameLoser = getAssetName(context, loser);
        String nameSurvivor = getAssetName(context, survivor);

        context.getLogger().log("RedundancyLayer",
                        String.format("Eliminating '%s' (vol=%.4f, weight=%.4f), keeping '%s' (vol=%.4f).",
                                        nameLoser, volatilities[loser], adjusted[loser],
                                        nameSurvivor, volatilities[survivor]));

        double loserWeight = adjusted[loser];
        adjusted[survivor] += loserWeight;
        adjusted[loser] = 0.0;
        eliminated[loser] = true;

        double maxWeight = context.getConfig().getMaxWeightPerAsset();
        if (adjusted[survivor] > maxWeight)
        {
            double excess = adjusted[survivor] - maxWeight;
            adjusted[survivor] = maxWeight;

            double activeSum = 0.0;
            int activeCount = 0;
            for (int k = 0; k < adjusted.length; k++)
            {
                if (!eliminated[k] && k != survivor && adjusted[k] > 0.0)
                {
                    activeSum += adjusted[k];
                    activeCount++;
                }
            }

            if (activeCount > 0 && activeSum > 0.0)
            {
                for (int k = 0; k < adjusted.length; k++)
                {
                    if (!eliminated[k] && k != survivor && adjusted[k] > 0.0)
                    {
                        adjusted[k] += excess * (adjusted[k] / activeSum);
                    }
                }
                context.getLogger().log("RedundancyLayer",
                                String.format("Capping survivor '%s' to maxWeight (%.4f). Excess (%.4f) redistributed proportionally among %d active assets.",
                                                nameSurvivor, maxWeight, excess, activeCount));
            }
            else
            {
                context.getLogger().log("RedundancyLayer",
                                String.format("Capping survivor '%s' to maxWeight (%.4f). Excess (%.4f) redistributed to cash (no other active assets).",
                                                nameSurvivor, maxWeight, excess));
            }
        }
    }

    /**
     * Counts and logs the total number of redundant assets that have been
     * discarded during this run.
     *
     * @param context The shared rebalancing context.
     * @param eliminated The eliminated assets mask.
     */
    private void logRedundancySummary(RebalancingContext context, boolean[] eliminated)
    {
        int eliminatedCount = 0;
        for (boolean e : eliminated)
        {
            if (e)
                eliminatedCount++;
        }
        context.getLogger().log("RedundancyLayer",
                        String.format("Redundancy check complete: %d asset(s) eliminated.", eliminatedCount));
    }

    /**
     * Computes annualized volatility for each asset.
     * Returns a safe default (1.0) for assets with insufficient data.
     */
    private double[] computeVolatilities(List<PerformanceIndex> assets, int n)
    {
        double[] vols = new double[n];
        for (int i = 0; i < n; i++)
        {
            if (assets == null || i >= assets.size() || assets.get(i) == null)
            {
                vols[i] = 1.0;
                continue;
            }
            double[] delta = assets.get(i).getDeltaPercentage();
            if (delta == null || delta.length < 2)
            {
                vols[i] = 1.0;
                continue;
            }
            double annualizedVol = AdvancedRiskMetrics.annualizedStandardDeviation(delta);
            vols[i] = Double.isNaN(annualizedVol) ? 1.0 : annualizedVol;
        }
        return vols;
    }

    private boolean isFrozenAsset(RebalancingContext context, int index)
    {
        String identifier = context.getAssetIdentifier(index);
        return identifier != null && context.getConfig().isAssetFrozen(identifier);
    }

    @Override
    protected double[][] weightCandidates(RebalancingContext context)
    {
        return new double[][] {
            context.getConstrainedWeights(),
            context.getTargetWeights(),
            context.getCurrentWeights()
        };
    }

    @Override
    protected String[] weightCandidateLabels()
    {
        return new String[] { "constrainedWeights", "targetWeights", "currentWeights" };
    }
}
