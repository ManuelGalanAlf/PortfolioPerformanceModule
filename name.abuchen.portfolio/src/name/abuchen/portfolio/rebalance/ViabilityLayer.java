package name.abuchen.portfolio.rebalance;

import java.util.List;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * Layer 4: Viability and Economic Feasibility.
 * <p>
 * Converts theoretical weights into actionable {@link Order} objects,
 * applying real-world filters:
 * <ul>
 * <li><b>Inertia filter:</b> Ignores moves smaller than the drift tolerance</li>
 * <li><b>Lot sizing:</b> Truncates to whole units when fractions are not allowed</li>
 * <li><b>Commission filter:</b> Blocks trades where cost exceeds 2% of the value</li>
 * <li><b>Cash buffer:</b> Reserves a fixed amount outside the market</li>
 * <li><b>Sell-first ordering:</b> Sells are placed before buys to free capital</li>
 * </ul>
 */
public class ViabilityLayer extends AbstractLayer
{
    private static final double COMMISSION_THRESHOLD = 0.02;

    @Override
    public void process(RebalancingContext context)
    {
        double[] weights = resolveWeightsWithFallback(context);
        if (weights == null)
            return;

        double[] current = context.getCurrentWeights();

        if (current == null)
        {
            context.getLogger().log("ViabilityLayer",
                            "ABORT: currentWeights is null. IntegrityLayer must run before ViabilityLayer.");
            context.setAborted(true);
            return;
        }

        double totalValue = context.getTotalPortfolioValue();
        RebalancingConfig config = context.getConfig();
        double cashBuffer = config.getCashBuffer();
        double investableValue = totalValue + config.getNewCashAmount() - cashBuffer;

        if (investableValue <= 0.0)
        {
            context.getLogger().log("ViabilityLayer",
                            String.format("ABORT: Investable value is zero or negative after cash buffer (Total=%.2f, NewCash=%.2f, CashBuffer=%.2f, Investable=%.2f).",
                                            totalValue, config.getNewCashAmount(), cashBuffer, investableValue));
            context.setAborted(true);
            context.setFinalWeights(current.clone());
            return;
        }

        context.getLogger().log("ViabilityLayer",
                        String.format("Total value=%.2f, cashBuffer=%.2f, investable=%.2f",
                                        totalValue, cashBuffer, investableValue));

        double[] currentAdjusted = adjustCurrentWeightsForNewCash(current, totalValue, investableValue);

        // Calculate available cash for strict cash budgeting
        double startingCash = calculateStartingCash(context, config, cashBuffer);
        double[] availableCashRef = new double[] { startingCash };

        double[] finalWeights = weights.clone();

        // Pass 1: SELLS first (to free capital)
        processSells(context, context.getAssets(), config, investableValue, weights, currentAdjusted, finalWeights, availableCashRef);

        // Pass 2: BUYS
        processBuys(context, context.getAssets(), config, investableValue, weights, currentAdjusted, finalWeights, availableCashRef);

        // Redistribution of remaining residual cash
        distributeResidualCash(context, context.getAssets(), config, investableValue, weights, currentAdjusted,
                        finalWeights, availableCashRef);

        context.setFinalWeights(finalWeights);

        context.getLogger().log("ViabilityLayer",
                        String.format("Viability complete: %d orders generated.",
                                        context.getProposedOrders().size()));
    }


    /**
     * Adjusts the current weights taking into account the new cash injection.
     * This allows for accurate mathematical calculations of the real drifts
     * based on the updated total portfolio value.
     *
     * @param current The array of current weights.
     * @param totalValue The current net total value of the portfolio.
     * @param investableValue The total value to be invested in the market.
     * @return The array of current weights adjusted for the new capital.
     */
    private double[] adjustCurrentWeightsForNewCash(double[] current, double totalValue, double investableValue)
    {
        int n = current.length;
        double[] currentAdjusted = current.clone();
        if (investableValue > 0.0)
        {
            double newCashRatio = totalValue / investableValue;
            for (int i = 0; i < n; i++)
            {
                currentAdjusted[i] = current[i] * newCashRatio;
            }
        }
        return currentAdjusted;
    }

    private double calculateStartingCash(RebalancingContext context, RebalancingConfig config, double cashBuffer)
    {
        return context.getCashAccountBalance() + config.getNewCashAmount() - cashBuffer;
    }

