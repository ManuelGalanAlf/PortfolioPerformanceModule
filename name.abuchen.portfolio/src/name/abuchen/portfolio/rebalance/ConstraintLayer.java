package name.abuchen.portfolio.rebalance;

/**
 * Layer 2: Constraint Enforcement.
 * <p>
 * Applies user-defined limits (max/min weight per asset) to the target
 * weights produced by the {@link RiskLayer}. When an asset exceeds its
 * maximum, the excess is redistributed proportionally among uncapped assets.
 * <p>
 * The algorithm iterates until all weights are within bounds and the sum
 * equals 1.0. This is the mathematically most delicate redistribution step.
 */
public class ConstraintLayer extends AbstractLayer
{
    private static final int MAX_ITERATIONS = 100;
    private static final double WEIGHT_TOLERANCE = 1e-9;

    @Override
    public void process(RebalancingContext context)
    {
        // 1. Validate inputs
        double[] weights = resolveWeightsWithFallback(context);
        if (weights == null)
            return;

        double maxWeight = context.getConfig().getMaxWeightPerAsset();
        double minWeight = context.getConfig().getMinWeightPerAsset();
        int n = weights.length;

        // 2. Validate feasibility of constraints
        if (!validateConstraintsFeasibility(context, n, minWeight, maxWeight))
        {
            return;
        }

        context.getLogger().log("ConstraintLayer",
                        String.format("Applying constraints: maxWeight=%.4f, minWeight=%.4f",
                                        maxWeight, minWeight));

        double[] constrained = weights.clone();

        // 3. Perform iterative redistribution to enforce bounds and keep sum = 1.0
        redistributeWeightsIteratively(context, constrained, minWeight, maxWeight);

        // 4. Safety normalization to absorb floating point rounding errors
        normalizeConstrainedWeights(constrained);

        // 5. Save and log result
        context.setConstrainedWeights(constrained);
        logConstrainedWeights(context, constrained);
    }
 
    /**
     * Verifies the mathematical feasibility of the user-defined constraints.
     * If the sum of the minimum weights exceeds 1.0, or if the sum of the maximum weights
     * is less than 1.0, the optimization problem has no feasible mathematical solution and is aborted.
     *
     * @param context The shared rebalancing context.
     * @param n The total number of financial assets in the portfolio.
     * @param minWeight The configured minimum allowed weight per asset.
     * @param maxWeight The configured maximum allowed weight per asset.
     * @return true if the constraints are feasible; false if they are mathematically unsolvable.
     */
    private boolean validateConstraintsFeasibility(RebalancingContext context, int n, double minWeight, double maxWeight)
    {
        if (n * minWeight > 1.0 + WEIGHT_TOLERANCE)
        {
            context.getLogger().log("ConstraintLayer",
                            String.format("ABORT: Infeasible constraints — The sum of minimum weights (%d * %.4f = %.4f) exceeds 100%%. " +
                                          "Please increase the number of assets or lower the minimum weight limit.",
                                          n, minWeight, n * minWeight));
            context.setAborted(true);
            return false;
        }
        if (n * maxWeight < 1.0 - WEIGHT_TOLERANCE)
        {
            context.getLogger().log("ConstraintLayer",
                            String.format("ABORT: Infeasible constraints — The sum of maximum weights (%d * %.4f = %.4f) is less than 100%%. " +
                                          "Please increase the number of assets or increase the maximum weight limit.",
                                          n, maxWeight, n * maxWeight));
            context.setAborted(true);
            return false;
        }
        return true;
    }

