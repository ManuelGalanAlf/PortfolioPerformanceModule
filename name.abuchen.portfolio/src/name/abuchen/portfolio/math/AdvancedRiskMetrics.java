package name.abuchen.portfolio.math;

import java.util.Arrays;

import name.abuchen.portfolio.math.Risk.Drawdown;
import name.abuchen.portfolio.math.Risk.Volatility;
import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * Utility class that provides advanced risk-adjusted performance metrics for a
 * portfolio or security.
 * <p>
 * All methods are stateless and operate on data provided by
 * {@link PerformanceIndex}. This class cannot be instantiated.
 * <p>
 * Metrics implemented:
 * <ul>
 * <li>Expected Return E(R): arithmetic mean of periodic returns</li>
 * <li>Downside Risk (σd): standard deviation of negative returns only</li>
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
     * Calculates the Downside Risk (σd), i.e. the standard deviation of
     * returns that fall below the given target return (typically 0).
     * <p>
     * Formula: σd = √( Σ min(Ri - target, 0)² / N )
     *
     * @param returns
     *            array of periodic returns (e.g. daily delta values)
     * @param target
     *            minimum acceptable return (MAR). Use 0 for an absolute
     *            threshold or the risk-free rate for Sortino calculations.
     * @return downside risk as a positive decimal (e.g. 0.05 = 5%), or 0 if
     *         there are no negative returns
     * @throws IllegalArgumentException
     *             if returns is null or empty
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
     *            the {@link PerformanceIndex} of the portfolio or security
     * @param riskFreeRate
     *            annualized risk-free rate as a decimal (e.g. 0.02 = 2%)
     * @return Sharpe Ratio, or {@link Double#NaN} if volatility is zero or
     *         data is insufficient
     */
    public static double sharpeRatio(PerformanceIndex index, double riskFreeRate)
    {
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length < 2)
            return Double.NaN;

        double sigma = index.getVolatility().getStandardDeviation();

        if (sigma == 0.0)
            return Double.NaN;

        // Use IRR (already annualized)
        double r = index.getPerformanceIRR();

        if (Double.isNaN(r))
            return Double.NaN;

        // Annualize sigma (volatility) to match the annualized IRR (r)
        // Portfolio Performance uses 256 trading days as a standard.
        double annualizedSigma = sigma * Math.sqrt(256);

        return (r - riskFreeRate) / annualizedSigma;
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
     *            the {@link PerformanceIndex} of the portfolio or security
     * @param riskFreeRate
     *            annualized risk-free rate as a decimal (e.g. 0.02 = 2%)
     * @return Sortino Ratio, or {@link Double#NaN} if downside risk is zero or
     *         data is insufficient
     */
    public static double sortinoRatio(PerformanceIndex index, double riskFreeRate)
    {
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length < 2)
            return Double.NaN;

        double sigmaDown = downsideRisk(delta, 0.0);

        if (sigmaDown == 0.0)
            return Double.NaN;

        // Use IRR (already annualized)
        double r = index.getPerformanceIRR();

        if (Double.isNaN(r))
            return Double.NaN;

        // Annualize sigmaDown (downside risk) to match the annualized IRR (r)
        // Portfolio Performance uses 256 trading days as a standard.
        double annualizedSigmaDown = sigmaDown * Math.sqrt(256);

        return (r - riskFreeRate) / annualizedSigmaDown;
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
     *            the {@link PerformanceIndex} of the portfolio or security
     * @return Calmar Ratio, or {@link Double#NaN} if max drawdown is zero or
     *         data is insufficient
     */
    public static double calmarRatio(PerformanceIndex index)
    {
        double[] accumulated = index.getAccumulatedPercentage();

        if (accumulated == null || accumulated.length < 2)
            return Double.NaN;

        var drawdown = new Drawdown(accumulated, index.getDates(), 0);
        double maxDD = drawdown.getMaxDrawdown();

        if (maxDD == 0.0)
            return Double.NaN;

        // Use IRR (already annualized) to match existing WidgetFactory behaviour
        double r = index.getPerformanceIRR();

        if (Double.isNaN(r))
            return Double.NaN;

        return r / maxDD;
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
     *            the {@link PerformanceIndex} of the portfolio or security
     * @param confidence
     *            confidence level as a decimal between 0 and 1 (e.g. 0.95 for
     *            95%)
     * @return VaR as a positive decimal representing the loss threshold, or
     *         {@link Double#NaN} if data is insufficient
     * @throws IllegalArgumentException
     *             if confidence is not in (0, 1)
     */
    public static double valueAtRisk(PerformanceIndex index, double confidence)
    {
        if (confidence <= 0.0 || confidence >= 1.0)
            throw new IllegalArgumentException("Confidence must be between 0 and 1 (exclusive)"); //$NON-NLS-1$

        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length < 2)
            return Double.NaN;

        double[] sorted = Arrays.copyOf(delta, delta.length);
        Arrays.sort(sorted);

        // The percentile index for the left tail
        int idx = (int) Math.floor((1.0 - confidence) * sorted.length);
        idx = Math.max(0, Math.min(idx, sorted.length - 1));

        // VaR is the loss (positive value), so we negate the negative return
        return -sorted[idx];
    }

    /**
     * Calculates the arithmetic mean of periodic returns (Expected Return).
     * <p>
     * Formula: E(R) = Σ(Ri) / N
     * <p>
     * This value is used by the Markowitz Optimizer (Phase 3) as the expected
     * return input for each asset. It represents the historical average daily
     * return of the portfolio or security.
     *
     * @param index
     *            the {@link PerformanceIndex} of the portfolio or security
     * @return arithmetic mean of delta[] returns, or 0 if data is insufficient
     */
    public static double expectedReturn(PerformanceIndex index)
    {
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length == 0)
            return 0.0;

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
     *            the {@link PerformanceIndex} of the portfolio or security
     * @return skewness of returns, or {@link Double#NaN} if data is
     *         insufficient
     */
    public static double skewness(PerformanceIndex index)
    {
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length < 3)
            return Double.NaN;

        double mean = expectedReturn(index);
        double sigma = new Volatility(delta, ii -> true).getStandardDeviation();

        if (sigma == 0.0)
            return Double.NaN;

        double sumCubed = 0.0;
        for (double r : delta)
        {
            double deviation = r - mean;
            sumCubed += deviation * deviation * deviation;
        }

        return (sumCubed / delta.length) / (sigma * sigma * sigma);
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
     *            the {@link PerformanceIndex} of the portfolio or security
     * @return excess kurtosis of returns, or {@link Double#NaN} if data is
     *         insufficient
     */
    public static double excessKurtosis(PerformanceIndex index)
    {
        double[] delta = index.getDeltaPercentage();

        if (delta == null || delta.length < 4)
            return Double.NaN;

        double mean = expectedReturn(index);
        double sigma = new Volatility(delta, ii -> true).getStandardDeviation();

        if (sigma == 0.0)
            return Double.NaN;

        double sumFourth = 0.0;
        for (double r : delta)
        {
            double deviation = r - mean;
            sumFourth += deviation * deviation * deviation * deviation;
        }

        double sigma4 = sigma * sigma * sigma * sigma;
        return (sumFourth / delta.length) / sigma4 - 3.0;
    }

}