    /**
     * Executes the first phase of rebalancing by processing SELL orders
     * to free up liquidity before proceeding with buys.
     * Applies the inertia filter to ignore non-significant drifts.
     *
     * @param context The shared rebalancing context.
     * @param assets The list of portfolio assets.
     * @param config The current rebalancing configuration.
     * @param investableValue The net total amount to invest in the market.
     * @param weights The recommended target weights.
     * @param currentAdjusted The current weights adjusted to net assets.
     * @param finalWeights The final actual weights array of the portfolio.
     * @param availableCashRef Pointer to the available cash in the ledger.
     */
    private void processSells(RebalancingContext context, List<PerformanceIndex> assets, 
                    RebalancingConfig config, double investableValue, double[] weights,
                    double[] currentAdjusted, double[] finalWeights, double[] availableCashRef)
    {
        int n = finalWeights.length;
        for (int i = 0; i < n; i++)
        {
            if (isFrozenAsset(context, i))
            {
                finalWeights[i] = currentAdjusted[i];
                context.getLogger().log("ViabilityLayer",
                                String.format("Asset %d is frozen; preserving current weight.", i));
                continue;
            }

            double drift = weights[i] - currentAdjusted[i];

            // Inertia filter: skip small moves EXCEPT when we are underfunded (availableCash < 0) and need to sell to raise cash
            if (Math.abs(drift) < config.getInertiaTolerance() && availableCashRef[0] >= 0.0)
            {
                finalWeights[i] = currentAdjusted[i];
                context.getLogger().log("ViabilityLayer",
                                String.format("Asset %d: drift %.4f below inertia tolerance %.4f, skipping.",
                                                i, Math.abs(drift), config.getInertiaTolerance()));
                continue;
            }

            if (drift < 0) // SELL
            {
                processOrder(context, assets, config, investableValue,
                                i, drift, Order.OrderSide.SELL, finalWeights, currentAdjusted, availableCashRef);
            }
        }
    }

    /**
     * Executes the second phase of rebalancing by processing BUY orders
     * to purchase new assets using the total available cash.
     *
     * @param context The shared rebalancing context.
     * @param assets The list of portfolio assets.
     * @param config The current rebalancing configuration.
     * @param investableValue The net total amount to invest in the market.
     * @param weights The recommended target weights.
     * @param currentAdjusted The current weights adjusted to net assets.
     * @param finalWeights The final actual weights array of the portfolio.
     * @param availableCashRef Pointer to the available cash in the ledger.
     */
    private void processBuys(RebalancingContext context, List<PerformanceIndex> assets, 
                    RebalancingConfig config, double investableValue, double[] weights,
                    double[] currentAdjusted, double[] finalWeights, double[] availableCashRef)
    {
        int n = finalWeights.length;
        for (int i = 0; i < n; i++)
        {
            if (isFrozenAsset(context, i))
                continue;

            double drift = weights[i] - currentAdjusted[i];

            if (Math.abs(drift) < config.getInertiaTolerance())
            {
                finalWeights[i] = currentAdjusted[i];

                context.getLogger().log("ViabilityLayer", String.format("Asset %d: drift %.4f below inertia tolerance %.4f, skipping.",
                    i, Math.abs(drift), config.getInertiaTolerance()));
                
                continue;
            }
                

            if (drift > 0) // BUY
            {
                processOrder(context, assets, config, investableValue,
                                i, drift, Order.OrderSide.BUY, finalWeights, currentAdjusted, availableCashRef);
            }
        }

        // Final cleanup: set very small weights to zero to avoid noise in the output
        for (int i = 0; i < finalWeights.length; i++)
        {
            if (Math.abs(finalWeights[i]) < 1e-6)
            {
                finalWeights[i] = 0.0;
            }
        }
    }