    /**
     * Main iterative algorithm responsible for adjusting asset weights
     * so that they strictly comply with the configured bounds [minWeight, maxWeight]
     * while maintaining a total sum equal to 1.0 (100%).
     *
     * @param context The shared rebalancing context.
     * @param constrained The array of weights to adjust (by reference).
     * @param minWeight The minimum weight per asset.
     * @param maxWeight The maximum weight per asset.
     */
    private void redistributeWeightsIteratively(RebalancingContext context, double[] constrained, double minWeight, double maxWeight)
    {
        int n = constrained.length;
        for (int iter = 0; iter < MAX_ITERATIONS; iter++)
        {
            // Step A: Clamp weights to configured hard limits [minWeight, maxWeight]
            applyHardLimits(constrained, minWeight, maxWeight);

            // Step B: Calculate total sum and residual deviation from 1.0
            double sum = 0.0;
            for (double w : constrained)
                sum += w;

            double diff = 1.0 - sum;
            if (Math.abs(diff) < WEIGHT_TOLERANCE)
                break;

            // Step C: Identify assets that have room to absorb the discrepancy
            boolean[] canAdjust = new boolean[n];
            int adjustableCount = 0;
            double adjustableWeightSum = 0.0;

            for (int i = 0; i < n; i++)
            {
                boolean hasRoomAbove = diff > 0 && constrained[i] < maxWeight - WEIGHT_TOLERANCE;
                boolean hasRoomBelow = diff < 0 && constrained[i] > minWeight + WEIGHT_TOLERANCE;

                if (hasRoomAbove || hasRoomBelow)
                {
                    canAdjust[i] = true;
                    adjustableWeightSum += constrained[i];
                    adjustableCount++;
                }
            }

            // Stop if no assets have room to absorb the difference (feasible boundary reached)
            if (adjustableCount == 0)
            {
                context.getLogger().log("ConstraintLayer",
                                String.format("WARNING: No adjustable assets remain. Residual diff=%.6f", diff));
                break;
            }

            // Step D: Redistribute the difference proportionally among adjustable assets
            redistributeDiffProportionally(constrained, canAdjust, diff, adjustableWeightSum, adjustableCount);
        }
    }

    /**
     * Clamps all asset weights rigidly, ensuring they fall within the closed
     * interval [minWeight, maxWeight].
     *
     * @param constrained The array of weights to clamp.
     * @param minWeight The lower weight limit.
     * @param maxWeight The upper weight limit.
     */
    private void applyHardLimits(double[] constrained, double minWeight, double maxWeight)
    {
        for (int i = 0; i < constrained.length; i++)
        {
            constrained[i] = Math.max(minWeight, Math.min(maxWeight, constrained[i]));
        }
    }

    /**
     * Redistributes the residual deviation from 100% proportionally among
     * the portfolio assets that still have free margin to increase or decrease
     * their weights.
     *
     * @param constrained The array of weights being adjusted.
     * @param canAdjust Boolean mask indicating which assets are adjustable.
     * @param diff The residual difference to redistribute.
     * @param adjustableWeightSum The sum of the weights of the adjustable assets.
     * @param adjustableCount The count of adjustable assets.
     */
    private void redistributeDiffProportionally(double[] constrained, boolean[] canAdjust, double diff, double adjustableWeightSum, int adjustableCount)
    {
        for (int i = 0; i < constrained.length; i++)
        {
            if (canAdjust[i])
            {
                double share = (adjustableWeightSum > WEIGHT_TOLERANCE)
                                ? (constrained[i] / adjustableWeightSum)
                                : (1.0 / adjustableCount);
                constrained[i] += diff * share;
            }
        }
    }

    /**
     * Normalizes the resulting weights to sum exactly to 1.0.
     * This step eliminates any residual micro-deviations caused by double precision
     * arithmetic in the previous iterations.
     *
     * @param constrained The array of weights to normalize.
     */
    private void normalizeConstrainedWeights(double[] constrained)
    {
        double finalSum = 0.0;
        for (double w : constrained)
            finalSum += w;

        if (finalSum > WEIGHT_TOLERANCE && Math.abs(finalSum - 1.0) > WEIGHT_TOLERANCE)
        {
            for (int i = 0; i < constrained.length; i++)
                constrained[i] /= finalSum;
        }
    }

    /**
     * Logs the final array of constrained weights to the audit log.
     *
     * @param context The shared rebalancing context.
     * @param constrained The final constrained weights array.
     */
    private void logConstrainedWeights(RebalancingContext context, double[] constrained)
    {
        StringBuilder sb = new StringBuilder("Constrained weights: [");
        for (int i = 0; i < constrained.length; i++)
        {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.6f", constrained[i]));
        }
        sb.append("]");
        context.getLogger().log("ConstraintLayer", sb.toString());
    }

    @Override
    protected double[][] weightCandidates(RebalancingContext context)
    {
        return new double[][] {
            context.getTargetWeights(),
            context.getCurrentWeights()
        };
    }

    @Override
    protected String[] weightCandidateLabels()
    {
        return new String[] { "targetWeights", "currentWeights" };
    }
}
