package name.abuchen.portfolio.math;

import java.util.Arrays;

import name.abuchen.portfolio.math.Risk.Drawdown;
import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * Utility class that provides advanced risk-adjusted performance metrics for a
 * portfolio or security.
 * <p>
 * All methods are stateless and operate on data provided by
 * {@link PerformanceIndex}. Most metrics consume daily return series and
 * annualize results when needed so they can be compared against annualized
 * values such as IRR. This class cannot be instantiated.
 * <p>
 * Metrics implemented:
 * <ul>
 * <li>Expected Return E(R): arithmetic mean of periodic returns</li>
 * <li>Downside Risk (σd): standard deviation of returns below a target</li>
 * <li>Sharpe Ratio: excess return per unit of total volatility</li>
 * <li>Sortino Ratio: excess return per unit of downside risk</li>
 * <li>Calmar Ratio: annualized return divided by maximum drawdown</li>
 * <li>Value at Risk (VaR): maximum expected loss at a given confidence level</li>
 * <li>Skewness: asymmetry of the return distribution</li>
 * <li>Excess Kurtosis: fat-tail indicator of the return distribution</li>
 * </ul>
 *
 * @see Risk
 * @see PerformanceIndex
 */
public final class AdvancedRiskMetrics
{
    private AdvancedRiskMetrics()
    {
    }

    /**
     * Calculates the sample standard deviation of the given return series.
     * <p>
     * Formula: σ = √( Σ(Ri - μ)² / (N - 1) )
     * <p>
     * This is the volatility measure used by Sharpe, skewness, kurtosis and
     * tracking error in this class.
     */
    static double standardDeviation(double[] values)
    {
        if (values == null || values.length < 2)
            return Double.NaN;

        double sum = 0.0;
        int count = 0;
        // Calculate mean of the values
        for (double value : values)
        {
            if (!Double.isFinite(value))
                continue;

            sum += value;
            count++;
        }

        if (count < 2)
            return Double.NaN;

        double mean = sum / count;
        double squaredDeviations = 0.0;

        // Calculate the sum of squared deviations from the mean
        for (double value : values)
        {
            if (!Double.isFinite(value))
                continue;

            double deviation = value - mean;
            squaredDeviations += deviation * deviation;
        }

        return Math.sqrt(squaredDeviations / (count - 1));
    }

    /**
     * Calculates the Downside Risk (σd), i.e. the standard deviation of returns
     * that fall below the given target return (typically 0).
     * <p>
     * Formula: σd = √( Σ min(Ri - target, 0)² / N )
     *
     * @param returns
     *                    array of periodic returns (e.g. daily delta values)
     * @param target
     *                    minimum acceptable return (MAR). Use 0 for an absolute
     *                    threshold or the risk-free rate for Sortino
     *                    calculations.
     * @return downside risk as a positive decimal (e.g. 0.05 = 5%), or 0 if
     *         there are no negative returns
     * @throws IllegalArgumentException
     *                                      if returns is null or empty
     */
    public static double downsideRisk(double[] returns, double target)
    {
        if (returns == null || returns.length == 0)
            throw new IllegalArgumentException("Returns array must not be null or empty"); //$NON-NLS-1$

        double sumSquared = 0.0;
        int count = 0;

        for (double r : returns)
        {
            double deviation = Math.min(r - target, 0.0);
            sumSquared += deviation * deviation;
            count++;
        }

        if (count == 0)
            return 0.0;

        return Math.sqrt(sumSquared / count);
    }

    /**
     * Calculates the Sharpe Ratio for the given performance index.
     * <p>
     * Formula: Sharpe = (Rp - Rf) / σp
     * <p>
     * Where Rp is the annualized portfolio return, Rf is the risk-free rate,
     * and σp is the total annualized volatility (standard deviation).
     *
     * @param index
     *                         the {@link PerformanceIndex} of the portfolio or
     *                         security
     * @param riskFreeRate
     *                         annualized risk-free rate as a decimal (e.g. 0.02
     *                         = 2%)
     * @return Sharpe Ratio, or {@link Double#NaN} if volatility is zero or data
     *         is insufficient
     */
    public static double sharpeRatio(PerformanceIndex index, double riskFreeRate)
    {
        if (index == null)
            return Double.NaN;

        // Array of daily percentage returns (delta values)
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length < 2)
            return Double.NaN;

        // Compute volatility directly from daily returns to avoid depending on
        // Risk.Volatility internals.
        double standardDeviation = standardDeviation(delta);

        if (Double.isNaN(standardDeviation) || standardDeviation == 0.0)
            return Double.NaN;