    /**
     * Creates and processes an individual transactional order for asset i.
     * Applies the strict cash budget, lot size rounding, and the 2% commission
     * fee filter. Also recalculates the final weights based on the actual trade
     * value executed.
     *
     * @param context The shared rebalancing context.
     * @param assets The list of portfolio assets.
     * @param config The current rebalancing configuration.
     * @param investableValue The net total amount to invest in the market.
     * @param i The index of the asset in the assets list.
     * @param drift The proposed target drift.
     * @param side The transaction type (BUY or SELL).
     * @param finalWeights The final actual weights array of the portfolio (by reference).
     * @param current The adjusted current weights array.
     * @param availableCashRef Pointer to the available cash in the ledger (by reference).
     */
    private void processOrder(RebalancingContext context, List<PerformanceIndex> assets,
                    RebalancingConfig config, double investableValue,
                    int i, double drift, Order.OrderSide side,
                    double[] finalWeights, double[] current, double[] availableCashRef)
    {
        double tradeValue = Math.abs(drift) * investableValue;

        if (assets == null || i >= assets.size() || assets.get(i) == null)
            return;

        PerformanceIndex asset = assets.get(i);

        // Obtain the last known price
        double lastPrice = 0.0;
        if (context.getAssetPrices() != null && i >= 0 && i < context.getAssetPrices().length)
        {
            lastPrice = context.getAssetPrices()[i];
        }
        else
        {
            lastPrice = getLastPrice(context, asset, i);
        }
        if (lastPrice <= 0.0)
        {
            context.getLogger().log("ViabilityLayer",
                            String.format("Asset %d: no valid price available, skipping.", i));
            finalWeights[i] = current[i];
            return;
        }

        // Apply strict cash budgeting for BUY orders (always enabled)
        if (side == Order.OrderSide.BUY)
        {
            double commissionFixed = config.getCommissionFixed();
            double commissionVar = config.getCommissionVariable();
            double available = availableCashRef[0];

            if (available - commissionFixed <= 0.0)
            {
                context.getLogger().log("ViabilityLayer",
                                String.format("Asset %d: BUY skipped due to strict cash budget (available cash %.2f <= fixed fee %.2f).",
                                                i, available, commissionFixed));
                finalWeights[i] = current[i];
                return;
            }

            double maxAllowedTradeValue = (available - commissionFixed) / (1.0 + commissionVar);
            if (tradeValue > maxAllowedTradeValue)
            {
                double oldTradeValue = tradeValue;
                tradeValue = maxAllowedTradeValue;
                context.getLogger().log("ViabilityLayer",
                                String.format("Asset %d: BUY trade value scaled down from %.2f to %.2f due to strict cash budget.",
                                                i, oldTradeValue, tradeValue));
            }
        }

        double quantity = tradeValue / lastPrice;

        // Lot sizing: round UP for sells if underfunded (to meet the cash buffer), round DOWN otherwise
        if (!config.isAllowFractions())
        {
            if (side == Order.OrderSide.SELL)
            {
                if (availableCashRef[0] < 0.0)
                {
                    quantity = Math.ceil(quantity);
                }
                else
                {
                    quantity = Math.floor(quantity);
                }
            }
            else
            {
                quantity = Math.floor(quantity);
            }
        }

        if (quantity <= 0)
        {
            context.getLogger().log("ViabilityLayer",
                            String.format("Asset %d: skipped because proposed quantity (%.4f) is zero or negative (tradeValue=%.2f, lastPrice=%.4f, allowFractions=%b).",
                                            i, quantity, tradeValue, lastPrice, config.isAllowFractions()));
            finalWeights[i] = current[i];
            return;
        }

        // Recalculate actual trade value after rounding
        double actualTradeValue = quantity * lastPrice;
        double commission = config.getCommissionFixed()
                        + (actualTradeValue * config.getCommissionVariable());

        // Commission filter: block if commission > 2% of actual trade value
        // Exception: DO NOT block if we are forced to sell to meet cash buffer
        boolean isForcedSell = (side == Order.OrderSide.SELL && availableCashRef[0] < 0.0);
        if (!isForcedSell && actualTradeValue > 0 && (commission / actualTradeValue) > COMMISSION_THRESHOLD)
        {
            finalWeights[i] = current[i];
            context.getLogger().log("ViabilityLayer",
                            String.format("Asset %d: %s blocked — commission (%.2f) > 2%% of trade (%.2f).",
                                            i, side, commission, actualTradeValue));
            return;
        }

        // Update available cash reference
        if (side == Order.OrderSide.SELL)
        {
            availableCashRef[0] += (actualTradeValue - commission);
        }
        else
        {
            availableCashRef[0] -= (actualTradeValue + commission);
        }

        // Update final weight based on actual trade value
        if (side == Order.OrderSide.BUY)
        {
            finalWeights[i] = current[i] + (actualTradeValue / investableValue);
        }
        else
        {
            finalWeights[i] = current[i] - (actualTradeValue / investableValue);
        }

        context.addOrder(new Order(asset, side, quantity, lastPrice));
        context.getLogger().log("ViabilityLayer",
                        String.format("Asset %d: %s order — %.2f shares at %.2f (value=%.2f).",
                                        i, side, quantity, lastPrice, actualTradeValue));
    }

