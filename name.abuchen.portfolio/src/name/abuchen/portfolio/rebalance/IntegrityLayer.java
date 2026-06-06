package name.abuchen.portfolio.rebalance;

import java.util.List;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * Layer 0: Integrity Validation.
 * <p>
 * Validates that the input data is sufficient and correct before the
 * pipeline proceeds. If any asset fails validation, the pipeline is
 * aborted to prevent downstream errors.
 * <p>
 * Checks performed:
 * <ul>
 * <li>Assets list is not null or empty</li>
 * <li>Each asset has a valid PerformanceIndex with return data</li>
 * <li>Minimum number of historical observations (≥30 days)</li>
 * <li>No NaN or infinite values in the return series</li>
 * </ul>
 */
public class IntegrityLayer implements ILayer
{
    private static final int MIN_OBSERVATIONS = 30;

    @Override
    public void process(RebalancingContext context)
    {
        List<PerformanceIndex> assets = context.getAssets();

        // 1. Assets list must not be null or empty
        if (!validateAssetsNotNullOrEmpty(context, assets))
        {
            return;
        }

        context.getLogger().log("IntegrityLayer",
                        String.format("Validating %d assets...", assets.size()));

        // 2. Validate each asset individually
        for (int i = 0; i < assets.size(); i++)
        {
            if (!validateAsset(context, assets.get(i), i))
            {
                return;
            }
        }

        context.getLogger().log("IntegrityLayer",
                        String.format("All %d assets passed integrity checks.", assets.size()));

        // 3. Log frozen assets for auditability
        logFrozenAssets(context, assets, context.getConfig());
    }

    /**
     * Validates that the list of assets is not null or empty.
     * If the list is invalid, aborts the execution of the rebalancing engine.
     *
     * @param context The shared rebalancing context.
     * @param assets The list of assets to validate.
     * @return true if the list is valid and has elements; false otherwise.
     */
    private boolean validateAssetsNotNullOrEmpty(RebalancingContext context, List<PerformanceIndex> assets)
    {
        if (assets == null || assets.isEmpty())
        {
            context.getLogger().log("IntegrityLayer", "ABORT: Assets list is null or empty.");
            context.setAborted(true);
            return false;
        }
        return true;
    }

    /**
     * Validates a single financial asset from the portfolio.
     * Checks that the asset is not null, has a return series,
     * exceeds the minimum historical observation threshold, and contains no corrupt values (NaN/Infinity).
     *
     * @param context The shared rebalancing context.
     * @param asset The asset to validate.
     * @param index The index of the asset in the original list.
     * @return true if the asset passes all integrity checks; false otherwise.
     */
    private boolean validateAsset(RebalancingContext context, PerformanceIndex asset, int index)
    {
        if (asset == null)
        {
            context.getLogger().log("IntegrityLayer",
                            String.format("ABORT: Asset at index %d is null.", index));
            context.setAborted(true);
            return false;
        }

        double[] delta = asset.getDeltaPercentage();

        if (delta == null)
        {
            context.getLogger().log("IntegrityLayer",
                            String.format("ABORT: Asset at index %d has no return series.", index));
            context.setAborted(true);
            return false;
        }

        // Check: Minimum historical data
        if (delta.length < MIN_OBSERVATIONS)
        {
            context.getLogger().log("IntegrityLayer",
                            String.format("ABORT: Asset at index %d has only %d observations (minimum: %d).",
                                            index, delta.length, MIN_OBSERVATIONS));
            context.setAborted(true);
            return false;
        }

        // Check: No corrupt values
        for (int t = 0; t < delta.length; t++)
        {
            if (!Double.isFinite(delta[t]))
            {
                context.getLogger().log("IntegrityLayer",
                                String.format("ABORT: Asset %d has NaN/Infinite value at index %d.", index, t));
                context.setAborted(true);
                return false;
            }
        }

        return true;
    }

    /**
     * Identifies and logs frozen assets (blacklist) in the audit trail.
     * These assets will be excluded from optimization by downstream layers,
     * preserving their exact current weight in the portfolio.
     *
     * @param context The shared rebalancing context.
     * @param assets The full list of assets.
     * @param config The current rebalancing configuration.
     */
    private void logFrozenAssets(RebalancingContext context, List<PerformanceIndex> assets, RebalancingConfig config)
    {
        if (config != null && !config.getFrozenAssets().isEmpty())
        {
            int frozenCount = 0;
            for (int i = 0; i < assets.size(); i++)
            {
                String id = context.getAssetIdentifier(i);
                if (id != null && config.isAssetFrozen(id))
                {
                    context.getLogger().log("IntegrityLayer",
                                    String.format("Asset %d (%s) is FROZEN — will be excluded from optimization.", i, id));
                    frozenCount++;
                }
            }
            if (frozenCount > 0)
            {
                context.getLogger().log("IntegrityLayer",
                                String.format("%d frozen asset(s) detected. They will be preserved by downstream layers.",
                                                frozenCount));
            }
        }
    }
}