        // Use IRR (already annualized)
        double annualizedIRR = index.getPerformanceIRR();

        if (Double.isNaN(annualizedIRR))
            return Double.NaN;

        // Annualize standardDeviation (volatility) to match the annualized IRR
        // (rp)
        double annualizedStandardDeviation = standardDeviation * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR);

        return (annualizedIRR - riskFreeRate) / annualizedStandardDeviation;
    }

    /**
     * Converts an annualized rate into an equivalent daily rate using
     * compound growth.
     * <p>
     * Formula: r_daily = (1 + r_annual)^(1 / 252) - 1
     * <p>
     * This is used to compare the risk-free rate with daily return series on
     * the same scale.
     */
    private static double annualToDailyRate(double annualRate)
    {
        if (!Double.isFinite(annualRate) || annualRate <= -1.0)
            return Double.NaN;

        return Math.pow(1.0 + annualRate, 1.0 / FinancialConstants.US_TRADING_DAYS_PER_YEAR) - 1.0;
    }

    /**
     * Calculates the Sortino Ratio for the given performance index.
     * <p>
     * Formula: Sortino = (Rp - Rf) / σd
     * <p>
     * Unlike the Sharpe Ratio, only downside volatility is penalized, making
     * this metric more appropriate for asymmetric return distributions.
     *
     * @param index
     *                         the {@link PerformanceIndex} of the portfolio or
     *                         security
     * @param riskFreeRate
     *                         annualized risk-free rate as a decimal (e.g. 0.02
     *                         = 2%)
     * @return Sortino Ratio, or {@link Double#NaN} if downside risk is zero or
     *         data is insufficient
     */
    public static double sortinoRatio(PerformanceIndex index, double riskFreeRate)
    {
        if (index == null)
            return Double.NaN;

        // Array of daily percentage returns (delta values)
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length < 2)
            return Double.NaN;

        // Convert annual MAR/risk-free rate to daily to match daily return inputs.
        double dailyTarget = annualToDailyRate(riskFreeRate);
        if (Double.isNaN(dailyTarget))
            return Double.NaN;

        double downsideRisk = downsideRisk(delta, dailyTarget);

        if (Double.isNaN(downsideRisk) || downsideRisk == 0.0)
            return Double.NaN;

        // Use IRR (already annualized)
        double annualizedIRR = index.getPerformanceIRR();

        if (Double.isNaN(annualizedIRR))
            return Double.NaN;

        // Annualize downsideRisk to match the annualized IRR (rp)
        double annualizedDownsideRisk = downsideRisk * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR);

        return (annualizedIRR - riskFreeRate) / annualizedDownsideRisk;
    }

    /**
     * Calculates the Calmar Ratio for the given performance index.
     * <p>
     * Formula: Calmar = Rp_annualized / |MaxDrawdown|
     * <p>
     * A higher Calmar Ratio indicates better risk-adjusted return relative to
     * the maximum historical loss suffered.
     *
     * @param index
     *                  the {@link PerformanceIndex} of the portfolio or
     *                  security
     * @return Calmar Ratio, or {@link Double#NaN} if max drawdown is zero or
     *         data is insufficient
     */
    public static double calmarRatio(PerformanceIndex index)
    {
        if (index == null)
            return Double.NaN;

        // Array of accumulated percentage returns (index values)
        double[] accumulated = index.getAccumulatedPercentage();

        if (accumulated == null || accumulated.length < 2)
            return Double.NaN;

        // Calculate maximum drawdown using the Drawdown class
        var drawdown = new Drawdown(accumulated, index.getDates(), 0);
        double maxDrawdown = drawdown.getMaxDrawdown();

        if (maxDrawdown == 0.0)
            return Double.NaN;

        // Use IRR (already annualized) to match existing WidgetFactory
        // behaviour
        double annualizedIRR = index.getPerformanceIRR();

        if (Double.isNaN(annualizedIRR))
            return Double.NaN;

        return annualizedIRR / maxDrawdown;
    }

    /**
     * Calculates the historical Value at Risk (VaR) at the given confidence
     * level.
     * <p>
     * VaR answers the question: "What is the maximum loss I can expect with X%
     * probability over one period?" using the empirical distribution of past
     * returns.
     * <p>
     * Example: a VaR of 0.03 at confidence=0.95 means there is a 5% chance of
     * losing more than 3% in a single period.
     *
     * @param index
     *                       the {@link PerformanceIndex} of the portfolio or
     *                       security
     * @param confidence
     *                       confidence level as a decimal between 0 and 1 (e.g.
     *                       0.95 for 95%)
     * @return VaR as a positive decimal representing the loss threshold, or
     *         {@link Double#NaN} if data is insufficient
     * @throws IllegalArgumentException
     *                                      if confidence is not in (0, 1)
     */
    public static double valueAtRisk(PerformanceIndex index, double confidence)
    {
        if (confidence <= 0.0 || confidence >= 1.0)
            throw new IllegalArgumentException("Confidence must be between 0 and 1 (exclusive)"); //$NON-NLS-1$

        if (index == null)
            return Double.NaN;

        // Array of daily percentage returns (delta values)
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length < 2)
            return Double.NaN;

        // Sort the returns to find the appropriate percentile for VaR
        double[] sorted = Arrays.copyOf(delta, delta.length);
        Arrays.sort(sorted);

        // Calculate the index for the (1 - confidence) percentile in the sorted
        // array
        // For example, for confidence=0.95, we want the 5th percentile (0.05)
        int idx = (int) Math.floor(((1.0 - confidence) * sorted.length) + 1e-12);
        idx = Math.max(0, Math.min(idx, sorted.length - 1));

        // VaR is the loss (positive value), so we negate the negative return
        return -sorted[idx];
    }

    /**
     * Calculates the arithmetic mean of periodic returns (Expected Return).
     * <p>
     * Formula: E(R) = Σ(Ri) / N
     * <p>
     * This value is used by the Markowitz Optimizer as the expected return
     * input for each asset. It represents the historical average daily return
     * of the portfolio or security.
     *
     * @param index
     *                  the {@link PerformanceIndex} of the portfolio or
     *                  security
     * @return arithmetic mean of delta[] returns, or {@link Double#NaN} if data
     *         is insufficient
     */
    public static double expectedReturn(PerformanceIndex index)
    {
        if (index == null)
            return Double.NaN;

        // Array of daily percentage returns (delta values)
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length == 0)
            return Double.NaN;

        double sum = 0.0;

        for (double r : delta)
            sum += r;

        return sum / delta.length;
    }

    /**
     * Calculates the skewness (third standardized moment) of the return
     * distribution.
     * <p>
     * Formula: Skewness = [Σ(Ri - μ)³ / N] / σ³
     * <p>
     * Interpretation:
     * <ul>
     * <li>Skewness &lt; 0: left tail is longer — losses are more extreme than
     * gains. This is the typical case for equity portfolios and justifies using
     * Sortino over Sharpe.</li>
     * <li>Skewness = 0: symmetric distribution (e.g. normal distribution)</li>
     * <li>Skewness &gt; 0: right tail is longer — gains are more extreme than
     * losses.</li>
     * </ul>
     *
     * @param index
     *                  the {@link PerformanceIndex} of the portfolio or
     *                  security
     * @return skewness of returns, or {@link Double#NaN} if data is
     *         insufficient
     */
    public static double skewness(PerformanceIndex index)
    {
        if (index == null)
            return Double.NaN;

        // Array of daily percentage returns (delta values)
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length < 3)
            return Double.NaN;

        // Calculate mean from expectedReturn method
        double mean = expectedReturn(index);

        // Calculate standard deviation from the Volatility class
        double standardDeviation = standardDeviation(delta);

        if (Double.isNaN(standardDeviation) || standardDeviation == 0.0)
            return Double.NaN;

        double sumOfCubedDeviations = 0.0;
        for (double deltaValue : delta)
        {
            double deviation = deltaValue - mean;
            sumOfCubedDeviations += deviation * deviation * deviation;
        }

        return (sumOfCubedDeviations / delta.length) / (standardDeviation * standardDeviation * standardDeviation);
    }

    /**
     * Calculates the excess kurtosis (fourth standardized moment minus 3) of
     * the return distribution.
     * <p>
     * Formula: Excess Kurtosis = [Σ(Ri - μ)⁴ / N] / σ⁴ - 3
     * <p>
     * The subtraction of 3 normalizes the result so that a normal distribution
     * yields 0. This is also called Fisher's kurtosis.
     * <p>
     * Interpretation:
     * <ul>
     * <li>Excess Kurtosis &gt; 0 (leptokurtic): fat tails — extreme events
     * (crashes and rallies) occur more often than a normal distribution
     * predicts. This justifies using historical VaR over parametric VaR, which
     * assumes normality (Basel III requirement).</li>
     * <li>Excess Kurtosis = 0: normal distribution (mesokurtic)</li>
     * <li>Excess Kurtosis &lt; 0 (platykurtic): thin tails — fewer extreme
     * events than expected.</li>
     * </ul>
     *
     * @param index
     *                  the {@link PerformanceIndex} of the portfolio or
     *                  security
     * @return excess kurtosis of returns, or {@link Double#NaN} if data is
     *         insufficient
     */
    public static double excessKurtosis(PerformanceIndex index)
    {
        if (index == null)
            return Double.NaN;

        // Array of daily percentage returns (delta values)
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length < 4)
            return Double.NaN;

        // Calculate mean from expectedReturn method
        double mean = expectedReturn(index);

        // Calculate standard deviation from the Volatility class
        double standardDeviation = standardDeviation(delta);

        if (Double.isNaN(standardDeviation) || standardDeviation == 0.0)
            return Double.NaN;

        double sumFourthDeviations = 0.0;
        for (double deltaValue : delta)
        {
            double deviation = deltaValue - mean;
            sumFourthDeviations += deviation * deviation * deviation * deviation;
        }

        double standardDeviationToFourth = standardDeviation * standardDeviation * standardDeviation
                        * standardDeviation;

        return (sumFourthDeviations / delta.length) / standardDeviationToFourth - 3.0;
    }

    /**
     * Calculates the tracking error between a portfolio and its benchmark.
     * <p>
     * Tracking error is the standard deviation of the difference between
     * portfolio and benchmark returns.
     *
     * @param portfolio
     *                      the {@link PerformanceIndex} of the portfolio or
     *                      fund
     * @param benchmark
     *                      the {@link PerformanceIndex} of the benchmark index
     * @return daily tracking error as a decimal, or {@link Double#NaN} if data
     *         is mismatched or insufficient
     */
    public static double trackingError(PerformanceIndex portfolio, PerformanceIndex benchmark)
    {
        if (portfolio == null || benchmark == null)
            return Double.NaN;

        // Array of daily percentage returns of the portfolio
        double[] portfolioDelta = portfolio.getDeltaPercentage();
        // Array of daily percentage returns of the benchmark
        double[] benchmarkDelta = benchmark.getDeltaPercentage();

        if (portfolioDelta == null || benchmarkDelta == null || portfolioDelta.length != benchmarkDelta.length
                        || portfolioDelta.length < 2)
            return Double.NaN;

        double[] diff = new double[portfolioDelta.length];
        for (int i = 0; i < portfolioDelta.length; i++)
            diff[i] = portfolioDelta[i] - benchmarkDelta[i];

        return standardDeviation(diff);
    }

    /**
     * Calculates the annualized tracking error between a portfolio and its
     * benchmark.
     * <p>
    * Annualized using sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR) trading days.
     *
     * @param portfolio
     *                      the {@link PerformanceIndex} of the portfolio or
     *                      fund
     * @param benchmark
     *                      the {@link PerformanceIndex} of the benchmark index
     * @return annualized tracking error as a decimal
     */
    public static double annualizedTrackingError(PerformanceIndex portfolio, PerformanceIndex benchmark)
    {
        if (portfolio == null || benchmark == null)
            return Double.NaN;

        // Daily tracking error
        double trackingErrorDaily = trackingError(portfolio, benchmark);

        if (Double.isNaN(trackingErrorDaily))
            return Double.NaN;

        return trackingErrorDaily * Math.sqrt(FinancialConstants.US_TRADING_DAYS_PER_YEAR);
    }

    /**
     * Calculates the information ratio of a portfolio versus its benchmark.
     * <p>
     * Formula: IR = (Rp - Rb) / Annualized Tracking Error
     * <p>
     * Rp and Rb are the annualized returns (IRR) of both indices.
     *
     * @param portfolio
     *                      the {@link PerformanceIndex} of the portfolio or
     *                      fund
     * @param benchmark
     *                      the {@link PerformanceIndex} of the benchmark index
     * @return annualized information ratio, or {@link Double#NaN} if tracking
     *         error is zero or data is insufficient
     */
    public static double informationRatio(PerformanceIndex portfolio, PerformanceIndex benchmark)
    {
        if (portfolio == null || benchmark == null)
            return Double.NaN;

        // Annualized tracking error
        double annualizedTrackingError = annualizedTrackingError(portfolio, benchmark);

        if (annualizedTrackingError == 0.0 || Double.isNaN(annualizedTrackingError))
            return Double.NaN;

        // Annualized returns (IRR) of both indices
        double annualizedIRRPortfolio = portfolio.getPerformanceIRR();
        double annualizedIRRBenchmark = benchmark.getPerformanceIRR();

        if (Double.isNaN(annualizedIRRPortfolio) || Double.isNaN(annualizedIRRBenchmark))
            return Double.NaN;

        return (annualizedIRRPortfolio - annualizedIRRBenchmark) / annualizedTrackingError;
    }

}
