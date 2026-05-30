package name.abuchen.portfolio.math;

import java.util.List;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * The PortfolioAnalytics class calculates theoretical metrics for simulated
 * portfolios.
 * <p>
 * Unlike AdvancedRiskMetrics (which evaluates empirical and historical real
 * data), this class uses Linear Algebra and the Covariance Matrix to project
 * the financial behavior of hypothetical asset combinations based on their
 * allocated weights.
 */
public class PortfolioAnalytics
{
    
    private final Covariance covariance;
    private final List<PerformanceIndex> assets;

    public PortfolioAnalytics(Covariance covariance, List<PerformanceIndex> assets)
    {
        this.covariance = covariance;
        this.assets = assets;
    }

    /**
     * Returns the Portfolio Standard Deviation (σp).
     * <p>
     * The Optimizer will call this method thousands of times during a Monte
     * Carlo simulation.
     * 
     * @param weights array of weights
     * @return the portfolio standard deviation
     */
    public double getPortfolioStandardDeviationAnnualized(double[] weights)
    {
        double variance = covariance.getPortfolioVariance(weights);
        return Double.isNaN(variance) || variance < 0.0 ? Double.NaN : Math.sqrt(variance);
    }

    /**
     * Calculates the expected return (weighted average) of a simulated
     * portfolio.
     * <p>
     * Returns the daily expected return.
     * 
     * @param weights
     *            array of portfolio weights (must sum up to 1.0)
     * @return theoretical annualized expected return, or {@link Double#NaN} if
     *         data is missing
     */
    public double getPortfolioExpectedReturnAnnualized(double[] weights)
    {
        if (weights == null || weights.length != assets.size())
            return Double.NaN;

        double expectedReturnAnnualized = 0.0;
        for (int i = 0; i < assets.size(); i++)
        {
            PerformanceIndex asset = assets.get(i);
            if (asset == null)
                return Double.NaN;

            double assetAnnualizedER = AdvancedRiskMetrics.annualizedExpectedReturn(asset);
            if (Double.isNaN(assetAnnualizedER))
                return Double.NaN;

            expectedReturnAnnualized += weights[i] * assetAnnualizedER;
        }
        return expectedReturnAnnualized;
    }

    /**
     * Calculates the theoretical Sharpe Ratio of a given portfolio according to
     * Markowitz Theory.
     * <p>
     * Sharpe = (E(Rp_annualized) - Rf) / σp_annualized
     * 
     * @param weights
     *                         weights of the simulated portfolio
     * @param riskFreeRate
     *                         annualized Risk-Free Rate (e.g., 0.02 for 2%)
     * @return theoretical Sharpe Ratio
     */
    public double getPortfolioSharpeRatioAnnualized(double[] weights, double riskFreeRate)
    {
        double portfolioVolatility = getPortfolioStandardDeviationAnnualized(weights);
        if (Double.isNaN(portfolioVolatility) || portfolioVolatility == 0.0)
            return Double.NaN;

        double annualizedExpectedReturn = getPortfolioExpectedReturnAnnualized(weights);
        return (annualizedExpectedReturn - riskFreeRate) / portfolioVolatility;
    }

    /**
     * Calculates the Parametric Value at Risk (VaR) assuming a normal
     * distribution.
     * <p>
     * This is a global standard for market risk management (Basel III).
     * 
     * @param weights
     *                    weights of the simulated portfolio
     * @param zScore
     *                    normal distribution factor associated with a
     *                    confidence level (e.g., 1.645 for 95%)
     * @return maximum probable annualized loss expressed as a positive decimal
     *         (e.g., 0.15 = 15%)
     */
    public double getParametricVaRAnnualized(double[] weights, double zScore)
    {
        double portfolioVolatility = getPortfolioStandardDeviationAnnualized(weights);
        if (Double.isNaN(portfolioVolatility))
            return Double.NaN;

        double annualizedExpectedReturn = getPortfolioExpectedReturnAnnualized(weights);
        return (zScore * portfolioVolatility) - annualizedExpectedReturn;
    }

