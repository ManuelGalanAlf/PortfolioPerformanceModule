package name.abuchen.portfolio.math;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import name.abuchen.portfolio.snapshot.PerformanceIndex;

public class CovarianceTest
{
    private static final double TOLERANCE = 0.1e-10;

    private PerformanceIndex index(double... deltas)
    {
        PerformanceIndex index = mock(PerformanceIndex.class);
        when(index.getDeltaPercentage()).thenReturn(deltas);
        return index;
    }

    private double[][] sampleCovariance(double[][] data)
    {
        int observations = data.length;
        int dimensions = data[0].length;

        double[] means = new double[dimensions];
        for (int column = 0; column < dimensions; column++)
        {
            double sum = 0.0;
            for (int row = 0; row < observations; row++)
                sum += data[row][column];
            means[column] = sum / observations;
        }

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

    @Test
    public void testEmptyAssetsCreateEmptyMatrices()
    {
        Covariance covariance = new Covariance(Collections.emptyList());

        boolean threw = false;
        try
        {
            double[][] cov = covariance.getCovarianceMatrix();
            double[][] corr = covariance.getCorrelationMatrix();
            assertThat(cov.length, is(0));
            assertThat(corr.length, is(0));
        }
        catch (RuntimeException ex)
        {
            threw = true;
            assertThat(ex.getClass().getSimpleName(), is("NotStrictlyPositiveException"));
        }

        assertThat(threw || Double.isNaN(covariance.getPortfolioVariance(new double[0])), is(true));
    }

    @Test
    public void testCovarianceMatrixIsSymmetric()
    {
        PerformanceIndex first = index(0.01, -0.02, 0.05);
        PerformanceIndex second = index(0.03, 0.04, -0.01);
        Covariance covariance = new Covariance(Arrays.asList(first, second));

        double[][] matrix = covariance.getCovarianceMatrix();
        

        assertThat(matrix[0][1], closeTo(matrix[1][0], TOLERANCE));
    }

    @Test
    public void testCovarianceIsAnnualizedWithUsTradingDaysFactor()
    {
        PerformanceIndex first = index(0.01, 0.02, 0.03, 0.04);
        PerformanceIndex second = index(0.03, 0.02, 0.05, 0.04);

        Covariance covariance = new Covariance(Arrays.asList(first, second));
    double[][] matrix = covariance.getCovarianceMatrix();

        double[][] aligned = new double[][] {
                        { 0.01, 0.03 },
                        { 0.02, 0.02 },
                        { 0.03, 0.05 },
                        { 0.04, 0.04 } };

        double[][] daily = sampleCovariance(aligned);
        double scale = FinancialConstants.US_TRADING_DAYS_PER_YEAR;

        assertThat(matrix[0][0], closeTo(daily[0][0] * scale, TOLERANCE));
        assertThat(matrix[0][1], closeTo(daily[0][1] * scale, TOLERANCE));
        assertThat(matrix[1][1], closeTo(daily[1][1] * scale, TOLERANCE));
    }

    @Test
    public void testCorrelationValuesAreBoundedBetweenMinusOneAndOne()
    {
        PerformanceIndex first = index(0.01, -0.02, 0.03, -0.01, 0.04);
        PerformanceIndex second = index(-0.03, 0.02, -0.01, 0.03, -0.04);

        Covariance covariance = new Covariance(Arrays.asList(first, second));
        double[][] correlation = covariance.getCorrelationMatrix();

        assertThat(correlation[0][1] <= 1.0, is(true));
        assertThat(correlation[0][1] >= -1.0, is(true));
    }

    @Test
    public void testDifferentSeriesLengthsAreAlignedUsingLastCommonValues()
    {
        PerformanceIndex first = index(-0.10, 0.01, 0.02, 0.03, 0.04);
        PerformanceIndex second = index(0.02, 0.03, 0.04);

        Covariance covariance = new Covariance(Arrays.asList(first, second));
    double[][] matrix = covariance.getCovarianceMatrix();

        double[][] expectedAligned = new double[][] {
                        { 0.02, 0.02 },
                        { 0.03, 0.03 },
                        { 0.04, 0.04 } };
        double[][] expected = sampleCovariance(expectedAligned);
        double scale = FinancialConstants.US_TRADING_DAYS_PER_YEAR;

        assertThat(matrix[0][0], closeTo(expected[0][0] * scale, TOLERANCE));
        assertThat(matrix[0][1], closeTo(expected[0][1] * scale, TOLERANCE));
        assertThat(matrix[1][1], closeTo(expected[1][1] * scale, TOLERANCE));
    }

    @Test
    public void testInsufficientDataCreatesZeroMatricesWithAssetDimensions()
    {
        List<PerformanceIndex> assets = Arrays.asList(index(0.01), index(0.02));

        Covariance covariance = new Covariance(assets);
        double[][] matrix = covariance.getCovarianceMatrix();

        assertThat(matrix.length, is(2));
        assertThat(matrix[0].length, is(2));
        assertThat(matrix[0][0], is(0.0));
        assertThat(matrix[0][1], is(0.0));
        assertThat(matrix[1][1], is(0.0));
    }

    @Test
    public void testPortfolioVarianceMatchesQuadraticForm()
    {
        List<PerformanceIndex> assets = Arrays.asList(index(0.01, 0.02, 0.03, 0.04), index(0.03, 0.02, 0.05, 0.04));

        Covariance covariance = new Covariance(assets);
        double[] weights = new double[] { 0.6, 0.4 };

    double[][] sigma = covariance.getCovarianceMatrix();
    double expected = (weights[0] * weights[0] * sigma[0][0])
            + (2 * weights[0] * weights[1] * sigma[0][1])
            + (weights[1] * weights[1] * sigma[1][1]);

        assertThat(covariance.getPortfolioVariance(weights), closeTo(expected, TOLERANCE));
    }

    @Test
    public void testPortfolioVarianceReturnsNaNForInvalidWeightDimensions()
    {
        List<PerformanceIndex> assets = Arrays.asList(index(0.01, 0.02, 0.03), index(0.03, 0.02, 0.01));

        Covariance covariance = new Covariance(assets);

        assertThat(Double.isNaN(covariance.getPortfolioVariance(new double[] { 1.0 })), is(true));
    }

    @Test
    public void testPortfolioVarianceReturnsNaNForNullWeights()
    {
        List<PerformanceIndex> assets = Arrays.asList(index(0.01, 0.02, 0.03), index(0.03, 0.02, 0.01));

        Covariance covariance = new Covariance(assets);

        assertThat(Double.isNaN(covariance.getPortfolioVariance(null)), is(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCovarianceCalculationFailsFastForNullPerformanceIndex()
    {
        Covariance covariance = new Covariance(Arrays.asList(index(0.01, 0.02, 0.03), null));

        covariance.getCovarianceMatrix();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCovarianceCalculationFailsFastForMissingReturnSeries()
    {
        PerformanceIndex first = mock(PerformanceIndex.class);
        when(first.getDeltaPercentage()).thenReturn(new double[] { 0.01, 0.02, 0.03 });

        PerformanceIndex second = mock(PerformanceIndex.class);
        when(second.getDeltaPercentage()).thenReturn(null);

        Covariance covariance = new Covariance(Arrays.asList(first, second));

        covariance.getCovarianceMatrix();
    }
}
