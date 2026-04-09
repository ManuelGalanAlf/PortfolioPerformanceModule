package name.abuchen.portfolio.math;

import java.util.List;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

/**
 * The Covariance class is the engine for managing relationships between assets
 * (Covariance and Correlation).
 * <p>
 * 
 */
public class Covariance
{
    private final List<PerformanceIndex> assets;
    private double[][] covarianceMatrix;
    private double[][] correlationMatrix;

    public Covariance(List<PerformanceIndex> assets)
    {
        this.assets = assets;
    }

    /**
     * Calculates the annualized matrices.
     */
    public void calculate()
    {
        int numAssets = assets.size();
        if (numAssets == 0)
        {
            covarianceMatrix = new double[0][0];
            correlationMatrix = new double[0][0];
            return;
        }

        double[][] deltas = new double[numAssets][];
        int minLength = Integer.MAX_VALUE;

        // Find the minimum length of delta arrays to align them.
        for (int i = 0; i < numAssets; i++)
        {
            PerformanceIndex index = assets.get(i);
            if (index == null)
                throw new IllegalArgumentException("Assets list contains a null performance index");

            deltas[i] = index.getDeltaPercentage();

            if (deltas[i] == null)
                throw new IllegalArgumentException("Performance index contains no return series");

            if (deltas[i].length < minLength)
            {
                minLength = deltas[i].length;
            }
        }

        if (minLength == Integer.MAX_VALUE || minLength < 2)
        {
            // Empty matrix if data is missing
            covarianceMatrix = new double[numAssets][numAssets];
            correlationMatrix = new double[numAssets][numAssets];
            return;
        }

        // Build the data matrix for Apache Commons (rows: days, columns: assets)
        // Align by taking the last 'minLength' data points to synchronize them.
        double[][] data = new double[minLength][numAssets];
        for (int i = 0; i < numAssets; i++)
        {
            // Align the data by taking the last 'minLength' entries
            int offset = deltas[i].length - minLength;
            for (int t = 0; t < minLength; t++)
            {
                data[t][i] = deltas[i][offset + t];
            }
        }

        // Obtain the daily matrix and annualize it by multiplying by the US market trading days standard
        double[][] dailyCovariance = calculateCovarianceMatrix(data);
        covarianceMatrix = annualize(dailyCovariance);

        // Correlation is not annualized because it is bounded between -1 and 1
        correlationMatrix = calculateCorrelationMatrix(dailyCovariance);
    }

    /**
     * Annualizes the covariance matrix by multiplying each entry by the number of trading days per year.
     *
     * @param matrix
     *                   daily covariance matrix
     * @return annualized covariance matrix
     */
    private double[][] annualize(double[][] matrix)
    {
        double factor = FinancialConstants.US_TRADING_DAYS_PER_YEAR;
        int rows = matrix.length;
        int columns = rows == 0 ? 0 : matrix[0].length;

        double[][] annualized = new double[rows][columns];
        for (int row = 0; row < rows; row++)
        {
            for (int column = 0; column < columns; column++)
            {
                annualized[row][column] = matrix[row][column] * factor;
            }
        }

        return annualized;
    }

    /**
     * Calculates the sample covariance matrix from aligned return series.
     * <p>
     * Input matrix shape is observations x assets (rows are time points,
     * columns are assets). The method applies the sample estimator with
     * divisor (n - 1).
    * Formula: cov(i,j) = sum((x_ti - mean_i) * (x_tj - mean_j)) / (n - 1).
     *
     * @param data
     *                 aligned daily returns (rows: observations, columns:
     *                 assets)
     * @return daily covariance matrix (not annualized)
     */
    private double[][] calculateCovarianceMatrix(double[][] data)
    {
        int observations = data.length;
        int dimensions = data[0].length;

        // Calculate means for each asset (column)
        double[] means = new double[dimensions];
        for (int column = 0; column < dimensions; column++)
        {
            double sum = 0.0;
            for (int row = 0; row < observations; row++)
            {
                sum += data[row][column];
            }
            means[column] = sum / observations;
        }

        // Apply the covariance formula for each pair of assets (columns)  
        double[][] matrix = new double[dimensions][dimensions];
        for (int row = 0; row < dimensions; row++)
        {
            for (int column = row; column < dimensions; column++)
            {
                double sum = 0.0;
                for (int observation = 0; observation < observations; observation++)
                {
                    sum += (data[observation][row] - means[row]) * (data[observation][column] - means[column]);
                }

                double covariance = sum / (observations - 1);
                matrix[row][column] = covariance;
                matrix[column][row] = covariance;
            }
        }

        return matrix;
    }

    /**
     * Calculates the correlation matrix from a covariance matrix.
     * <p>
     * Correlation is computed as cov(i,j) / (standardDeviation_i * standardDeviation_j). When an
     * asset has zero volatility, diagonal entries are set to 1 and off-diagonal
     * entries to 0 to keep the matrix well-defined.
     *
     * @param covariance
     *                       daily covariance matrix
     * @return correlation matrix with values bounded in [-1, 1]
     */
    private double[][] calculateCorrelationMatrix(double[][] covariance)
    {
        int dimensions = covariance.length;
        double[][] matrix = new double[dimensions][dimensions];

        // Calculate the correlation using the covariance and standard deviations
        for (int row = 0; row < dimensions; row++)
        {
            double stdRow = covariance[row][row] > 0.0 ? Math.sqrt(covariance[row][row]) : 0.0;

            for (int column = row; column < dimensions; column++)
            {
                double stdColumn = covariance[column][column] > 0.0
                                ? Math.sqrt(covariance[column][column])
                                : 0.0;

                double correlation;
                if (stdRow == 0.0 || stdColumn == 0.0)
                {
                    correlation = row == column ? 1.0 : 0.0;
                }
                else
                {
                    correlation = covariance[row][column] / (stdRow * stdColumn);
                }

                matrix[row][column] = correlation;
                matrix[column][row] = correlation;
            }
        }

        return matrix;
    }

    public double[][] getCovarianceMatrix()
    {
        if (covarianceMatrix == null)
            calculate();
        return covarianceMatrix;
    }

    public double[][] getCorrelationMatrix()
    {
        if (correlationMatrix == null)
            calculate();
        return correlationMatrix;
    }

    /**
     * Returns the Portfolio Variance (Vp).
     * <p>
     * Matricial formula: Vp = w^T * Σ * w
     * 
     * @param weights array of weights
     * @return the portfolio variance
     */
    public double getPortfolioVariance(double[] weights)
    {
        double[][] cov = getCovarianceMatrix();

        if (weights == null || cov.length == 0 || weights.length != cov.length)
            return Double.NaN;

        // Result = w^T * Σ * w
        double variance = 0.0;
        for (int row = 0; row < cov.length; row++)
        {
            for (int column = 0; column < cov.length; column++)
            {
                variance += weights[row] * cov[row][column] * weights[column];
            }
        }

        return variance;
    }

    /**
     * Returns the annualized covariance value between two assets.
     *
     * @param row
     *                first asset index
     * @param column
     *                second asset index
     * @return covariance entry, or {@link Double#NaN} if indices are invalid
     */
    public double getCovarianceEntry(int row, int column)
    {
        double[][] cov = getCovarianceMatrix();

        if (row < 0 || column < 0 || row >= cov.length || column >= cov.length)
            return Double.NaN;

        return cov[row][column];
    }

    /**
     * Returns the covariance matrix dimension (number of assets).
     *
     * @return covariance matrix column count
     */
    public int getDimension()
    {
        return getCovarianceMatrix().length;
    }

}