    /**
     * Calculates the Diversification Ratio.
     * <p>
     * Evaluates how efficiently individual risks are canceling each other out
     * due to imperfect correlations. Higher values indicate a greater
     * diversification benefit.
     * 
     * @param weights
     *                    weights of the portfolio
     * @return diversification ratio
     */
    public double getDiversificationRatio(double[] weights)
    {
        if (weights == null || weights.length != assets.size())
            return Double.NaN;

        double portfolioVolatility = getPortfolioStandardDeviationAnnualized(weights);
        if (Double.isNaN(portfolioVolatility) || portfolioVolatility == 0.0)
            return Double.NaN;

        double weightedSumIndividualVolatility = 0.0;
        for (int i = 0; i < assets.size(); i++)
        {
            PerformanceIndex index = assets.get(i);
            if (index == null)
                return Double.NaN;

            double[] delta = index.getDeltaPercentage();
            if (delta == null)
                return Double.NaN;

            double individualVola = AdvancedRiskMetrics.annualizedStandardDeviation(delta);
            if (Double.isNaN(individualVola))
                return Double.NaN;

            weightedSumIndividualVolatility += weights[i] * individualVola;
        }

        return weightedSumIndividualVolatility / portfolioVolatility;
    }

    /**
     * Calculates the portfolio concentration index (HHI).
     * <p>
     * Formula: HHI = sum(w_i^2). Higher values imply higher concentration.
     *
     * @param weights
     *                    weights of the portfolio
     * @return concentration index, or {@link Double#NaN} if weights are invalid
     */
    public double getConcentrationIndex(double[] weights)
    {
        if (weights == null || weights.length != assets.size())
            return Double.NaN;

        double concentration = 0.0;
        for (double weight : weights)
        {
            if (Double.isNaN(weight) || Double.isInfinite(weight))
                return Double.NaN;

            concentration += weight * weight;
        }

        return concentration;
    }

    /**
     * Calculates each asset contribution to total portfolio risk.
     * <p>
     * Formula: RC_i = w_i * (Σw)_i / σp where σp is the portfolio standard
     * deviation.
     *
     * @param weights
     *                    weights of the portfolio
     * @return array with one contribution per asset. Returns NaN entries when
     *         inputs are invalid.
     */
    public double[] getRiskContributions(double[] weights)
    {
        int dimension = covariance.getDimension();

        if (weights == null || dimension == 0 || weights.length != dimension)
            return new double[0];

        // Obtain the portfolio volatility
        double portfolioVolatility = getPortfolioStandardDeviationAnnualized(weights);
        if (Double.isNaN(portfolioVolatility) || portfolioVolatility == 0.0)
            return new double[dimension];

        // Calculate the marginal risk contribution of each asset
        double[] contributions = new double[dimension];
        for (int i = 0; i < dimension; i++)
        {
            double marginalRisk = 0.0;
            for (int j = 0; j < dimension; j++)
            {
                marginalRisk += covariance.getCovarianceEntry(i, j) * weights[j];
            }

            contributions[i] = (weights[i] * marginalRisk) / portfolioVolatility;
        }

        return contributions;
    }

    /**
     * Calculates each asset contribution as a percentage of total portfolio risk.
     * <p>
     * Formula: RC%_i = RC_i / σp where RC_i is the absolute contribution and σp
     * is the portfolio standard deviation.
     *
     * @param weights
     *                    weights of the portfolio
     * @return array with one percentage contribution per asset (values typically
     *         in the range 0..1). Returns zeroes when portfolio volatility is
     *         invalid.
     */
    public double[] getRiskContributionsPercentage(double[] weights)
    {
        double[] absoluteContributions = getRiskContributions(weights);
        double portfolioVol = getPortfolioStandardDeviationAnnualized(weights);

        if (portfolioVol <= 0 || Double.isNaN(portfolioVol))
            return new double[absoluteContributions.length];

        double[] percentages = new double[absoluteContributions.length];
        for (int i = 0; i < absoluteContributions.length; i++)
        {
            // Convert absolute contribution to percentage of total portfolio risk
            percentages[i] = absoluteContributions[i] / portfolioVol;
        }
        return percentages;
    }
}