    /**
     * Iteratively examines the remaining residual cash and redistributes it
     * proportionally among the assets already selected for a BUY order,
     * minimizing cash drag.
     */
    private void distributeResidualCash(RebalancingContext context, List<PerformanceIndex> assets,
                    RebalancingConfig config, double investableValue, double[] weights, double[] currentAdjusted,
                    double[] finalWeights, double[] availableCashRef)
    {
        double tolerableThreshold = Math.max(100.0, investableValue * 0.0001);
        int n = finalWeights.length;

        while (availableCashRef[0] > tolerableThreshold)
        {
            double totalCandidateWeights = 0.0;
            java.util.List<Integer> candidateIndices = new java.util.ArrayList<>();

            // Filter candidates: only assets already being BOUGHT with a valid target weight
            for (int i = 0; i < n; i++)
            {
                if (isFrozenAsset(context, i))
                    continue;

                if (finalWeights[i] > currentAdjusted[i] && weights[i] > 0.0)
                {
                    candidateIndices.add(i);
                    totalCandidateWeights += weights[i];
                }
            }

            // Break if no suitable candidates can absorb the remaining cash
            if (candidateIndices.isEmpty() || totalCandidateWeights <= 0.0)
            {
                break;
            }

            boolean anyStockPurchased = false;

            // Allocate cash proportionally based on target weights
            for (int i : candidateIndices)
            {
                double proportion = weights[i] / totalCandidateWeights;
                double allocatedCash = availableCashRef[0] * proportion;

                double lastPrice = 0.0;
                if (context.getAssetPrices() != null && i >= 0 && i < context.getAssetPrices().length)
                {
                    lastPrice = context.getAssetPrices()[i];
                }
                else
                {
                    lastPrice = getLastPrice(context, assets.get(i), i);
                }

                if (lastPrice <= 0.0)
                    continue;

                double commissionFixed = config.getCommissionFixed();
                double commissionVar = config.getCommissionVariable();

                if (allocatedCash - commissionFixed <= 0.0)
                    continue;

                double maxTradeValue = (allocatedCash - commissionFixed) / (1.0 + commissionVar);
                double quantity = maxTradeValue / lastPrice;

                if (!config.isAllowFractions())
                {
                    quantity = Math.floor(quantity);
                }

                if (quantity <= 0)
                    continue;

                double actualTradeValue = quantity * lastPrice;
                double commission = commissionFixed + (actualTradeValue * commissionVar);

                if (actualTradeValue > 0 && (commission / actualTradeValue) > COMMISSION_THRESHOLD)
                {
                    continue;
                }

                // Apply the supplemental purchase
                availableCashRef[0] -= (actualTradeValue + commission);
                finalWeights[i] += (actualTradeValue / investableValue);

                context.mergeOrder(new Order(assets.get(i), Order.OrderSide.BUY, quantity, lastPrice));
                anyStockPurchased = true;

                context.getLogger().log("ViabilityLayer", String.format(
                                "[Residual Loop] Asset %d: Supplemental BUY order — %.2f shares at %.2f (value=%.2f) deployed from residual cash.",
                                i, quantity, lastPrice, actualTradeValue));
            }

            // Safeguard against infinite loop if remaining cash cannot afford a
            // single share of any candidate
            if (!anyStockPurchased)
            {
                break;
            }
        }
    }

    /**
     * Extracts the last available price from a PerformanceIndex. Uses the
     * accumulated percentage of returns as a safe proxy for price evolution.
     *
     * @param asset
     *            The asset to extract the price from.
     * @return The last available price; 0.0 if none is valid.
     */
    private double getLastPrice(RebalancingContext context, PerformanceIndex asset, int assetIndex)
    {
        if (asset == null)
            return 0.0;
        double[] accumulated = asset.getAccumulatedPercentage();
        if (accumulated != null && accumulated.length > 0)
        {
            double val = accumulated[accumulated.length - 1];
            if (val > 0)
            {
                String name = context.getAssetIdentifier(assetIndex);
                if (name == null)
                    name = "Index " + assetIndex;
                context.getLogger().log("ViabilityLayer",
                                String.format("WARNING: Using accumulated returns proxy as price for asset %s (Value: %.2f). Prices might be incorrect.", name, val));
                return val;
            }
        }
        return 0.0;
    }

    /**
     * Determines whether a specific asset in the portfolio is frozen
     * (blacklisted) based on its identifier and the rebalancing configuration.
     *
     * @param context
     *            The shared rebalancing context.
     * @param assetIndex
     *            The index of the asset to check.
     * @return true if the asset is frozen; false otherwise.
     */
    private boolean isFrozenAsset(RebalancingContext context, int assetIndex)
    {
        if (context == null || context.getConfig() == null)
            return false;

        String identifier = context.getAssetIdentifier(assetIndex);
        return identifier != null && context.getConfig().isAssetFrozen(identifier);
    }

    @Override
    protected double[][] weightCandidates(RebalancingContext context)
    {
        return new double[][] {
            context.getRedundancyFilteredWeights(),
            context.getConstrainedWeights(),
            context.getTargetWeights(),
            context.getCurrentWeights()
        };
    }

    @Override
    protected String[] weightCandidateLabels()
    {
        return new String[] {
            "redundancyFilteredWeights",
            "constrainedWeights",
            "targetWeights",
            "currentWeights"
        };
    }
}
