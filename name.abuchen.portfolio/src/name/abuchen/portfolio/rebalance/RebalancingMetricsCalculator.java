package name.abuchen.portfolio.rebalance;

import java.util.ArrayList;
import java.util.List;

import name.abuchen.portfolio.math.AdvancedRiskMetrics;
import name.abuchen.portfolio.math.Covariance;
import name.abuchen.portfolio.math.PortfolioAnalytics;
import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * Calculator class that computes Sharpe Ratio, Volatility, and Value at Risk (VaR)
 * metrics for a portfolio before and after rebalancing.
 */
public class RebalancingMetricsCalculator
{
    public static RebalancingMetrics calculate(RebalancingContext context, PerformanceIndex portfolioIndex,
                    double riskFreeRate)
    {
        List<PerformanceIndex> assets = context.getAssets() != null ? context.getAssets() : new ArrayList<>();
        double[] currentWeights = context.getCurrentWeights() != null ? context.getCurrentWeights()
                        : new double[assets.size()];
        double[] finalWeights = context.getFinalWeights() != null ? context.getFinalWeights() : currentWeights;

        // Historic: AdvancedRiskMetrics
        double sharpeH = Double.NaN, volH = Double.NaN, varH = Double.NaN;
        if (portfolioIndex != null)
        {
            sharpeH = AdvancedRiskMetrics.annualizedSharpeRatio(portfolioIndex, riskFreeRate);
            double[] delta = portfolioIndex.getDeltaPercentage();
            if (delta != null && delta.length >= 2)
                volH = AdvancedRiskMetrics.annualizedStandardDeviation(delta);
            varH = AdvancedRiskMetrics.annualizedValueAtRisk(portfolioIndex, 0.95);
        }

        // Unchanged and changed metrics: PortfolioAnalytics
        double sharpeU = Double.NaN, volU = Double.NaN, varU = Double.NaN;
        double sharpeR = Double.NaN, volR = Double.NaN, varR = Double.NaN;
        if (!assets.isEmpty())
        {
            Covariance covariance = new Covariance(assets);
            covariance.calculate();
            PortfolioAnalytics analytics = new PortfolioAnalytics(covariance, assets);

            sharpeU = analytics.getPortfolioSharpeRatioAnnualized(currentWeights, riskFreeRate);
            volU = analytics.getPortfolioStandardDeviationAnnualized(currentWeights);
            varU = analytics.getParametricVaRAnnualized(currentWeights, 1.645);

            sharpeR = analytics.getPortfolioSharpeRatioAnnualized(finalWeights, riskFreeRate);
            volR = analytics.getPortfolioStandardDeviationAnnualized(finalWeights);
            varR = analytics.getParametricVaRAnnualized(finalWeights, 1.645);
        }

        return new RebalancingMetrics(sharpeH, sharpeU, sharpeR, volH, volU, volR, varH, varU, varR);
    }
}
