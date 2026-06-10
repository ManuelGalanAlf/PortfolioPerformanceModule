package name.abuchen.portfolio.rebalance;

public abstract class AbstractLayer implements ILayer
{
    /**
     * Ordered weight candidates from most to least preferred.
     * Return null to opt out (e.g. layers that don't consume weights).
     */
    protected abstract double[][] weightCandidates(RebalancingContext context);

    /**
     * Human-readable labels for each candidate, used in fallback log messages.
     */
    protected abstract String[] weightCandidateLabels();

    /**
     * Resolves the first valid weight array from the fallback chain.
     * Logs a warning if a fallback is used. Aborts context if none are valid.
     */
    protected final double[] resolveWeightsWithFallback(RebalancingContext context)
    {
        double[][] candidates = weightCandidates(context);
        if (candidates == null)
            return null;

        String[] labels = weightCandidateLabels();
        int n = context.getAssets().size();
        String layerName = getClass().getSimpleName();

        for (int i = 0; i < candidates.length; i++)
        {
            double[] w = candidates[i];
            String label = (labels != null && i < labels.length) ? labels[i] : "candidate[" + i + "]";

            if (areWeightsValid(w, n))
            {
                if (i > 0)
                    context.getLogger().log(layerName,
                        String.format("FALLBACK: '%s' unavailable or invalid. Using '%s'.",
                            labels[0], label));
                return w;
            }
        }

        context.getLogger().log(layerName, "ABORT: No valid input weights found in fallback chain.");
        context.setAborted(true);
        return null;
    }

    /**
     * Validates that a weight array is usable:
     * not null, correct size, all finite and non-negative, sum within [0.99, 1.01].
     */
    protected final boolean areWeightsValid(double[] weights, int expectedSize)
    {
        if (weights == null || weights.length != expectedSize)
            return false;

        double sum = 0.0;
        for (double w : weights)
        {
            if (!Double.isFinite(w) || w < 0.0)
                return false;
            sum += w;
        }

        return sum >= 0.99 && sum <= 1.01;
    }
}