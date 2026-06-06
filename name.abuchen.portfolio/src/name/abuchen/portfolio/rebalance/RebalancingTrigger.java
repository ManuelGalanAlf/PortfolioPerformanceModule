package name.abuchen.portfolio.rebalance;

/**
 * Smart Trigger system that determines when the RebalancingEngine should run.
 * <p>
 * Three trigger types prevent the engine from running constantly:
 * <ul>
 * <li><b>Drift Trigger:</b> fires when any asset's current weight deviates
 *     from its target by more than the configured tolerance.</li>
 * <li><b>Risk Trigger:</b> fires when the portfolio's annualized volatility
 *     or VaR exceeds a user-defined limit.</li>
 * <li><b>Cash-in Trigger:</b> fires when the user manually injects new capital
 *     that needs to be allocated.</li>
 * </ul>
 */
public class RebalancingTrigger
{
    private long lastEvaluationTime = 0;
    private double lastNewCashAmount = -1.0;
    private double lastCashBalance = -1.0;
    private int lastConfigHash = 0;
    private boolean firstExecution = true;

    /**
     * The type of event that triggered the rebalancing.
     */
    public enum TriggerType
    {
        /** The user injected new capital. */
        CASH_IN,
        /** Sufficient time has passed since last evaluation. */
        TIME_BASED,
        /** The rebalancing configuration was modified by the user. */
        CONFIG_CHANGED,
        /** No trigger condition is met. */
        NONE
    }

    
    /**
     * Evaluates whether sufficient time has passed since the last evaluation.
     * This implements time-based triggering for periodic rebalancing checks.
     *
     * @param config
     *            the rebalancing configuration containing monitoring interval
     * @return TIME_BASED if interval has elapsed, NONE otherwise
     */
    public TriggerType evaluateTimeBased(RebalancingConfig config)
    {
        if (config == null)
            return TriggerType.NONE;

        long currentTime = System.currentTimeMillis();
        long intervalMillis = config.getMonitoringIntervalMinutes() * 60 * 1000L; // convert minutes to milliseconds

        if (currentTime - lastEvaluationTime >= intervalMillis)
        {
            return TriggerType.TIME_BASED;
        }

        return TriggerType.NONE;
    }

    /**
     * Updates the timestamp of the last evaluation to the current time.
     * Call this after performing a rebalancing evaluation.
     */
    public void updateLastEvaluationTime()
    {
        lastEvaluationTime = System.currentTimeMillis();
    }

    /**
     * Evaluates whether capital has been injected or the cash account balance
     * has changed since the last evaluation. This implements the Cash-in
     * Trigger logic.
     *
     * @param context
     *            the shared rebalancing context containing data and
     *            configuration
     * @return CASH_IN if a significant change in cash is detected, NONE
     *         otherwise
     * @throws IllegalArgumentException
     *             if the provided context is null
     */
    public TriggerType evaluateMoneyChanged(RebalancingContext context)
    {
        if (context == null)
            throw new IllegalArgumentException("Context must not be null");

        RebalancingConfig config = context.getConfig();
        if (config == null)
            return TriggerType.NONE;

        // Detect changes in user-injected capital
        double currentNewCash = config.getNewCashAmount();
        if (lastNewCashAmount >= 0.0 && Math.abs(currentNewCash - lastNewCashAmount) > 0.01)
        {
            lastNewCashAmount = currentNewCash;
            return TriggerType.CASH_IN;
        }
        lastNewCashAmount = currentNewCash;

        // Detect changes in the cash account balance
        double currentCash = context.getCashAccountBalance();
        if (lastCashBalance >= 0.0 && Math.abs(currentCash - lastCashBalance) > 0.01)
        {
            lastCashBalance = currentCash;
            return TriggerType.CASH_IN;
        }

        lastCashBalance = currentCash;
        return TriggerType.NONE;
    }

    /**
     * Detects whether the rebalancing configuration has changed since the
     * last evaluation by comparing a hash of its relevant fields.
     *
     * @param config the current rebalancing configuration
     * @return CONFIG_CHANGED if the configuration changed or is first execution, NONE otherwise
     */
    public TriggerType evaluateConfigChanged(RebalancingConfig config)
    {
        if (config == null)
            return TriggerType.NONE;

        int currentHash = buildConfigHash(config);

        if (firstExecution)
        {
            // Always fire to initialize the engine
            firstExecution = false;
            lastConfigHash = currentHash;
            return TriggerType.CONFIG_CHANGED;
        }

        if (currentHash != lastConfigHash)
        {
            lastConfigHash = currentHash;
            return TriggerType.CONFIG_CHANGED;
        }

        return TriggerType.NONE;

    }

    /**
     * Builds a hash from all user-configurable fields of the config.
     * Only fields that affect the pipeline output are included.
     */
    private int buildConfigHash(RebalancingConfig config)
    {
        return java.util.Objects.hash(
            config.getStrategy(),
            config.getMaxWeightPerAsset(),
            config.getMinWeightPerAsset(),
            config.getCorrelationThreshold(),
            config.getInertiaTolerance(),
            config.getMaxPortfolioVolatility(),
            config.getMonitoringIntervalMinutes(),
            config.getNewCashAmount(),
            config.getCommissionFixed(),
            config.getCommissionVariable(),
            config.isAllowFractions(),
            config.getCashBuffer(),
            config.getFrozenAssets()
        );
    }
}
